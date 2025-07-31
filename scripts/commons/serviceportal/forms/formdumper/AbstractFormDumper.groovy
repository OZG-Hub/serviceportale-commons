package commons.serviceportal.forms.formdumper

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.*
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGDIKMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGeoMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import de.seitenbau.serviceportal.scripting.api.v1.start.StartParameterV1
import de.seitenbau.serviceportal.scripting.api.v1.start.StartedByUserV1
import groovy.json.JsonSlurper
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate

/**
 * A FormDumper is a helper class designed to transfer the Serviceportal-proprietary form (= a FormContentV1 object)
 * into various other formats (XML, JSON, HTML-tables, …), sometimes with additional configuration options.
 *
 * This abstract class holds shared functionality and provides a stable interface.
 *
 */
abstract class AbstractFormDumper {
  final protected FormV1 form
  final protected ScriptingApiV1 api
  final protected boolean includeMetadata

  final protected static String ISO8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

  /**
   * Additional rules which decided if a field should be hidden. By default we show all fields.
   */
  private Closure<Boolean> additionalLogicToHideContent = { FormFieldV1 field -> return false }

  /**
   * Creates a new FormDumper.
   *
   * @param formContent The form to transform
   * @param api The API of the serviceportal. The only way to access this is via the variable "apiV1" that is available
   *   in a script task.
   * @param includeMetadata whether to include metadata about the application like postfachHandleId, form id, creation
   *   date and a base64-encoded PDF file of the form
   */
  AbstractFormDumper(FormContentV1 formContent, ScriptingApiV1 api, boolean includeMetadata = false) {
    // Note about why the api needs to be provided as a parameter:
    // The variable is made available in a script task as a variable set by the process engine, but it's not possible
    // to access it inside a class (like AbstractFormDumper), therefore it needs to be provided explicitly when
    // creating a FormDumper.
    this.api = api
    this.includeMetadata = includeMetadata

    form = api.getForm(formContent.getFormId())
    form.setContent(formContent)
  }

  /**
   * It might be necessary to instruct the FormDumper to avoid outputting some fields (that are not already
   * ignored by existing rules). Example: Hiding a application ID that is shown in every form (to the user), but should
   * not be visible in the XML file that is sent to a third party.
   *
   * This method allows configuring that additional logic. Existing logic to hide fields (e.g. fields that were not
   * visible to the user) is still respected.
   *
   * @param additionalLogicToHideContent A closure that is evaluated for every field in the form and should return
   *   `true` if the field should be hidden.
   *
   * Example:
   * <pre>
   * {@code
   * myExampleDumper.configureAdditionalHidingLogic({field ->
   *   if (field.id == "firstName") {
   *     return true // dont show the firstName-field
   *   } else {
   *     return false // show all other fields
   *   }
   * })
   *}
   * </pre>
   */
  void configureAdditionalHidingLogic(
          @ClosureParams(
                  value = SimpleType.class,
                  options = "de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1")
                  Closure<Boolean> additionalLogicToHideContent) {
    this.additionalLogicToHideContent = additionalLogicToHideContent
  }

  /**
   * Transforms the form content into another format, depending on the concrete Implementation (e.g. a
   * {@link commons.serviceportal.forms.formdumper.XmlDumper} would create a String of a XML.)
   *
   * This works in the following way:
   * - Offer hooks to setup the resulting String
   * - Offer hooks to add metadata
   * - Iterate through grouping elements (like group instances, fields, etc)
   * - Call a Hook for every grouping element and pass the current result to it
   * - - Implementations of the AbstractFormDumper then implement that hook with format-specific logic.
   *     A XmlDumper would create appropriate XML tags for instance.
   * - Store the changed result and continue with the next grouping element
   *
   * @return
   */
  String dump() {
    String result = dumpingStartHook()

    if (includeMetadata) {
      result = metadataHook(result)
    }

    form.groupInstances.each { groupInstance ->
      result = groupInstanceBeginHook(result, groupInstance)

      groupInstance.rows.each { row ->
        row.fields.each { field ->

          if (shouldRenderField(field, groupInstance)) {
            result = fieldHook(result, field, groupInstance)
          }

        }
      }

      result = groupInstanceEndHook(result, groupInstance)
    }

    result = dumpingDoneHook(result)

    return result
  }

