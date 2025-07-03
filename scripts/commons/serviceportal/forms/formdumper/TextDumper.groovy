package commons.serviceportal.forms.formdumper

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormRowV1
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

  /**
   * Creates a new TextDumper.
   *
   * @param formContent The form to transform
   * @param api The API of the serviceportal. The only way to access this is via the variable "apiV1" that is available
   *   in a script task.
   */
  TextDumper(FormContentV1 formContent, ScriptingApiV1 api) {
    super(formContent, api)
  }

  /**
   * Dump the content of a form as a human-readable text.
   *
   * @param printGroupHeadings set to true if groups in the form should have headings.
   *   (Those heading do not have any special formatting. They simply show up as:
   *   "GroupInstanceName (GroupInstanceId):\n")
   * @param escapeHtml Whether to HTML-escape user input. Set to true, if you intend to display the output in a web
   *   browser to avoid XSS.
   * @return a String containing a human readable version of the form
   */
  String dump(boolean printGroupHeadings = true, boolean escapeHtml = true) {
    String result = ""

    form.groupInstances.eachWithIndex { groupInstance, index ->
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
          if (shouldRenderField(field, groupInstance)) {
            String userInput = renderFieldForUserOutput(field)
            if (escapeHtml) {
              userInput = api.stringUtils.escapeHtml(userInput)
            }

            result += "  ${field.label} >>> $userInput <<<\n"
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

}
