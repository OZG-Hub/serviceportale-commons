import commons.serviceportal.forms.JsonToFormContentConverter
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.StringUtilsApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupInstanceV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldGroupV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldTypeV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormRowV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormSectionV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormV1
import de.seitenbau.serviceportal.scripting.api.v1.form.PossibleValueListV1
import de.seitenbau.serviceportal.scripting.api.v1.form.PossibleValueV1
import de.seitenbau.serviceportal.scripting.api.v1.form.VerifiedFormFieldValueV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormFieldContentV1
import de.seitenbau.serviceportal.scripting.api.v1.start.StartedByUserV1
import spock.lang.Specification

import java.text.SimpleDateFormat

import commons.serviceportal.forms.FormDumper

class FormDumperSpecification extends Specification {

  public static final String MAIN_GROUP_ID = "mainGroupId"
  private static ScriptingApiV1 mockedApi

  void addFieldToInstance(FieldGroupInstanceV1 groupInstance, String fieldId, FieldTypeV1 type, String label)
  {
    FormFieldV1 field = new FormFieldV1(fieldId, type)
    field.setLabel(label)
    FormRowV1 row = FormRowV1.builder().fields([field]).build()
    groupInstance.getRows().add(row)
  }

  FormV1 createEmptyForm()
  {
    FormV1 form = new FormV1("6000357:testform:v1.0")
    FieldGroupV1 fieldGroup = new FieldGroupV1(MAIN_GROUP_ID)
    FormSectionV1 section = FormSectionV1.builder().fieldGroups([fieldGroup]).build()
    form.getSections().add(section)
    return form
  }

  def setup() {
    // Mock scripting API
    mockedApi = Mock(ScriptingApiV1)

    // Mock form
    FormV1 form = createEmptyForm()
    FieldGroupInstanceV1 fieldGroupInstance = form.getGroupInstance(MAIN_GROUP_ID, 0)
    fieldGroupInstance.setTitle("Main Group")
    addFieldToInstance(fieldGroupInstance, "textanzeige", FieldTypeV1.TEXT, "Textanzeige")
    addFieldToInstance(fieldGroupInstance, "time", FieldTypeV1.TIME, "Time")
    addFieldToInstance(fieldGroupInstance, "yesno", FieldTypeV1.BOOLEAN, "Yes/No")
    addFieldToInstance(fieldGroupInstance, "npa", FieldTypeV1.SUBMITTED_WITH_NPA_INFO, "NPA")
    addFieldToInstance(fieldGroupInstance, "textfield", FieldTypeV1.STRING, "Textfield")
    addFieldToInstance(fieldGroupInstance, "simpleCheckbox", FieldTypeV1.SINGLE_CHECKBOX, "Sinple Checkbox")
    addFieldToInstance(fieldGroupInstance, "radioButtons", FieldTypeV1.RADIO_BUTTONS, "Radio Buttons")
    addFieldToInstance(fieldGroupInstance, "textarea", FieldTypeV1.TEXTAREA, "Textarea")
    addFieldToInstance(fieldGroupInstance, "multiselect", FieldTypeV1.DROPDOWN_MULTIPLE_SELECT, "Multiselect")
    PossibleValueListV1 pvList = new PossibleValueListV1()
    pvList.add(new PossibleValueV1("first label", "firstOption", null))
    pvList.add(new PossibleValueV1("second label", "secondOption", null))
    fieldGroupInstance.getField("multiselect").setPossibleValues(pvList)
    addFieldToInstance(fieldGroupInstance, "checkboxList", FieldTypeV1.CHECKBOX, "Checkbox List")
    fieldGroupInstance.getField("checkboxList").setPossibleValues(pvList)
    BinaryContentV1 mockedBinaryContent = Mock(BinaryContentV1)
    mockedBinaryContent.data >> "test content".getBytes("UTF-8")
    mockedBinaryContent.mimetype >> "text/plain"
    mockedBinaryContent.uploadedFilename >> "test.txt"
    addFieldToInstance(fieldGroupInstance, "fileupload", FieldTypeV1.FILE, "Fileupload")
    addFieldToInstance(fieldGroupInstance, "h2", FieldTypeV1.H2, "H2")
    addFieldToInstance(fieldGroupInstance, "h1", FieldTypeV1.H1, "H1")
    addFieldToInstance(fieldGroupInstance, "date", FieldTypeV1.DATE, "Date")

    addFieldToInstance(fieldGroupInstance, "ca4618b9", FieldTypeV1.PLACEHOLDER, "Placeholder")
    addFieldToInstance(fieldGroupInstance, "selectOptions", FieldTypeV1.DROPDOWN_SINGLE_SELECT, "SelectOptions")
    fieldGroupInstance.getField("selectOptions").setPossibleValues(pvList)
    addFieldToInstance(fieldGroupInstance, "money", FieldTypeV1.EURO_BETRAG, "Eurobetrag")
    addFieldToInstance(fieldGroupInstance, "name", FieldTypeV1.STRING, "Name")

    mockedApi.getVariable("processEngineConfig", Map) >> ["serviceportal.environment.main-portal-host":"dev.service-bw.de"]

    mockedApi.getForm("6000357:testform:v1.0") >> form

    // Mock api for metadata
    StartedByUserV1 startedByUser = new StartedByUserV1("1","user","user", "user", "{\"@type\":\"nkb\",\"id\":\"ab0b63be-ee10-4740-b5e7-66aa81834510\"}")
    mockedApi.getVariable("startedByUser", StartedByUserV1) >> startedByUser

    byte[] pdfContent = getClass().getResourceAsStream("resources/dummy.pdf").readAllBytes()
    BinaryContentV1 mockedPdf = new BinaryContentV1("key","dummy.pdf","label","application/pdf", pdfContent)
    mockedApi.getVariable("applicantFormAsPdf", BinaryContentV1) >> mockedPdf

    StringUtilsApiV1 mockedStringUtilsApiV1 = Mock(StringUtilsApiV1)
    mockedApi.getStringUtils() >> mockedStringUtilsApiV1
    mockedStringUtilsApiV1.escapeHtml(_) >> { args -> return ((String) args[0])
            .replace('<', "&lt;")
            .replace('>', "&gt;")
            .replace('"', '&quot;')
            .replace('€', "&euro;") }
  }