  /**
   * Called at the beginning of the dumping process to setup the result object.
   * Default implementation returns an empty String.
   *
   * @return
   */
  protected String dumpingStartHook() {
    return ""
  }

  /**
   * Called at the beginning of the dumping process, when the dumper should provide some additional metadata.
   * (This hook is only called, if the FormDumper constructor's `includeMetadata` parameter was set to true.)
   * @return
   */
  protected abstract String metadataHook(String currentResult)

  /**
   * Called at the begin of a group instance
   * @param currentResult
   * @param groupInstance
   * @return
   */
  protected abstract String groupInstanceBeginHook(String currentResult, FieldGroupInstanceV1 groupInstance)

  /**
   * Called when a group is closing (before opening a new group, or finishing the entire dumping process)
   * @param currentResult
   * @param groupInstance
   * @return
   */
  protected abstract String groupInstanceEndHook(String currentResult, FieldGroupInstanceV1 groupInstance)

  /**
   * Called on every single individual field
   * @param currentResult
   * @param field
   * @param groupInstance
   * @return
   */
  protected abstract String fieldHook(String currentResult, FormFieldV1 field, FieldGroupInstanceV1 groupInstance)

  /**
   * Called at the end of the dumping process. Useful for removing trailing newlines or similar.
   * The default implementation doesn't edit the result.
   * @param currentResult
   * @return
   */
  protected String dumpingDoneHook(String currentResult) {
    // This default implementation just passes the current result through without any changes.
    return currentResult
  }

  /**
   * Determines if a field should be rendered or not.
   * Fields will not be rendered, if:
   * - They are invisible
   * - Users can't input anything (e.g. headings, download buttons)
   * - Additional logic was set via the configureAdditionalHidingLogic method.
   *
   * @param field the field to check
   *
   * @return true, if the field should be shown
   */
  protected boolean shouldRenderField(FormFieldV1 field, FieldGroupInstanceV1 instance) {
    // Hide fields that are not shown
    if (!field.isShown(instance, form)) {
      return false
    }

    // Hide fields without any input
    if (field.type == FieldTypeV1.TEXT ||
            field.type == FieldTypeV1.H2 ||
            field.type == FieldTypeV1.H1 ||
            field.type == FieldTypeV1.PLACEHOLDER ||
            field.type == FieldTypeV1.DOWNLOAD ||
            field.type == FieldTypeV1.VIDEO ||
            field.type == FieldTypeV1.IMAGE ||
            field.type == FieldTypeV1.HINTBOX) {
      return false
    }

    // User might have overridden the 'additionalLogicToHideContent' to hide some additional fields.
    // That's why we evaluate that closure now.
    if (additionalLogicToHideContent.call(field) == true) {
      return false
    }

    // **DO** show all other fields
    return true
  }

