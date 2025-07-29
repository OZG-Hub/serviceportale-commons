import commons.serviceportal.forms.JsonToFormContentConverter
import commons.serviceportal.forms.formdumper.CsvDumper
import commons.serviceportal.forms.formdumper.HtmlDumper
import commons.serviceportal.forms.formdumper.JsonDumper
import commons.serviceportal.forms.formdumper.TextDumper
import commons.serviceportal.forms.formdumper.XmlDumper
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

class FormDumperSpecification extends Specification {

  public static final String MAIN_GROUP_ID = "mainGroupId"
  private static ScriptingApiV1 mockedApi

  void addFieldToInstance(FieldGroupInstanceV1 groupInstance, String fieldId, FieldTypeV1 type, String label) {
    FormFieldV1 field = new FormFieldV1(fieldId, type)
    field.setLabel(label)
    FormRowV1 row = FormRowV1.builder().fields([field]).build()
    groupInstance.getRows().add(row)
  }

  FormV1 createEmptyForm() {
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

    mockedApi.getVariable("processEngineConfig", Map) >> ["serviceportal.environment.main-portal-host": "dev.service-bw.de"]

    mockedApi.getForm("6000357:testform:v1.0") >> form

    // Mock api for metadata
    StartedByUserV1 startedByUser = new StartedByUserV1("1", "user", "user", "user", "{\"@type\":\"nkb\",\"id\":\"ab0b63be-ee10-4740-b5e7-66aa81834510\"}")
    mockedApi.getVariable("startedByUser", StartedByUserV1) >> startedByUser

    // Mock escapeHtml function
    StringUtilsApiV1 mockedStringUtils = Mock(StringUtilsApiV1)
    mockedApi.stringUtils >> mockedStringUtils
    mockedStringUtils.escapeHtml(_) >> { args ->
      return ((String) args[0])
              .replace('<', "&lt;")
              .replace('>', "&gt;")
              .replace('"', '&quot;')
              .replace('€', "&euro;")
    }

    byte[] pdfContent = getClass().getResourceAsStream("resources/dummy.pdf").readAllBytes()
    BinaryContentV1 mockedPdf = new BinaryContentV1("key", "dummy.pdf", "label", "application/pdf", pdfContent)
    mockedApi.getVariable("applicantFormAsPdf", BinaryContentV1) >> mockedPdf
  }

  def "dumping a simple input to a csv with metadata"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    CsvDumper dumper = new CsvDumper(formContent, mockedApi, true)

    when:
    String csv = dumper.dump()