  def "dumping a simple input to a csv with metadata"() {
    given:
    FormContentV1 mockedFormContent = Mock(FormContentV1, constructorArgs: ["mockedFormId"]) as FormContentV1
    mockedFormContent.formId >> "6000357:testform:v1.0"
    FormFieldContentV1 mockedFieldContent = Mock()
    mockedFieldContent.value >> "Example input of a user"
    mockedFormContent.fields >> ["exampleGroup:0:exampleField": mockedFieldContent]

    byte[] pdfContent = getClass().getResourceAsStream("resources/dummy.pdf").readAllBytes()
    String pdfContentBase64 = pdfContent.encodeBase64().toString()

    FormDumper dumper = new FormDumper(mockedFormContent, mockedApi)

    when:
    String csv = dumper.dumpFormAsCsv(true)

    then:
    csv.contains("postfachHandleId,\"ab0b63be-ee10-4740-b5e7-66aa81834510\"\r\n")
    csv.contains("formId,\"6000357:testform:v1.0\"\r\n")
    csv.contains("pdfApplicantFormBase64,\"" + pdfContentBase64 + "\"\r\n")
    csv.contains("exampleGroup:0:exampleField,\"Example input of a user\"\r\n")
  }

  def "dumping a simple input to a csv"() {
    given:
    FormContentV1 mockedFormContent = Mock(FormContentV1, constructorArgs: ["mockedFormId"]) as FormContentV1
    FormFieldContentV1 mockedFieldContent = Mock()
    mockedFieldContent.value >> "Example input of a user"
    mockedFormContent.fields >> ["exampleGroup:0:exampleField": mockedFieldContent]

    FormDumper dumper = new FormDumper(mockedFormContent, mockedApi)

    when:
    String csv = dumper.dumpFormAsCsv()

    then:
    csv == "exampleGroup:0:exampleField,\"Example input of a user\"\r\n"
  }

