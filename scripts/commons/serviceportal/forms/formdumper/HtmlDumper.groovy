package commons.serviceportal.forms.formdumper

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupInstanceV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldTypeV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormRowV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGDIKMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGeoMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1

class HtmlDumper extends AbstractFormDumper {
  HtmlDumper(FormContentV1 formContent, ScriptingApiV1 api) {
    super(formContent, api)
  }

  /**
   * Dump the form as a HTML table.
   * The table uses the CSS class "summary-form" that is provided by the serviceportal itself.
   *
   * @param baseHeadingLevel the HTML-heading level (i.e. the "2" in "&lt;h2&gt;" used for the headings before each group
   *   instance)
   * @return a String containing HTML code
   */
  String dump(int baseHeadingLevel = 2) {
    String result = ""

    form.groupInstances.each { groupInstance ->
      String tableContent = dumpGroupInstance(groupInstance)

      if (tableContent == null) {
        // don't render the table for this group, as it does not contain anything.
      } else {
        result += "<h${baseHeadingLevel}>${groupInstance.title}</h${baseHeadingLevel}>"
        // General headings for the instance
        result += "<table class=\"summary-form\">"
        result += "<thead><tr><th>Feld</th><th>Ihre Eingabe</th></tr></thead>"
        result += "<tbody>"
        result += tableContent
        result += "</tbody>"
        result += "</table>"
      }
    }

    return result
  }

  /**
   * Dumps the content of a group as a String containing HTML-table-row (<tr>) and nested table-data (<td>) elements.
   *
   * @param groupInstance
   * @return
   */
  private String dumpGroupInstance(FieldGroupInstanceV1 groupInstance) {
    String result = ""

    groupInstance.rows.each { FormRowV1 row ->
      row.fields.each { FormFieldV1 field ->
        // For rendering NPA fields hideDisabled must be false
        if (shouldRenderField(field, groupInstance)) {
          result += "<tr>"

          // Left column: The question
          result += "<td>${field.label ?: ""}</td>"

          // Right column: The answer
          result += "<td>"


          switch (field.type) {
            case FieldTypeV1.GEO_MAP:
              // Special handling for GeoMap fields.
              // Those fields are best represented as an image-HTML-tag with embedded data (rather than text)
              BinaryGeoMapContentV1 fieldContent = field.value as BinaryGeoMapContentV1
              result += generateEmbeddedImage(fieldContent.file)
              break
            case FieldTypeV1.GDIK_MAP:
              // Special handling for GDIK map fields.
              // Those fields are best represented as an image-HTML-tag with embedded data (rather than text)
              BinaryGDIKMapContentV1 fieldContent = field.value as BinaryGDIKMapContentV1
              for (file in fieldContent.files) {
                result += generateEmbeddedImage(file)
              }
              break
            default:
              // Use a default String representation for this field
              // Also escape the result of renderFieldForUserOutput as it might contain XSS content
              result += api.stringUtils.escapeHtml(renderFieldForUserOutput(field))
          }

          result += "</td>"

          result += "</tr>"
        }
      }
    }

    if (result == "") {
      return null
    } else {
      return result
    }
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
