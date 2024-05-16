//file:noinspection UnnecessaryQualifiedReference - do NOT import org.apache.commons.text.StringEscapeUtils, as it cause duplicate imports of StringEscapeUtils by the gradle build plugin. See SBW-25576.
package commons.serviceportal.forms

import commons.serviceportal.helpers.ServiceportalLogger
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldTypeV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormRowV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormV1
import de.seitenbau.serviceportal.scripting.api.v1.form.PossibleValueV1
import de.seitenbau.serviceportal.scripting.api.v1.form.VerifiedFormFieldValueV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGeoMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormFieldContentV1
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.xml.MarkupBuilder
import groovy.xml.XmlUtil

import java.text.SimpleDateFormat

class FormDumper {
  private FormContentV1 formContent
  private ScriptingApiV1 api
  /**
   * Users of FormDumper can specify additional rules which decided if a field should be hidden. By default we show all
   * fields.
   */
  private Closure<Boolean> additionalLogicToHideContent = { FormFieldV1 field -> return false }

  static private final String iso8601Format = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
  static private final String CSV_SEPARATOR = ","

  /**
   * Creates a new FormDumper. A class used to transform Serviceportal forms into other formats.
   *
   * @param formContent The form to transform
   * @param additionalLogicToHideContent An optional closure to define additional rules to hide form fields. Input of
   * the closure is a FormFieldAndMapping. Output shall be a Boolean. True if the field should be hidden, false if the
   * field should be displayed.
   * @param api The API of the serviceportal. The only way to access this is via the variable "apiV1" that is available
   * in a script task (but unfortunately not inside a class in script tasks. That's why this parameter needs to be
   * provided)
   */
  FormDumper(
          FormContentV1 formContent,
          @ClosureParams(value = SimpleType.class, options = "de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1")
                  Closure<Boolean> additionalLogicToHideContent = null,
          ScriptingApiV1 api) {
    this.formContent = formContent

    if (additionalLogicToHideContent != null) {
      this.additionalLogicToHideContent = additionalLogicToHideContent
    }

    this.api = api
  }

  /**
   * Dump the content of a form as a HTML table formatted for the serviceportal.
   *
   * @param baseHeadingLevel the HTML-heading level for the top-most element (groups / accordions, etc)
   * @return a String containing HTML code
   */
  String dumpFormAsHtmlTable(int baseHeadingLevel = 2) {
    FormV1 formAndMapping = api.getForm(formContent.getFormId())
    formAndMapping.setContent(formContent)

    String result = ""

    formAndMapping.groupInstances.each { instance ->
      if (instance.isShown(formAndMapping)) {
        // Determine the content of that instance first, so we can decide later if we want to
        // show the (otherwise possibly empty) group
        String groupContentRendered = ""
        // check if instance is shown
        instance.rows.each { FormRowV1 row ->
          row.fields.each { FormFieldV1 field ->
            // For rendering NPA fields hideDisabled must be false
            if (shouldRenderField(field) && field.isShown(instance, formAndMapping)) {
              groupContentRendered += "<tr>"

              // Left column: The question
              groupContentRendered += "<td>${field.label}</td>"

              // Right column: The answer
              groupContentRendered += "<td>"

              if (field.type == FieldTypeV1.GEO_MAP) {
                // Special handling for GeoMap fields. Those fields are best represented as an image (rather than text)
                groupContentRendered += "<img src='data:image/jpeg;base64,${generateBase64StringOfImage(field.value as BinaryGeoMapContentV1)}' width='100%'>"
              } else {
                // Escape the result of renderFieldForUserOutput as it might contain XSS content
                groupContentRendered += org.apache.commons.text.StringEscapeUtils.escapeHtml4(renderFieldForUserOutput(field))
              }

              groupContentRendered += "</td>"

              groupContentRendered += "</tr>"
            }
          }
        }

        // Now, build the instance
        if (!groupContentRendered.isEmpty()) {
          result += "<h${baseHeadingLevel}>${instance.title}</h${baseHeadingLevel}>"
          // General headings for the instance
          result += "<table class=\"summary-form\">"
          result += "<thead><tr><th>Feld</th><th>Ihre Eingabe</th></tr></thead>"
          result += "<tbody>"
          result += groupContentRendered
          result += "</tbody>"
          result += "</table>"
        }
      }
    }

    return result
  }