  /**
   * Get a String representation useful for displaying it to a human.
   * E.g. Checkboxes will contain the label the user selected, dates are formatted with a German
   * date format and no time component, etc.
   *
   * @param field The field to render
   * @return The String representation
   */
  @SuppressWarnings('GrDeprecatedAPIUsage')
  // We need to support deprecated form field types as they might still be in use by older forms
  protected String renderFieldForUserOutput(FormFieldV1 field) {
    def value = getValueFromField(field)

    if (value == null || value.toString().isAllWhitespace()) {
      return "[Keine Eingabe]"
    }

    //noinspection GroovyFallthrough - those fall-throughs are on purpose.
    switch (field.type) {
      case FieldTypeV1.STRING:
        // fall through
      case FieldTypeV1.STRING_AJAX_AUTOCOMPLETE:
        // fall through
      case FieldTypeV1.KFZ_KENNZEICHEN:
        // fall through
      case FieldTypeV1.TEXTAREA:
        return value
        break
      case FieldTypeV1.FILE:
        return "Datei: \"${(value as BinaryContentV1).uploadedFilename}\""
        break
      case FieldTypeV1.MULTIPLE_FILE:
        return getFilenamesFromMultipleUpload(field)
        break
      case FieldTypeV1.BOOLEAN:
        // fall though
      case FieldTypeV1.SINGLE_CHECKBOX:
        return value ? "Ja" : "Nein"
        break
      case FieldTypeV1.CHECKBOX:
        return generateCommaSeparatedListOfPossibleValueLabel(value as ArrayList<String>, field.possibleValues)
        break
      case FieldTypeV1.RADIO_BUTTONS:
        return findLabelForPossibleValue(field.possibleValues, value as String)
        break
      case FieldTypeV1.DROPDOWN_SINGLE_SELECT:
        // fall-through
      case FieldTypeV1.DROPDOWN_SINGLE_SELECT_AJAX:
        return findLabelForPossibleValue(field.possibleValues, value as String)
        break
      case FieldTypeV1.DROPDOWN_MULTIPLE_SELECT:
        return generateCommaSeparatedListOfPossibleValueLabel(value as ArrayList<String>, field.possibleValues)
        break
      case FieldTypeV1.TWO_LIST_SELECT:
        return generateCommaSeparatedListOfPossibleValueLabel(value as ArrayList<String>, field.possibleValues)
        break
      case FieldTypeV1.DATE:
        // The types of date fields differ between the process engineV1 and engineV2, so the respective date type must be used in each case.
        String dateString = value.class == Date ? new SimpleDateFormat("dd.MM.yyyy").format(value as Date) :
                (value as LocalDate).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        return dateString
        break
      case FieldTypeV1.TIME:
        // The types of time fields differ between the process engineV1 and engineV2, so the respective date type must be used in each case.
        String timeString = value.class == Date ? new SimpleDateFormat("HH:mm").format(value as Date) :
                (value as LocalTime).format(DateTimeFormatter.ofPattern("HH:mm"))
        return timeString
        break
      case FieldTypeV1.EURO_BETRAG:
        return (value as BigDecimal).toString() + " €"
        break
      case FieldTypeV1.SUBMITTED_WITH_NPA_INFO:
        return value ? "Sie waren mit dem neuem Personalausweis angemeldet" : "Sie waren NICHT mit dem neuem Personalausweis angemeldet"
        break
      case FieldTypeV1.GEO_MAP:
        // Its difficult to represent as GeoMap field as a String, so we just output the attributes we get from the API.
        // Note that the individual implementations of AbstractFormDumper might use their own rendering logic (which
        // uses more suitable behaviour than representing the GeoMap field as a String) before even calling this method.
        BinaryGeoMapContentV1 binaryGeoMapContentValue = field.value as BinaryGeoMapContentV1
        return "Nutzereingaben: '${binaryGeoMapContentValue.json}', Auswahl von Elementen auf der Karte: '${binaryGeoMapContentValue.selectionJson}'"
      case FieldTypeV1.GDIK_MAP:
        // Similar to GEO_MAP, its difficult to represent GDIK_MAP as a String.
        BinaryGDIKMapContentV1 binaryGDIKMapContentValue = field.value as BinaryGDIKMapContentV1
        return "Nutzereingaben: '${binaryGDIKMapContentValue.json}', Auswahl von Elementen auf der Karte: '${binaryGDIKMapContentValue.selectionJson}'"
      default:
        api.logger.warn("AbstractFormDumper.renderFieldForUserOutput does not know how to display this field '${field.type}' (${field.type.class.name}), " + "so it defaults to toString().")
        return value.toString()
        break
    }
  }

