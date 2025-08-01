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
import groovy.json.JsonBuilder

/**
 * Transforms a Serviceportal-proprietary form (= a FormContentV1 object) into a JSON structure where field keys are
 * json key and values depend on the field type of the corresponding field.
 *
 * See {@link commons.serviceportal.forms.formdumper.dummy.AbstractFormDumper}
 *
 * <h2>Example Output:</h2>
 * (Assuming fileNamesOnly = true)
 * <pre>
 * {
 *   "group1_0_textfield": "text",
 *   "group1_0_checkbox": [ "option1", "option3"],
 *   "group_1_upload": ["file.pdf" ]
 * }
 * </pre>
 */
class JsonDumper extends AbstractFormDumper {
  /**
   * Creating a json object is much easier by creating a "buffer" map, filling it during the hooks and building it via
   * groovy tools rather than constructing a String by itself.
   */
  Map fieldsBuffer = [:]
  boolean fileNamesOnly

  /**
   * Initialize a new JsonDumper.
   *
   * @param formContent See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param api See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param includeMetadata See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}.
   *   When set to true, the end result's root element will contain a "metadata" and "fields" attribute.
   *   When set to false, the end result's root element will directly list all fields.
   * @param fileNamesOnly If true: Upload & Multi-Upload fields will only list the file name. If false: Each file will
   *   be a structure, containing both a "filename" and "fileAsBase64" attribute.
   */
  JsonDumper(FormContentV1 formContent, ScriptingApiV1 api, boolean includeMetadata, boolean fileNamesOnly = true) {
    super(formContent, api, includeMetadata)
    this.fileNamesOnly = fileNamesOnly
  }

  @Override
  protected String metadataHook(String currentResult) {
    // No changes to the current Result. This Dumper has it's own buffer and only constructs a results-object in the last step.
    return currentResult
  }

  @Override
  protected String groupInstanceBeginHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    // No changes to the current Result. This Dumper has it's own buffer and only constructs a results-object in the last step.
    return currentResult
  }

  @Override
  protected String groupInstanceEndHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    // No changes to the current Result. This Dumper has it's own buffer and only constructs a results-object in the last step.
    return currentResult
  }

  @Override
  protected String fieldHook(String currentResult, FormFieldV1 field, FieldGroupInstanceV1 groupInstance) {
    String fieldId = "${groupInstance.id}_${groupInstance.index}_${field.id}"

    //noinspection GrDeprecatedAPIUsage - we need to support some deprecated fieldTypes as they might still be in use
    switch (field.type) {
      case FieldTypeV1.MULTIPLE_FILE:
        if (fileNamesOnly) {
          List<String> fileNames = (field.value as List<BinaryContentV1>).collect { it.uploadedFilename }
          fieldsBuffer.put(fieldId, fileNames)
        } else {
          List uploadedFiles = []
          (field.value as List<BinaryContentV1>).each {
            uploadedFiles.add(binaryContentToMap(it))
          }
          fieldsBuffer.put(fieldId, uploadedFiles)
        }
        break
      case FieldTypeV1.FILE: // This is deprecated but still in use for old processes
        if (fileNamesOnly) {
          fieldsBuffer.put(fieldId, (field.value as BinaryContentV1).uploadedFilename)
        } else {
          fieldsBuffer.put(fieldId, binaryContentToMap(field.value))
        }
        break
      case FieldTypeV1.GEO_MAP:
        BinaryGeoMapContentV1 content = field.value as BinaryGeoMapContentV1
        fieldsBuffer.put(fieldId, [
                json         : content.json,
                selectionJson: content.selectionJson,
                searchJson   : content.searchJson,
        ])
        break
      case FieldTypeV1.GDIK_MAP:
        BinaryGDIKMapContentV1 content = field.value as BinaryGDIKMapContentV1
        fieldsBuffer.put(fieldId, [
                json         : content.json,
                selectionJson: content.selectionJson,
        ])
        break
      case FieldTypeV1.BOOLEAN:
        fieldsBuffer.put(fieldId, field.value)
        break
      default:
        // Use a default technical representation
        fieldsBuffer.put(fieldId, renderFieldForTechnicalOutput(field))
        break
    }

    return currentResult
  }

  @Override
  protected String dumpingDoneHook(String currentResult) {
    Map result

    if (includeMetadata) {
      result = [
              metadata: collectMetadata(),
              fields  : fieldsBuffer,
      ]
    } else {
      result = fieldsBuffer
    }

    return new JsonBuilder(result).toPrettyString()
  }

  /**
   * Transforms a BinaryContent object to a Map-Structure, as this can happen on multiple field types.
   *
   * @param bc The BinaryContent object to dump
   * @return
   */
  private static Map<String, String> binaryContentToMap(BinaryContentV1 bc) {
    Map result = [
            "filename"    : bc.uploadedFilename,
            "fileAsBase64": bc.data.encodeBase64().toString()
    ]
    return result
  }
}