  /**
   * Dump the content of a form as a human readable text
   *
   * @param printGroupHeadings set to true if groups in the form should have headings
   * @return a String containing a human readable version of the form
   */
  String dumpFormAsText(boolean printGroupHeadings = true) {
    FormV1 formAndMapping = api.getForm(formContent.getFormId())
    String result = ""

    formAndMapping.groupInstances.eachWithIndex { groupInstance, index ->
      boolean groupIsEmpty = true

      // Group heading
      if (printGroupHeadings) {
        String groupHeading = ""
        if (!groupInstance.title.empty) {
          groupHeading += groupInstance.title + " "
        }
        groupHeading += "(${groupInstance.id})"
        groupHeading += ":\n"
        result += groupHeading
        groupIsEmpty = false
      }


      groupInstance.rows.each { FormRowV1 row ->
        row.fields.each { FormFieldV1 field ->
          if (shouldRenderField(field) && field.isShown(groupInstance, formAndMapping)) {
            // Do not import StringEscapeUtils, see line 1
            result += "  ${field.label} >>> ${org.apache.commons.text.StringEscapeUtils.escapeHtml4(renderFieldForUserOutput(field))} <<<\n"
            groupIsEmpty = false
          }
        }
      }

      if (!groupIsEmpty) // only print newlines, if there actually was something to separate
        result += "\n"
    }

    if (result.length() != 0) {
      result = result.substring(0, result.length() - 1) // remove last newline
    }
    return result
  }

  /**
   * Dump the form as a simple CSV String in which the first column contains the form field name
   * and the second column the users input (as a technical value, e.g. "TRUE" / "FALSE" for
   * Yes/No-fields and the selected value (not the label) in a radio button)
   *
   * @return A String of representing the CSV files content
   */
  String dumpFormAsCsv() {
    String result = ""

    formContent.fields.each {
      result += it.key + CSV_SEPARATOR + escapeForCsv(it.value.value.toString()) + "\r\n"
    }

    return result
  }

  /**
   * See {@link #dumpFormAsCsv}, but the second column is moved to the third and now contains the
   * Java data type instead.
   * @return
   */
  String dumpFormAsCsvWithDatatype() {
    String result = ""

    formContent.fields.each {
      result += it.key + CSV_SEPARATOR
      def userInput = it?.value?.value
      if (userInput == null) {
        result += "null" + CSV_SEPARATOR
        result += "[no user input]" + "\r\n"
      } else {
        result += userInput.getClass().getSimpleName() + CSV_SEPARATOR
        result += escapeForCsv(userInput.toString()) + "\r\n"
      }
    }

    return result
  }

  /**
   * Dump the form as a XML string where there is a single 'serviceportal-fields', with sub-fields for each group,
   * which then has sub-fields for each group instance in this group (starting with "instance_"), which then has
   * sub-fields for each field.
   *
   * If a field is a BinaryContent, the value becomes <b>multiple</b> sub-fields "base64Data", "mimetype" and "filename"
   *
   * If a field is a List (e. g. a Multi-Select-Field), the value becomes <b>multiple</b> sub-fields, named
   * <code>selectedValue</code>.
   *
   * Example:
   * <pre>
   * {@code
   <serviceportal-fields>
   <mainGroupId>
   <instance_0>
   <textfield>TEXTFIELD</textfield>
   <simpleCheckbox>true</simpleCheckbox>
   <selectOptions>VAL2</selectOptions>
   <date>2015-07-08T00:00:00.000+02:00</date>
   <time>1970-01-01T11:55:00.000+01:00</time>
   <money>5.66</money>
   <checkboxList>
   <selectedValue>VAL1</selectedValue>
   <selectedValue>VAL2</selectedValue>
   </checkboxList>
   <fileupload>
   <base64Data>ABBREVIATED_IN_EXAMPLE</base64Data>
   <mimetype>application/pdf</mimetype>
   <filename>dummy.pdf</filename>
   </fileupload>
   </instance_0>
   </mainGroupId>
   </serviceportal-fields>}
   * </pre>
   *
   * @return The XML as a String
   */
  String dumpAsXml() {
    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)