  /**
   * Get a simple String representation useful for technical processing.
   * E.g. Date fields will contain a RFC3339 `full-date` representation
   *
   * Note that this Method will throw a UnsupportedOperationException for field types where no reasonable generic
   * representation exists. (E.g. Upload-Fields, as their representation should depend on the concrete target format.)
   *
   * @param field The field to render
   * @return The String representation
   */
  @SuppressWarnings('GrDeprecatedAPIUsage')
  protected String renderFieldForTechnicalOutput(FormFieldV1 field) {
    def value = getValueFromField(field)

    //noinspection GroovyFallthrough - those fall-throughs are on purpose.
    switch (field.type) {

      case FieldTypeV1.STRING:
        // fall through
      case FieldTypeV1.STRING_AJAX_AUTOCOMPLETE:
        // fall through
      case FieldTypeV1.KFZ_KENNZEICHEN:
        // fall through
      case FieldTypeV1.TEXTAREA:
        // fall through
      case FieldTypeV1.DROPDOWN_SINGLE_SELECT:
        // fall-through
      case FieldTypeV1.RADIO_BUTTONS:
        // fall through
        return value
        break

        // Note that some target formats (like JSON) might want to overwrite this behaviour as Boolean is a data type in JSON.
      case FieldTypeV1.BOOLEAN:
        // fall through
      case FieldTypeV1.SUBMITTED_WITH_NPA_INFO:
        // fall through
      case FieldTypeV1.SINGLE_CHECKBOX:
        return value.toString()
        break

      case FieldTypeV1.DATE:
        // See https://datatracker.ietf.org/doc/html/rfc3339#section-5.6, `full-date`
        // The types of date fields differ between the process engineV1 and engineV2, so the respective date type must be used in each case.
        String dateString = value.class == Date ? new SimpleDateFormat("yyyy-MM-dd").format(value as Date) :
                (value as LocalDate).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return dateString
        break
      case FieldTypeV1.TIME:
        // See https://datatracker.ietf.org/doc/html/rfc3339#section-5.6, `partial-time`
        // The types of time fields differ between the process engineV1 and engineV2, so the respective date type must be used in each case.
        String timeString = value.class == Date ? new SimpleDateFormat("HH:mm:ss").format(value as Date) :
                (value as LocalTime).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        return timeString
        break

      case FieldTypeV1.EURO_BETRAG:
        return (value as BigDecimal).toPlainString() // Note that this uses a dot '.' as a decimal seperator
        break


      case FieldTypeV1.CHECKBOX:
        // fall through
      case FieldTypeV1.DROPDOWN_SINGLE_SELECT_AJAX:
        // fall through
      case FieldTypeV1.DROPDOWN_MULTIPLE_SELECT:
        // fall through
      case FieldTypeV1.TWO_LIST_SELECT:
        // comma-and-space-separated and enclosed in brackets.
        // e.g. [firstOption, secondOption]
        assert value instanceof List: "Sanity check failed: Expected type ${field.type} to be List-like, but it wasn't."
        return "[${(value as List).collect {it.toString()}.join(", ")}]"

      case FieldTypeV1.FILE:
        // fall through
      case FieldTypeV1.GEO_MAP:
        // fall through
      case FieldTypeV1.GDIK_MAP:
        // fall through
      case FieldTypeV1.MULTIPLE_FILE:
        throw new UnsupportedOperationException("renderFieldForTechnicalOutput failed for field type `${field.type}` " +
                "because there exists no generic way to express that field as a String. Please extend the " +
                "implementation of this FormDumper is such a way that it generated a implementation-specific structure" +
                "(e.g. a JsonDumper would probably construct a Map for a Upload-Field containing filename, " +
                "base64-encoded content, …) and does not call renderFieldForTechnicalOutput() in the first place.")

      default:
        api.logger.warn("AbstractFormDumper.renderFieldForTechnicalOutput does not know how to display this field '${field.type}' (${field.type.class.name}), " + "so it defaults to toString().")
        return value.toString()
        break
    }
  }

