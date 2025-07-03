//file:noinspection UnnecessaryQualifiedReference - do NOT import org.apache.commons.text.StringEscapeUtils, as it cause duplicate imports of StringEscapeUtils by the gradle build plugin. See SBW-25576.
package commons.serviceportal.forms

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.*
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGDIKMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGeoMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import de.seitenbau.serviceportal.scripting.api.v1.start.StartedByUserV1
import de.seitenbau.serviceportal.scripting.api.v1.start.StartParameterV1
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
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
   * Dump the form as a simple CSV String in which the first column contains the form field name
   * and the second column the users input (as a technical value, e.g. "TRUE" / "FALSE" for
   * Yes/No-fields and the selected value (not the label) in a radio button)
   *
   * @param withMetadata A boolean that controls whether metadata is added or not (default = false). The resulting data
   * will be inserted at the beginning of the file
   *
   * @return A String of representing the CSV files content
   */
  String dumpFormAsCsv(boolean withMetadata = false) {
    String result = ""

    if (withMetadata) {
      Map<String, String> metadata = collectMetadata()
      metadata.keySet().each { key ->
        result += key + CSV_SEPARATOR + escapeForCsv(metadata.get(key)) + "\r\n"
      }
    }

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
   * Dump the form as a XML string where there is a single 'serviceportal' field. This field includes a 'serviceportal-fields' sub-field, with sub-fields for each group,
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
   <serviceportal>
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
   </serviceportal-fields>
   </serviceportal>}
   * </pre>
   *
   * @param withMetadata A boolean that controls whether a metadata-field is added or not (default = false)
   *
   * @return The XML as a String
   */
  String dumpAsXml(boolean withMetadata = false) {
    // Get additional data about the form itself (not just the content of the filled form)
    FormV1 formAndMapping = api.getForm(formContent.getFormId())
    formAndMapping.setContent(formContent)

    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)

    Set<String> groups = formContent.fields.keySet().collect { return it.split(":")[0] }

    xml."serviceportal"() {
      if (withMetadata) {
        Map<String, String> metadataMap = collectMetadata()
        // Add metadata
        "metadata"() {
          metadataMap.keySet().each { key ->
            "${key}"(XmlUtil.escapeXml(metadataMap.get(key)))
          }
        }
      }

      "serviceportal-fields"() {
        groups.each { group ->
          assert group.matches("^[a-zA-Z_][\\w.-]*\$"): "Failed to create XML file. Group name '$group' is not a valid name for a XML node. Please change the group name."
          "$group"() {
            Set<Integer> groupInstances = formContent.fields.keySet().findAll { it.startsWith("$group:") }
                    .collect { Integer.parseInt(it.split(":")[1]) }
            groupInstances.each { groupInstance ->
              // XML tags can NOT start with a number, so we need to add a prefix like "instance_" to it.
              "instance_$groupInstance"() {

                Set<String> fieldKeys = formContent.fields.keySet().findAll { it.startsWith("$group:$groupInstance:") }
                fieldKeys.each { fullKey ->
                  String fieldKey = fullKey.split(":")[2]
                  assert fieldKey != null

                  FormFieldV1 field = formAndMapping.getFieldInInstance(new FormFieldKeyV1(group, groupInstance, fieldKey))
                  if (shouldRenderField(field)) {

                    assert fieldKey.matches("^[a-zA-Z_][\\w.-]*\$"): "Failed to create XML file. Field name '$fieldKey' is not a valid name for a XML node. Please change the field name."

                    // Add field to XML object
                    "${fieldKey}" {
                      mkp.yieldUnescaped(renderFieldForXmlOutput(field))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return writer.toString()
  }

  /**
   * Dump the form as a Json string. It can either be a very simplified version of the user data or more complex.
   * The simplified version represents everything as a String or a List of Strings. For example uploaded files are only
   * represented with their filenames.
   * The more complex Json contains for example not only filenames for uploads but also the base64 representation.
   *
   * Example of a simplifies Json version:
   *
   * {
   *   "group1_0_textfield": "text",
   *   "group1_0_checkbox": [ "option1", "option3"],
   *   "group_1_upload": ["file.pdf" ]
   * }
   *
   * @param isSimplifiedJson A boolean that controls whether a simplified json is returned or a more complex json
   * @return a Json String
   */
  String dumpAsJson(boolean isSimplifiedJson) {
    // Get additional data about the form itself (not just the content of the filled form)
    FormV1 formAndMapping = api.getForm(formContent.getFormId())
    formAndMapping.setContent(formContent)

    Map fieldsAndValues = [:]
    // traverse through formAndMapping
    formAndMapping.groupInstances.each { instance ->
      instance.rows.each { row ->
        row.fields.each { field ->
          String fieldId = "${instance.id}_${instance.index}_${field.id}"
          if (field.value == null || field.value.toString().isAllWhitespace()) {
            fieldsAndValues.put(fieldId, "")
          } else if (shouldRenderField(field) && field.isShown(instance, formAndMapping)) {
            switch (field.type) {
              case FieldTypeV1.TIME:
                fieldsAndValues.put(fieldId, (field.value as Date).format("HH:mm"))
                break
              case FieldTypeV1.DATE:
                fieldsAndValues.put(fieldId, (field.value as Date).format("dd.MM.yyyy"))
                break
              case FieldTypeV1.MULTIPLE_FILE:
                if (isSimplifiedJson) {
                  def fileNames = (field.value as List<BinaryContentV1>).collect { it.uploadedFilename }
                  fieldsAndValues.put(fieldId, fileNames)
                } else {
                  List uploadedFiles = []
                  (field.value as List<BinaryContentV1>).each {
                    String fileAsBase64 = it.data.encodeBase64()
                    Map file = ["filename"    : it.uploadedFilename,
                                "fileAsBase64": fileAsBase64
                    ]
                    uploadedFiles.add(file)
                  }
                  fieldsAndValues.put(fieldId, uploadedFiles)
                }
                break
              case FieldTypeV1.FILE: // This is deprecated but still in use for old processes
                if (isSimplifiedJson) {
                  fieldsAndValues.put(fieldId, (field.value as BinaryContentV1).uploadedFilename)
                } else {
                  String fileAsBase64 = (field.value as BinaryContentV1).data.encodeBase64()
                  Map file = ["filename"    : (field.value as BinaryContentV1).uploadedFilename,
                              "fileAsBase64": fileAsBase64
                  ]
                  fieldsAndValues.put(fieldId, file)
                }
                break
              case FieldTypeV1.GEO_MAP:
                BinaryGeoMapContentV1 content = field.value as BinaryGeoMapContentV1
                fieldsAndValues.put(fieldId, [content.json, content.selectionJson, content.searchJson])
                break
              case FieldTypeV1.GDIK_MAP:
                BinaryGDIKMapContentV1 content = field.value as BinaryGDIKMapContentV1
                fieldsAndValues.put(fieldId, [content.json, content.selectionJson, content.searchJson])
                break
              default:
                // For most fields it makes sense to use the return values as is
                if (field.value.class == VerifiedFormFieldValueV1) {
                  fieldsAndValues.put(fieldId, (field.value as VerifiedFormFieldValueV1).value)
                } else {
                  fieldsAndValues.put(fieldId, field.value)
                }
                break
            }
          }
        }
      }
    }
    return new JsonBuilder(fieldsAndValues).toPrettyString()
  }

  private String renderFieldForXmlOutput(FormFieldV1 field) {
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
        api.logger.warn("FormDumper.dumpAsFlatXml does not know how to display this class '${value.class}', " + "so it defaults to toString()")
        return value.toString()
    }
  }

  private static String escapeForCsv(String stringToEscape) {
    // Add surrounding quotes and escape quotes that might appear in the string
    return "\"" + stringToEscape.replace("\"", "\"\"") + "\""
  }

  /**
   * Generates a map of metadata with entries for postfachHandleId, form id, creation date
   * and the base64-encoded content of a binary content file named 'applicantFormAsPdf'.
   *
   * @return map of metadata
   */
  private Map<String, String> collectMetadata(){
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

    metadata.put("formId", formContent.formId)

    SimpleDateFormat formatter = new SimpleDateFormat(iso8601Format)
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
}
