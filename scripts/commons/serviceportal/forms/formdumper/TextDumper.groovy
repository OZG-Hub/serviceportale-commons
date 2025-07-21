package commons.serviceportal.forms.formdumper

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupInstanceV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1

/**
 * Transforms a Serviceportal-proprietary form (= a FormContentV1 object) into raw, human-readable text.
 *
 * See {@link AbstractFormDumper}
 *
 * <h2>Example Output:</h2>
 *
 * <pre>
 * Main Group (mainGroupId):
 *   Time >>> 10:44 <<<
 *   Yes/No >>> Ja <<<
 *   Textfield >>> Textfield content <<<
 *   Sinple Checkbox >>> Ja <<<
 *   Radio Buttons >>> first label <<<
 *   Checkbox List >>> first label, second label <<<
 *   Fileupload >>> Datei: "dummy.pdf" <<<
 *   Date >>> 09.08.2015 <<<
 *   Eurobetrag >>> 5.66 € <<<
 * </pre>
 */
class TextDumper extends AbstractFormDumper {
  final boolean printGroupHeadings
  final boolean escapeHtml


  /**
   * Creates a new TextDumper.
   *
   * @param formContent See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param api See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param includeMetadata See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param printGroupHeadings set to true if groups in the form should have headings.
   *   (Those heading do not have any special formatting. They simply show up as:
   *   "GroupInstanceName (GroupInstanceId):\n")
   * @param escapeHtml Whether to HTML-escape user input. Set to true, if you intend to display the output in a web
   *   browser to avoid XSS. Consider {@link HtmlDumper} as an alternative.
   *
   */
  TextDumper(FormContentV1 formContent,
             ScriptingApiV1 api,
             boolean includeMetadata,
             boolean printGroupHeadings = true,
             boolean escapeHtml = true) {
    super(formContent, api, includeMetadata)

    this.printGroupHeadings = printGroupHeadings
    this.escapeHtml = escapeHtml
  }

  @Override
  String metadataHook() {
    String result = "Metadaten:\n"

    collectMetadata().each { key, value ->
      result += "  $key: >>> $value <<<\n"
    }

    result += "\n" // Add extra newline to separate groups

    return result
  }

  @Override
  String groupInstanceBeginHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    String result = currentResult

    if (printGroupHeadings) {
      result += "${groupInstance.title} (${groupInstance.id}):\n"
    }

    return result
  }

  @Override
  String groupInstanceEndHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    // Add extra newline to separate groups
    return currentResult + "\n"
  }

  @Override
  String fieldHook(String currentResult, FormFieldV1 field, FieldGroupInstanceV1 groupInstance) {
    String userInput = renderFieldForUserOutput(field)
    if (escapeHtml) {
      userInput = api.stringUtils.escapeHtml(userInput)
    }

    // Indent 2 spaces, then print label and user's input
    currentResult += "  ${field.label} >>> $userInput <<<\n"
    return currentResult
  }

  @Override
  String dumpingDoneHook(String currentResult) {
    // remove last newline
    if (currentResult.length() != 0) {
      currentResult = currentResult.substring(0, currentResult.length() - 1)
    }
    return currentResult
  }
}