  /**
   * Generates a map of metadata with entries for postfachHandleId, form id, creation date
   * and the base64-encoded content of a binary content file named 'applicantFormAsPdf'.
   *
   * @return map of metadata
   */
  protected Map<String, String> collectMetadata() {
    Map<String, String> metadata = new HashMap<>()

    // Set dev or prod api url
    Map<String, Object> processConfig = api.getVariable("processEngineConfig", Map)
    String portal = processConfig.get("serviceportal.environment.main-portal-host").toString().trim()

    // Determine the value for startedByUser based on the portal
    StartedByUserV1 startedByUser
    if (portal.contains("amt24") || portal.contains("service-bw")) {
      startedByUser = api.getVariable("startedByUser", StartedByUserV1)
    } else {
      StartParameterV1 startParameter = api.getVariable("startParameter", StartParameterV1)
      startedByUser = startParameter.startedByUser
    }
    String postfachHandle = startedByUser.postfachHandle
    JsonSlurper jsonSlurper = new JsonSlurper()
    def postfachHandleMap = jsonSlurper.parseText(postfachHandle)
    String postfachHandleId = postfachHandleMap.id
    metadata.put("postfachHandleId", postfachHandleId)

    metadata.put("formId", form.id)

    SimpleDateFormat formatter = new SimpleDateFormat(ISO8601_FORMAT)
    String formattedCreationDate = formatter.format(new Date())
    metadata.put("creationDate", formattedCreationDate)

    BinaryContentV1 pdf = api.getVariable("applicantFormAsPdf", BinaryContentV1)
    if (pdf != null && pdf.data != null) {
      metadata.put("pdfApplicantFormBase64", pdf.data.encodeBase64().toString())
    } else {
      metadata.put("pdfApplicantFormBase64", null)
    }

    return metadata
  }

  /**
   * This function returns the value of a field in a reasonable format (depending on the fields type. String for text
   * fields, Date for date fields, …)
   *
   * This function also takes care of unwrapping fields with attached "Vertrauensniveaus", which would hide the actual
   * value of a field if used.
   */
  protected static def getValueFromField(FormFieldV1 field) {
    def value = field.value

    if (value == null) {
      // Exit immediately, don't attempt to parse the content further.
      return value
    }

    // If fields are filled by "Vertrauensniveaus", their value turn into VerifiedFormFieldValueV1 (which breaks the
    // other parts of this function). Therefore we turn those values back into their actual content.
    if (value.class == VerifiedFormFieldValueV1) {
      value = value.value
    }

    return value
  }

  private static String generateCommaSeparatedListOfPossibleValueLabel(List<String> values, List<PossibleValueV1> pvList) {
    String result = ""
    boolean isFirst = true
    values.each { technicalName ->
      if (!isFirst) {
        // Add separator
        result += ", "
      }
      result += findLabelForPossibleValue(pvList, technicalName)
      isFirst = false
    }
    return result
  }

  private static String findLabelForPossibleValue(List<PossibleValueV1> pvList, String value) {
    PossibleValueV1 pv = pvList.find { it.value == value }

    if (pv != null) {
      return pv.label
    } else {
      throw new IllegalArgumentException("Could not find possible value '$value' in PossibleValueList '$pvList'")
    }
  }

  private static String getFilenamesFromMultipleUpload(FormFieldV1 field) {
    String result = ""
    field.value.eachWithIndex { it, idx ->
      if (idx == (field.value as List<BinaryContentV1>).size() - 1) {
        result += "\"${(it as BinaryContentV1).uploadedFilename}\""
      } else {
        result += "\"${(it as BinaryContentV1).uploadedFilename}\", "
      }
    }
    return result.strip()
  }


}