    Set<String> groups = formContent.fields.keySet().collect { return it.split(":")[0] }
    xml."serviceportal-fields"() {
      groups.each { group ->
        assert group.matches("^[a-zA-Z_][\\w.-]*\$"): "Failed to create XML file. Group name '$group' is not a valid name for a XML node. Please change the group name."
        "$group"() {
          Set<Integer> groupInstances = formContent.fields.keySet().findAll { it.startsWith("$group:") }
                  .collect { Integer.parseInt(it.split(":")[1]) }
          groupInstances.each { groupInstance ->
            // XML tags can NOT start with a number, so we need to add a prefix like "instance_" to it.
            "instance_$groupInstance"() {
              Set<String> fields = formContent.fields.keySet().findAll { it.startsWith("$group:$groupInstance:") }
                      .collect { it.split(":")[2] }
              fields.each { field ->
                String fullKey = "$group:$groupInstance:$field".toString()
                assert field.matches("^[a-zA-Z_][\\w.-]*\$"): "Failed to create XML file. Field name '$field' is not a valid name for a XML node. Please change the field name."
                "${field}" { mkp.yieldUnescaped(renderFieldForXmlOutput(formContent.fields.get(fullKey))) }
              }
            }
          }
        }
      }
    }

    return writer.toString()
  }

  /**
   * Get a String representation useful for displaying it back to the user.
   * E.g. Checkboxes will contain the label the user selected, dates are formatted with a German
   * date format and no time component, etc.
   *
   * @param field The field to render
   * @return The String representation
   */
  @SuppressWarnings('GrDeprecatedAPIUsage')
  // We need to support deprecated form field types as they might still be in use by older forms
  private static String renderFieldForUserOutput(FormFieldV1 field) {
    if (field.value == null || field.value.toString().isAllWhitespace()) {
      return "[Keine Eingabe]"
    } else {
      //noinspection GroovyFallthrough - those fall-throughs are on purpose.
      switch (field.type) {
        case FieldTypeV1.STRING:
          // fall through
        case FieldTypeV1.STRING_AJAX_AUTOCOMPLETE:
          // fall through
        case FieldTypeV1.KFZ_KENNZEICHEN:
          // fall through
        case FieldTypeV1.TEXTAREA:
          if (field.value.class == VerifiedFormFieldValueV1) {
            return (field.value as VerifiedFormFieldValueV1).value
          } else {
            return field.value
          }
          break
        case FieldTypeV1.FILE:
          return "Datei: \"${(field.value as BinaryContentV1).uploadedFilename}\""
          break
        case FieldTypeV1.MULTIPLE_FILE:
          return getFilenamesFromMultipleUpload(field)
          break
        case FieldTypeV1.BOOLEAN:
          // fall though
        case FieldTypeV1.SINGLE_CHECKBOX:
          return field.value ? "Ja" : "Nein"
          break
        case FieldTypeV1.CHECKBOX:
          return generateCommaSeparatedListOfPossibleValueLabel(field.value as ArrayList<String>, field.possibleValues)
          break
        case FieldTypeV1.RADIO_BUTTONS:
          return findLabelForPossibleValue(field.possibleValues, field.value as String)
          break
        case FieldTypeV1.DROPDOWN_SINGLE_SELECT:
          // fall-through
        case FieldTypeV1.DROPDOWN_SINGLE_SELECT_AJAX:
          return findLabelForPossibleValue(field.possibleValues, field.value as String)
          break
        case FieldTypeV1.DROPDOWN_MULTIPLE_SELECT:
          return generateCommaSeparatedListOfPossibleValueLabel(field.value as ArrayList<String>, field.possibleValues)
          break
        case FieldTypeV1.TWO_LIST_SELECT:
          return generateCommaSeparatedListOfPossibleValueLabel(field.value as ArrayList<String>, field.possibleValues)
          break
        case FieldTypeV1.DATE:
          return (field.value as Date).format("dd.MM.yyyy")
          break
        case FieldTypeV1.TIME:
          return (field.value as Date).format("HH:mm")
          break
        case FieldTypeV1.EURO_BETRAG:
          return (field.value as BigDecimal).toString() + " €"
          break
        case FieldTypeV1.SUBMITTED_WITH_NPA_INFO:
          return field.value ? "Sie waren mit dem neuem Personalausweis angemeldet" : "Sie waren NICHT mit dem neuem Personalausweis angemeldet"
          break
        case FieldTypeV1.TEXT:
          // fall through
        case FieldTypeV1.H2:
          // fall through
        case FieldTypeV1.H1:
          // fall through
        case FieldTypeV1.PLACEHOLDER:
          // fall through
        case FieldTypeV1.DOWNLOAD:
          // fall through
        case FieldTypeV1.VIDEO:
          // fall through
        case FieldTypeV1.IMAGE:
          // User can't input data in this field type. So nothing is rendered.
          return ""
          break
        case FieldTypeV1.GEO_MAP:
          // Its difficult to represent as GeoMap field as a String, so we just output the attributes we get from the API.
          // Note that the methods calling renderFieldForUserOutput (like renderAsHtmlTable()) might have overridden and
          // more suitable behaviour than representing the GeoMap field as a String.
          BinaryGeoMapContentV1 value = field.value as BinaryGeoMapContentV1
          return "Nutzereingaben: '${value.json}', Auswahl von Elementen auf der Karte: '${value.selectionJson}'"
        default:
          ServiceportalLogger.logWarn("FormDumper.renderFieldForUserOutput does not know how to display this field '${field.type}' (${field.type.class.name}), " + "so it defaults to toString().")
          return field.value.toString()
          break
      }
    }

    throw new RuntimeException("Unexpected field type '${field.type}'. " + "The FormDumper class does not know how to render that.")
  }

  private static String renderFieldForXmlOutput(FormFieldContentV1 field) {
    def value = field.value

    if (value == null) {
      return "" // Empty fields have empty strings
    }

    switch (value.class) {
      case String:
        return XmlUtil.escapeXml(value as String)
        break
      case Boolean:
        return value ? "true" : "false"
        break
      case Date:
        Date date = value as Date
        SimpleDateFormat sdf = new SimpleDateFormat(iso8601Format, Locale.GERMAN)
        return sdf.format(date)
        break
      case BinaryContentV1:
        BinaryContentV1 bc = value as BinaryContentV1
        StringWriter bcWriter = new StringWriter()
        MarkupBuilder bcXml = new MarkupBuilder(bcWriter)
        bcXml."base64Data"(bc.data.encodeBase64().toString())
        bcXml."mimetype"(XmlUtil.escapeXml(bc.mimetype))
        bcXml."filename"(XmlUtil.escapeXml(bc.uploadedFilename))
        return bcWriter.toString()
        break
      case List:
        List list = value as List
        StringWriter listWriter = new StringWriter()
        MarkupBuilder listXml = new MarkupBuilder(listWriter)
        list.each {
          listXml."selectedValue"(XmlUtil.escapeXml(it as String))
        }
        return listWriter.toString()
        break
      case BigDecimal:
        return XmlUtil.escapeXml((value as BigDecimal).toPlainString())
        break
      case BinaryGeoMapContentV1:
        // JSON format is not predictable (see https://serviceportal-community.de/153, so just output the JSON as-is)
        return XmlUtil.escapeXml((value as BinaryGeoMapContentV1).json)
      default:
        ServiceportalLogger.logWarn("FormDumper.dumpAsFlatXml does not know how to display this class '${value.class}', " + "so it defaults to toString()")
        return value.toString()
    }
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

  /**
   * Determines if a field should be rendered or not. Fields will not be rendered, if they are of a type where users can
   * not enter any input (like headings, download buttons, etc.) or if additional logic from the
   * additionalLogicToHideContent parameter should be applied.
   *
   * @param field the field to check
   *
   * @return true, if the field should be shown
   */
  private boolean shouldRenderField(FormFieldV1 field) {
    // Don't show TEXT-fields the user can't input into
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

  private static String escapeForCsv(String stringToEscape) {
    // Add surrounding quotes and escape quotes that might appear in the string
    return "\"" + stringToEscape.replace("\"", "\"\"") + "\""
  }

  private static String generateBase64StringOfImage(BinaryGeoMapContentV1 content) {
    BinaryContentV1 imageFile = content.file as BinaryContentV1
    //Make a base64 String out of PNG
    String base64OfImage = Base64.getEncoder().encodeToString(imageFile.data)

    return base64OfImage

  }
}
