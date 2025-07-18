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


}
