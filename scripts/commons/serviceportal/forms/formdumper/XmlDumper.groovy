package commons.serviceportal.forms.formdumper

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupInstanceV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldTypeV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldKeyV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGeoMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import groovy.xml.MarkupBuilder
import groovy.xml.XmlUtil

import java.text.SimpleDateFormat

/**
 * Transforms a Serviceportal-proprietary form (= a FormContentV1 object) into XML files.
 *
 * See {@link AbstractFormDumper}
 *
 * <h2>Example Output:</h2>
 *
 * <pre>
 * <serviceportal>
 * <serviceportal-fields>
 * <mainGroupId>
 * <instance_0>
 * <textfield>TEXTFIELD</textfield>
 * <simpleCheckbox>true</simpleCheckbox>
 * <selectOptions>VAL2</selectOptions>
 * <date>2015-07-08T00:00:00.000+02:00</date>
 * <time>1970-01-01T11:55:00.000+01:00</time>
 * <money>5.66</money>
 * <checkboxList>
 * <selectedValue>VAL1</selectedValue>
 * <selectedValue>VAL2</selectedValue>
 * </checkboxList>
 * <fileupload>
 * <base64Data>ABBREVIATED_IN_EXAMPLE</base64Data>
 * <mimetype>application/pdf</mimetype>
 * <filename>dummy.pdf</filename>
 * </fileupload>
 * </instance_0>
 * </mainGroupId>
 * </serviceportal-fields>
 * </serviceportal>
 * </pre>
 */
class XmlDumper extends AbstractFormDumper {

  /**
   * Initialize a new XmlDumper.
   * @param formContent See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param api See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param includeMetadata See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   */
  XmlDumper(FormContentV1 formContent, ScriptingApiV1 api, boolean includeMetadata = false) {
    super(formContent, api, includeMetadata)

  }

  String dump() {
    // TODO: Massively refactor - or delete completely if we implement the "abstract `eachNewGroup()` `eachSection` und `eachField()`" solution

    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)


    // TODO: Re-Implement iteration logic. Following same schema as TextDumper
    Set<String> groups = form.fields.keySet().collect { return it.split(":")[0] }

    xml."serviceportal"() {

      // Add optional metadata
      if (includeMetadata) {
        Map<String, String> metadataMap = collectMetadata()
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

  @Override
  String metadataHook() {
    return null
  }

  @Override
  String groupInstanceBeginHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    return null
  }

  @Override
  String groupInstanceEndHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    return null
  }

  @Override
  String fieldHook(String currentResult, FormFieldV1 field, FieldGroupInstanceV1 groupInstance) {
    return null
  }

  @Override
  String dumpingDoneHook(String currentResult) {
    return null
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
}