    then:
    csv.contains('postfachHandleId,"ab0b63be-ee10-4740-b5e7-66aa81834510"\r\n')
    csv.contains('formId,"6000357:testform:v1.0"\r\n')
    csv.contains('mainGroupId:0:textfield,"Textfield content"\r\n')
  }

  def "dumping a simple input to a csv"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)
    String expected = '''\
mainGroupId:0:time,"10:44"
mainGroupId:0:yesno,"true"
mainGroupId:0:npa,"false"
mainGroupId:0:textfield,"Textfield content"
mainGroupId:0:simpleCheckbox,"true"
mainGroupId:0:radioButtons,"firstOption"
mainGroupId:0:textarea,"Textarea
content"
mainGroupId:0:multiselect,"[firstOption, secondOption]"
mainGroupId:0:checkboxList,"[firstOption, secondOption]"
mainGroupId:0:fileupload,"UERGIGNvbnRlbnQ="
mainGroupId:0:date,"2015-08-09"
mainGroupId:0:selectOptions,"secondOption"
mainGroupId:0:money,"5.66"
'''.replace("\n", "\r\n")


    when:
    CsvDumper dumper = new CsvDumper(formContent, mockedApi, false)
    String csv = dumper.dump()

    then:
    csv == expected
  }

  def "Escaping unsecure content for CSV export"() {
    // As reported in https://tracker.seitenbau.net/browse/SKDE-1303

    given:
    String evil = 'evil\\",neue spalte'

    when:
    //noinspection GroovyAccessibility - just a unit test (for a private method. But that's OK)
    String escaped = CsvDumper.escapeForCsv(evil)

    then:
    escaped == '"evil\\"",neue spalte"'
  }

  def "dumping a form to XML"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    XmlDumper dumper = new XmlDumper(formContent, mockedApi)
    String xml = dumper.dump()
    def parsed = new XmlSlurper().parseText(xml)
    def parsedGroupInstance = parsed."serviceportal-fields".mainGroupId.instance_0

    then:
    parsedGroupInstance.textfield == "Textfield content"
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
    String xmlNoMetadata = new XmlDumper(formContent, mockedApi, true).dump()
    String xmlWithMetadata = new XmlDumper(formContent, mockedApi, false).dump()
    def parsedWithMetadata = new XmlSlurper().parseText(xmlWithMetadata)
    def parsedNoMetadata = new XmlSlurper().parseText(xmlNoMetadata)

    then:
    parsedWithMetadata."serviceportal-fields" == parsedNoMetadata."serviceportal-fields"
  }

  def "dumping a form to XML with metadata"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    byte[] pdfContent = getClass().getResourceAsStream("resources/dummy.pdf").readAllBytes()
    String pdfContentBase64 = pdfContent.encodeBase64().toString()

    when:
    XmlDumper dumper = new XmlDumper(formContent, mockedApi, true)
    String xml = dumper.dump()
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
    XmlDumper dumper = new XmlDumper(formContent, mockedApi)
    dumper.dump()

    then:
    AssertionError e = thrown(AssertionError)
    e.message.contains("Failed to create XML file. Field name '123illegalNameForXmlNode' is not a valid name for a XML node. Please change the field name.")
  }

  def "dumping a form to HTML Table"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    HtmlDumper dumper = new HtmlDumper(formContent, mockedApi, false)
    String html = dumper.dump()

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
    HtmlDumper dumper = new HtmlDumper(formContent, mockedApi, false)
    String html = dumper.dump()

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
    HtmlDumper dumper = new HtmlDumper(formContent, mockedApi, false)
    String html = dumper.dump()

    then:
    def expectedHtml = new File('test/resources/expected_verifiedFormFieldValue_nullValues.html').text.replaceAll("\n *<", "<")
    html == expectedHtml
  }

  def "dumping a form to raw text, with headings and without HTML-escaping"() {
    given:
    String json = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    TextDumper dumper = new TextDumper(formContent, mockedApi, false, true, false)
    String output = dumper.dump()

    then:
    output == """\
Main Group (mainGroupId):
  Time >>> 10:44 <<<
  Yes/No >>> Ja <<<
  NPA >>> Sie waren NICHT mit dem neuem Personalausweis angemeldet <<<
  Textfield >>> Textfield content <<<
  Sinple Checkbox >>> Ja <<<
  Radio Buttons >>> first label <<<
  Textarea >>> Textarea
content <<<
  Multiselect >>> first label, second label <<<
  Checkbox List >>> first label, second label <<<
  Fileupload >>> Datei: "dummy.pdf" <<<
  Date >>> 09.08.2015 <<<
  SelectOptions >>> second label <<<
  Eurobetrag >>> 5.66 € <<<
"""
  }

  def "dumping a form to JSON"() {
    given:
    String allFieldsFileContent = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(allFieldsFileContent)

    when:
    JsonDumper dumper = new JsonDumper(formContent, mockedApi, false, true)
    String json = dumper.dump()

    then:
    json.contains('"mainGroupId_0_yesno": true')
    json.contains('"mainGroupId_0_textfield": "Textfield content"')
    json.contains('"mainGroupId_0_fileupload": "dummy.pdf"')
    json.contains('"mainGroupId_0_date": "2015-08-09"')
  }

  def "dumping a form to JSON with metadata and complex file objects"() {
    given:
    String allFieldsFileContent = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(allFieldsFileContent)

    when:
    JsonDumper dumper = new JsonDumper(formContent, mockedApi, true, false)
    String json = dumper.dump()

    then:
    json.contains('"formId": "6000357:testform:v1.0"')
    json.contains('''\
        "mainGroupId_0_fileupload": {
            "filename": "dummy.pdf",
            "fileAsBase64": "UERGIGNvbnRlbnQ="
        }''')
  }

  def "hiding a specific field via the configureAdditionalHidingLogic logic"() {
    given:
    String allFieldsFileContent = getClass().getResourceAsStream("resources/formContent_allFields.json").text
    FormContentV1 formContent = JsonToFormContentConverter.convert(allFieldsFileContent)
    String fieldToHide = "textfield"
    String expectedDifference = "Textfield >>> Textfield content <<<"

    /**
     * Hides field with the id of $fieldToHide
     */
    Closure<Boolean> additionalHidingLogic = {
      //noinspection GroovyTrivialIf - better readability
      if (it.id == fieldToHide) {
        return true
      } else {
        return false
      }
    }

    when:
    TextDumper dumperWithLogic = new TextDumper(formContent, mockedApi, false)
    dumperWithLogic.configureAdditionalHidingLogic  additionalHidingLogic
    TextDumper dumperWithoutLogic = new TextDumper(formContent, mockedApi, false)

    then:
    !dumperWithLogic.dump().contains(expectedDifference)
    dumperWithoutLogic.dump().contains(expectedDifference)
  }
}


