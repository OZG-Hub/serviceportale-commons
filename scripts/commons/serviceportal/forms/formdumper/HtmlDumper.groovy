package commons.serviceportal.forms.formdumper

import commons.serviceportal.forms.formdumper.dummy.AbstractFormDumper
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupInstanceV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldTypeV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGDIKMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGeoMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1

/**
 * Transforms a Serviceportal-proprietary form (= a FormContentV1 object) into HTML-tables, useful e.g. for showing
 * a summary page.
 *
 * See {@link commons.serviceportal.forms.formdumper.dummy.AbstractFormDumper}
 *
 * <h2>Example Output:</h2>
 * <h2>Main Group</h2>
 * <table class="summary-form">
 *   <thead>
 *     <tr>
 *       <th>Feld</th>
 *       <th>Ihre Eingabe</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>Yes/No</td>
 *       <td>Ja</td>
 *     </tr>
 *     <tr>
 *       <td>Textfield</td>
 *       <td>Textfield content</td>
 *     </tr>
 *     <tr>
 *       <td>Radio Buttons</td>
 *       <td>first label</td>
 *     </tr>
 *     <tr>
 *       <td>Checkbox List</td>
 *       <td>first label, second label</td>
 *     </tr>
 *     <tr>
 *       <td>Fileupload</td>
 *       <td>Datei: &quot;dummy.pdf&quot;</td>
 *     </tr>
 *     <tr>
 *       <td>Eurobetrag</td>
 *       <td>5.66 &euro;</td>
 *     </tr>
 *   </tbody>
 * </table>
 */
class HtmlDumper extends AbstractFormDumper {
  final int baseHeadingLevel
  final boolean tableWithRowHeaders

  /**
   * Initialize a new HtmlDumper.
   *
   * @param formContent See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param api See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param includeMetadata See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param param baseHeadingLevel the HTML-heading level (i.e. the "2" in "&lt;h2&gt;" used for the headings before each group
   *   instance)
   */
  HtmlDumper(FormContentV1 formContent, ScriptingApiV1 api, boolean includeMetadata, int baseHeadingLevel = 2, boolean tableWithRowHeaders = false) {
    super(formContent, api, includeMetadata)

    this.baseHeadingLevel = baseHeadingLevel
    this.tableWithRowHeaders = tableWithRowHeaders
  }

  @Override
  protected String metadataHook(String currentResult) {
    throw new UnsupportedOperationException("HtmlDumper was configured to output metadata, but this option is not supported as users are generally not supposed to see their metadata. " +
            "Consider if this actually what you want to do and the re-implement the metadataHook() if you want to continue.")
  }

  @Override
  protected String groupInstanceBeginHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    currentResult += "<h${baseHeadingLevel}>${groupInstance.title}</h${baseHeadingLevel}>"
    // General headings for the instance
    currentResult += "<table class=\"summary-form\">"
    // Set Column Headers only when tableWithRowHeaders = false
    if (!tableWithRowHeaders){
      currentResult += "<thead><tr><th>Feld</th><th>Ihre Eingabe</th></tr></thead>"
    }
    currentResult += "<tbody>"
    return currentResult
  }

  @Override
  protected String groupInstanceEndHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    currentResult += "</tbody>"
    currentResult += "</table>"
    return currentResult
  }

  @Override
  protected String fieldHook(String currentResult, FormFieldV1 field, FieldGroupInstanceV1 groupInstance) {
    currentResult += "<tr>"

    // Left column: The question
    // Use <td> or <th> depending on whether they are column headers or row headers
    if (tableWithRowHeaders) {
      currentResult += "<th>${field.label ?: ""}</th>"
    } else {
      currentResult += "<td>${field.label ?: ""}</td>"
    }

    // Right column: The answer
    currentResult += "<td>"


    switch (field.type) {
      case FieldTypeV1.GEO_MAP:
        // Special handling for GeoMap fields.
        // Those fields are best represented as an image-HTML-tag with embedded data (rather than text)
        BinaryGeoMapContentV1 fieldContent = field.value as BinaryGeoMapContentV1
        currentResult += generateEmbeddedImage(fieldContent.file)
        break
      case FieldTypeV1.GDIK_MAP:
        // Special handling for GDIK map fields.
        // Those fields are best represented as an image-HTML-tag with embedded data (rather than text)
        BinaryGDIKMapContentV1 fieldContent = field.value as BinaryGDIKMapContentV1
        for (file in fieldContent.files) {
          currentResult += generateEmbeddedImage(file)
        }
        break
      default:
        // Use a default String representation for this field
        // Also escape the result of renderFieldForUserOutput as it might contain XSS content
        currentResult += api.stringUtils.escapeHtml(renderFieldForUserOutput(field))
    }

    currentResult += "</td>"

    currentResult += "</tr>"

    return currentResult
  }

  /**
   * Generates an HTML tag containing an image file as base64 encoded data
   *
   * @param imageFile file containing the image data
   *
   * @return
   */
  private static String generateEmbeddedImage(BinaryContentV1 imageFile) {
    String base64OfImage = Base64.getEncoder().encodeToString(imageFile.data)
    return "<img src='data:image/jpeg;base64,${base64OfImage}' width='100%'>"
  }
}