  def "dumping input that needs escaping to a csv"() {
    given:
    FormContentV1 mockedFormContent = Mock(FormContentV1, constructorArgs: ["mockedFormId"]) as FormContentV1
    FormFieldContentV1 mockedFieldContent = Mock()
    mockedFieldContent.value >> "Input with a \"quote\", a comma and nothing else."
    mockedFormContent.fields >> ["exampleGroup:0:exampleField": mockedFieldContent]

    FormDumper dumper = new FormDumper(mockedFormContent, mockedApi)

    when:
    String csv = dumper.dumpFormAsCsv()

    then:
    csv == "exampleGroup:0:exampleField,\"Input with a \"\"quote\"\", a comma and nothing else.\"\r\n"
  }

  def "Escaping unsecure content"() {
    // As reported in https://tracker.seitenbau.net/browse/SKDE-1303

    given:
    String evil = 'evil\\",neue spalte'

    when:
    //noinspection GroovyAccessibility - just a unit test (for a private method. But that's OK)
    String escaped = FormDumper.escapeForCsv(evil)

    then:
    escaped == '"evil\\"",neue spalte"'
  }

  def "dumping input to a csv with the class name"() {
    given:
    FormContentV1 mockedFormContent = Mock(FormContentV1, constructorArgs: ["mockedFormId"]) as FormContentV1
    FormFieldContentV1 mockedStringField = Mock()
    FormFieldContentV1 mockedDateField = Mock()

    mockedStringField.value >> "Input with a \"quote\", a comma and nothing else."
    mockedDateField.value >> new SimpleDateFormat("yyyy-MM-dd").parse("2019-01-01")
    mockedFormContent.fields >> [
            "exampleGroup:0:stringField": mockedStringField,
            "exampleGroup:0:dateField"  : mockedDateField
    ]

    FormDumper dumper = new FormDumper(mockedFormContent, mockedApi)

    when:
    String csv = dumper.dumpFormAsCsvWithDatatype()
    String firstLine = csv.split("\r\n")[0]
    String secondLine = csv.split("\r\n")[1]

    then:
    firstLine == /exampleGroup:0:stringField,String,"Input with a ""quote"", a comma and nothing else."/
    secondLine.matches("exampleGroup:0:dateField,Date,\"Tue Jan 01 00:00:00 (.*) 2019\"") // don't care about the time zone
  }

  def "dumping a form to XML"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String xml = dumper.dumpAsXml()
    def parsed = new XmlSlurper().parseText(xml)
    def parsedGroupInstance = parsed."serviceportal-fields".mainGroupId.instance_0

    then:
    parsedGroupInstance.textfield == "Textfield content with <html>HTML</html>"
    parsedGroupInstance.textarea == "Textarea\ncontent"

    // File Upload
    parsedGroupInstance.fileupload.filename == "dummy.pdf"
    parsedGroupInstance.fileupload.mimetype == "application/pdf"
    parsedGroupInstance.fileupload.base64Data == "PDF content".getBytes("UTF-8").encodeBase64()

    parsedGroupInstance.yesno == true
    parsedGroupInstance.simpleCheckbox == true

    // Checkbox list
    parsedGroupInstance.checkboxList.selectedValue[0] == "firstOption"
    parsedGroupInstance.checkboxList.selectedValue[1] == "secondOption"

    parsedGroupInstance.radioButtons == "firstOption"
    parsedGroupInstance.selectOptions == "secondOption"

    // Multiselect
    parsedGroupInstance.multiselect.selectedValue[0] == "firstOption"
    parsedGroupInstance.multiselect.selectedValue[1] == "secondOption"

    // Trust level
    parsedGroupInstance.name == "Testname"

