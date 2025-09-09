package commons.serviceportal.forms.formdumper

import commons.serviceportal.forms.formdumper.dummy.AbstractFormDumper
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupInstanceV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryGeoMapContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import groovy.xml.MarkupBuilder
import groovy.xml.XmlUtil

/**
 * Transforms a Serviceportal-proprietary form (= a FormContentV1 object) into XML files.
 *
 * See {@link commons.serviceportal.forms.formdumper.dummy.AbstractFormDumper}
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
  final static private String ROOT_TAG = "serviceportal"
  final static private String FIELDS_TAG = "serviceportal-fields"

  /**
   * XML tags can NOT start with a number, so we need to add a prefix like "instance_" to it.
   */
  final static private String instance_prefix = "instance_"

  private boolean isFirstGroup = true
  private String lastOpenedGroup = null

  /**
   * Initialize a new XmlDumper.
   * @param formContent See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param api See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   * @param includeMetadata See {@link AbstractFormDumper#AbstractFormDumper(FormContentV1, ScriptingApiV1)}
   */
  XmlDumper(FormContentV1 formContent, ScriptingApiV1 api, boolean includeMetadata = false) {
    super(formContent, api, includeMetadata)
  }

  /**
   * Called at the beginning of the dumping process to setup the result object.
   * Default implementation returns an empty String.
   *
   * @return
   */
  @Override
  protected String dumpingStartHook() {
    return "<$ROOT_TAG>"
  }

  @Override
  protected String metadataHook(String currentResult) {
    // Prepare groovy structure for XmlOutput
    def writer = new StringWriter()
    def xml = new MarkupBuilder(writer)

    Map<String, String> metadataMap = collectMetadata()
    xml."metadata"() {
      metadataMap.keySet().each { key ->
        "${key}"(XmlUtil.escapeXml(metadataMap.get(key)))
      }
    }

    // Output xml as String
    return currentResult + writer.toString()
  }

  @Override
  protected String groupInstanceBeginHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    if (isFirstGroup) {
      // Open the tag for form fields, before printing the first group (i.e. only open it once)
      currentResult += "<$FIELDS_TAG>"
      isFirstGroup = false
    }

    // Validate that the name of the instance is actually usable in XML.
    String id = groupInstance.id
    assert isValidXmlTagName(id): "Failed to create XML file. Group id '$id' is not a valid name for a XML node. Please change the group name."

    if (lastOpenedGroup != id && lastOpenedGroup != null) {
      // close previous open group tag as this one is no longer in use
      currentResult += "</$lastOpenedGroup>"
    }

    // open tag for group, but only, if we are not in it already
    if (lastOpenedGroup != id) {
      currentResult += "<$id>"
      lastOpenedGroup = id
    }

    // open tag for current instance
    currentResult += "<${instance_prefix}${groupInstance.index}>"

    return currentResult
  }

  @Override
  protected String groupInstanceEndHook(String currentResult, FieldGroupInstanceV1 groupInstance) {
    // close tag for current instance
    currentResult += "</${instance_prefix}${groupInstance.index}>"
    currentResult += "</$lastOpenedGroup>"
    return currentResult
  }

  @Override
  protected String fieldHook(String currentResult, FormFieldV1 field, FieldGroupInstanceV1 groupInstance) {
    String id = field.id
    assert isValidXmlTagName(id): "Failed to create XML file. Field name '$id' is not a valid name for a XML node. Please change the field name."

    currentResult += "<$id>"
    currentResult += renderFieldForXmlOutput(field)
    currentResult += "</$id>"

    return currentResult
  }

  @Override
  protected String dumpingDoneHook(String currentResult) {
    currentResult += "</$FIELDS_TAG>"
    currentResult += "</$ROOT_TAG>"

    // Sanity check: Verify the result is actually valid XML
    try {
      new XmlParser().parseText(currentResult)
    } catch (Exception e) {
      throw new Exception("Sanity check failed. XmlDumper did not generate valid XML. Please fix the XmlDumper class.", e)
    }

    // Pretty print the result
    return XmlUtil.serialize(currentResult)
  }

  private String renderFieldForXmlOutput(FormFieldV1 field) {
    def value = getValueFromField(field)

    if (value == null) {
      return "" // Empty fields have empty strings
    }

    switch (value.class) {
      case String:
        return XmlUtil.escapeXml(value as String)
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
      case BinaryGeoMapContentV1:
        // JSON format is not predictable (see https://serviceportal-community.de/153, so just output the JSON as-is)
        return XmlUtil.escapeXml((value as BinaryGeoMapContentV1).json)
      default:
        // Use a default technical representation, but make sure to escape it!
        return XmlUtil.escapeXml(renderFieldForTechnicalOutput(field))
    }
  }

  private static boolean isValidXmlTagName(String name) {
    String xmlPattern = /^[A-Za-z_][\w.-]*$/

    // Check if name follows basic valid XML name regex
    if (!name.matches(xmlPattern)) {
      return false
    }

    // Ensure name doesn't start with 'xml', case-insensitive
    if (name.toLowerCase().startsWith("xml")) {
      return false
    }

    return true
  }

}
