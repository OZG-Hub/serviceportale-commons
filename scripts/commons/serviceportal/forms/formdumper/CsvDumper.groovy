package commons.serviceportal.forms.formdumper

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupInstanceV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldTypeV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1

import java.text.SimpleDateFormat

/**
 * Transforms a Serviceportal-proprietary form (= a FormContentV1 object) into CSV files.
 * Fields are seperated by a separator character (see constructor) and always surrounded by quotes.
 * Individual fields are listed in rows, not columns. I.e. the output is "tall" (instead of "wide").
 *
 * See {@link AbstractFormDumper}
 *
 * <h2>Example Output:</h2>
 *
 * <pre>
 * mainGroupId:0:time,"10:44"
 * mainGroupId:0:yesno,"true"
 * mainGroupId:0:textfield,"Textfield content"
 * mainGroupId:0:simpleCheckbox,"true"
 * mainGroupId:0:radioButtons,"firstOption"
 * mainGroupId:0:textarea,"Textarea
 * content"
 * mainGroupId:0:multiselect,"[firstOption, secondOption]"
 * mainGroupId:0:fileupload,"UERGIGNvbnRlbnQ="
 * mainGroupId:0:date,"2015-08-09"
 * </pre>
 */
class CsvDumper extends AbstractFormDumper {
  private final String separator
  private final String lineTerminator = "\r\n" // CRLF, see RFC 4180

  /**
   * Initialize a new CsvDumper.
   * @param formContent See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param api See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param includeMetadata See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param separator A character to separate individual fields.
   *        Defaults to the comma "," (as described in RFC 4180).
   *        If the target audience uses MS Excel with a German language setting, it might be useful to overwrite that
   *          to a semicolon ";" instead.
   */
  CsvDumper(FormContentV1 formContent, ScriptingApiV1 api, boolean includeMetadata, String separator = ",") {
    super(formContent, api, includeMetadata)

    this.separator = separator
  }

  @Override
  protected String metadataHook(String currentResult) {
    collectMetadata().each {key, value ->
      currentResult += key + separator + escapeForCsv(value) + lineTerminator
    }

    return currentResult
  }

  @Override
  protected String groupInstanceBeginHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    // No changes when a new group starts
    return currentResult
  }

  @Override
  protected String groupInstanceEndHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    // No changes when a group ends
    return currentResult
  }

  @Override
  protected String fieldHook(String currentResult, FormFieldV1 field, FieldGroupInstanceV1 groupInstance) {
    String fieldKey = groupInstance.id + ":" + groupInstance.index + ":" + field.id

    String fieldValueAsString
    switch (field.type) {
      case FieldTypeV1.DATE:
        fieldValueAsString = new SimpleDateFormat("yyyy-MM-dd").format(field.value as Date)
        break
      case FieldTypeV1.TIME:
        fieldValueAsString = new SimpleDateFormat("HH:mm").format(field.value as Date)
        break
      case FieldTypeV1.FILE:
        // Output is base64 encoded data
        fieldValueAsString = (field.value as BinaryContentV1).data.encodeBase64()
        break
      case FieldTypeV1.MULTIPLE_FILE:
        // Output is wrapped base64 encoded data Example: [abc123, def456]
        List<BinaryContentV1> multiUpload = field.value
        fieldValueAsString = "[${multiUpload.each { it.data.encodeBase64() }.join(",")}]"
        break
      default:
        fieldValueAsString = field.value.toString()
    }

    currentResult += fieldKey + separator + escapeForCsv(fieldValueAsString) + lineTerminator
    return currentResult
  }

  private static String escapeForCsv(String stringToEscape) {
    // Normalize newline characters (CSV uses CRLF): Replace (Non-CR)LF with CRLF
    stringToEscape = stringToEscape.replaceAll(/(?<!\r)\n/, "\r\n")

    // Add surrounding quotes and escape quotes that might appear in the string
    return "\"" + stringToEscape.replace("\"", "\"\"") + "\""
  }
}