    parsedGroupInstance.date == "2015-08-09T00:00:00.000+02:00"
    parsedGroupInstance.time == "1970-01-01T10:44:00.000+01:00"
    parsedGroupInstance.money == "5.66"
    parsedGroupInstance.npa == false
  }

  def "check if the form structure is the same with and without metadata"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String xmlWithMetadata = dumper.dumpAsXml(true)
    String xmlWithoutMetadata = dumper.dumpAsXml(false)
    def parsedWithMetadata = new XmlSlurper().parseText(xmlWithMetadata)
    def parsedWithoutMetadata = new XmlSlurper().parseText(xmlWithoutMetadata)
    then:
    parsedWithMetadata."serviceportal-fields" == parsedWithoutMetadata."serviceportal-fields"
  }

  def "dumping a form to XML with metadata"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    byte[] pdfContent = getClass().getResourceAsStream("resources/dummy.pdf").readAllBytes()
    String pdfContentBase64 = pdfContent.encodeBase64().toString()

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String xml = dumper.dumpAsXml(true)
    def parsed = new XmlSlurper().parseText(xml)
    def parsedMetadata = parsed.metadata

    then:
    parsedMetadata.postfachHandleId == "ab0b63be-ee10-4740-b5e7-66aa81834510"
    parsedMetadata.formId == "6000357:testform:v1.0"
    parsedMetadata.pdfApplicantFormBase64 == pdfContentBase64
  }



  def "dumping a form with an illegally named placeholder field to XML"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_withIllegalXmlName.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)
    ScriptingApiV1 mockedApi = Mock(ScriptingApiV1)
    FormV1 form = createEmptyForm()
    mockedApi.getForm("6000357:testform:v1.0") >> form
    addFieldToInstance(form.getGroupInstance(MAIN_GROUP_ID, 0), "123illegalNameForXmlNode", FieldTypeV1.STRING, null)

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    dumper.dumpAsXml()

    then:
    AssertionError e = thrown(AssertionError)
    e.message == "Failed to create XML file. Field name '123illegalNameForXmlNode' is not a valid name for a XML node. Please change the field name.. Expression: fieldKey.matches(^[a-zA-Z_][\\w.-]*\$)"
  }

  def "dumping a form to HTML Table"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String html = dumper.dumpFormAsHtmlTable()

    then:
    def expectedHtml = new File('test/resources/expected.html').text.replaceAll("\n *<", "<")
    html == expectedHtml
  }

  def "dumping a form to HTML Table vith VerifiedFormFieldValueV1 values"() {
    given:
    FormContentV1 formContent = new FormContentV1("6000357:testform:v1.0")
    formContent.fields.put(
        MAIN_GROUP_ID + ":0:textfield",
        FormFieldContentV1.builder().value(new VerifiedFormFieldValueV1("textfieldContent", "DummyVerificationToken")).build())
    formContent.fields.put(
            MAIN_GROUP_ID + ":0:textarea",
            FormFieldContentV1.builder().value(new VerifiedFormFieldValueV1("textareaContent", "DummyVerificationToken")).build())
    formContent.fields.put(
        MAIN_GROUP_ID + ":0:date",
        FormFieldContentV1.builder().value(new VerifiedFormFieldValueV1(new GregorianCalendar(2015, Calendar.JULY, 8).time, "DummyVerificationToken")).build())

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String html = dumper.dumpFormAsHtmlTable()

    then:
    def expectedHtml = new File('test/resources/expected_verifiedFormFieldValue.html').text.replaceAll("\n *<", "<")
    html == expectedHtml
  }

  def "dumping a form to HTML Table vith VerifiedFormFieldValueV1 null values"() {
    given:
    FormContentV1 formContent = new FormContentV1("6000357:testform:v1.0")
    formContent.fields.put(
            MAIN_GROUP_ID + ":0:textfield",
            FormFieldContentV1.builder().value(new VerifiedFormFieldValueV1(null, "DummyVerificationToken")).build())
    formContent.fields.put(
            MAIN_GROUP_ID + ":0:textarea",
            FormFieldContentV1.builder().value(new VerifiedFormFieldValueV1(null, "DummyVerificationToken")).build())
    formContent.fields.put(
            MAIN_GROUP_ID + ":0:date",
            FormFieldContentV1.builder().value(new VerifiedFormFieldValueV1(null, "DummyVerificationToken")).build())

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String html = dumper.dumpFormAsHtmlTable()

    then:
    def expectedHtml = new File('test/resources/expected_verifiedFormFieldValue_nullValues.html').text.replaceAll("\n *<", "<")
    html == expectedHtml
  }

  def "dumping a form to Text"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String text = dumper.dumpFormAsText(true)

    then:
    def expectedText = new File('test/resources/expected.txt').text
    text == expectedText
  }
}


