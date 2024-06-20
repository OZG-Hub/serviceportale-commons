import commons.serviceportal.forms.JsonToFormContentConverter
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FieldTypeV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldKeyV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormFieldV1
import de.seitenbau.serviceportal.scripting.api.v1.form.FormV1
import de.seitenbau.serviceportal.scripting.api.v1.form.PossibleValueListV1
import de.seitenbau.serviceportal.scripting.api.v1.form.PossibleValueV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormFieldContentV1
import spock.lang.Specification

import java.text.SimpleDateFormat

import commons.serviceportal.forms.FormDumper

class FormDumperSpecification extends Specification {
  static private ScriptingApiV1 mockedApi

  def setupSpec() {
    // Mock scripting API
    mockedApi = Mock(ScriptingApiV1)

    // Mock form
    def uncastMockedForm = Mock(FormV1, constructorArgs: ["formIdDoesNotMatter"])
    assert uncastMockedForm instanceof FormV1
    FormV1 mockedForm = uncastMockedForm
    mockedApi.getForm(_ as String) >> mockedForm

    // Mock form fields
    mockedForm.getFieldInInstance(_ as FormFieldKeyV1) >> { arguments ->
      FormFieldKeyV1 fieldKey = arguments.first()
      String fullKey = fieldKey.toString()

      PossibleValueListV1 pvList = new PossibleValueListV1()
      pvList.add(new PossibleValueV1("first label", "firstOption", null))
      pvList.add(new PossibleValueV1("second label", "secondOption", null))

      BinaryContentV1 mockedBinaryContent = Mock(BinaryContentV1)
      mockedBinaryContent.data >> "test content".getBytes("UTF-8")
      mockedBinaryContent.mimetype >> "text/plain"
      mockedBinaryContent.uploadedFilename >> "test.txt"

      FormFieldV1 field
      switch (fieldKey.fieldId) {
        case "textanzeige":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.TEXT]) as FormFieldV1
          field.value >> null
          break
        case "time":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.TIME]) as FormFieldV1
          field.value >> new GregorianCalendar(1970, 0, 1, 11, 55, 00).time
          break
        case "yesno":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.BOOLEAN]) as FormFieldV1
          field.value >> true
          break
        case "npa":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.SUBMITTED_WITH_NPA_INFO]) as FormFieldV1
          field.value >> false
          break
        case "textfield":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.STRING]) as FormFieldV1
          field.value >> "Example input"
          break
        case "simpleCheckbox":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.SINGLE_CHECKBOX]) as FormFieldV1
          field.value >> true
          break
        case "radioButtons":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.RADIO_BUTTONS]) as FormFieldV1
          field.possibleValues >> pvList
          field.value >> "firstOption"
          break
        case "textarea":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.TEXTAREA]) as FormFieldV1
          field.value >> "Example\ninput"
          break
        case "mutliselect":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.DROPDOWN_MULTIPLE_SELECT]) as FormFieldV1
          field.possibleValues >> pvList
          field.value >> ["firstOption"]
          break
        case "checkboxList":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.FILE]) as FormFieldV1
          field.possibleValues >> pvList
          field.value >> ["firstOption"]
          break
        case "schubser":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.TWO_LIST_SELECT]) as FormFieldV1
          field.possibleValues >> pvList
          field.value >> ["firstOption"]
          break
        case "fileupload":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.FILE]) as FormFieldV1
          field.value >> mockedBinaryContent
          break
        case "h2":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.H2]) as FormFieldV1
          break
        case "h1":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.H1]) as FormFieldV1
          break
        case "date":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.TEXTAREA]) as FormFieldV1
          field.value >> new GregorianCalendar(2015, Calendar.JULY, 8).time
          break
        case "ca4618b9":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.PLACEHOLDER]) as FormFieldV1
          break
        case "selectOptions":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.DROPDOWN_SINGLE_SELECT]) as FormFieldV1
          field.possibleValues >> pvList
          field.value >> "firstOption"
          break
        case "money":
          field = Mock(FormFieldV1, constructorArgs: [fullKey, FieldTypeV1.EURO_BETRAG]) as FormFieldV1
          field.value >> new BigDecimal("12.34")
          break
        default:
          throw new UnsupportedOperationException("Mocked field for key '$fieldKey' not implemented yet. Please " +
                  "update FormDumperSpecification.")
      }
      return field

      // TODO: Mock different FormFieldV1's, depending on field key
    }
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

  def "Escaping unsecure content"(){
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
    final String FILENAME = "resources/formContent_allFields.json"
    String json = getClass().getResourceAsStream(FILENAME).text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String xml = dumper.dumpAsXml()
    def parsed = new XmlSlurper().parseText(xml)

    then:
    parsed.mainGroupId.instance_0.textfield == "Example input"
    parsed.mainGroupId.instance_0.textarea == "Example\ninput"

    // File Upload
    parsed.mainGroupId.instance_0.fileupload.filename == "test.txt"
    parsed.mainGroupId.instance_0.fileupload.mimetype == "text/plain"
    parsed.mainGroupId.instance_0.fileupload.base64Data == "test content".getBytes("UTF-8").encodeBase64()

    parsed.mainGroupId.instance_0.yesno == true
    parsed.mainGroupId.instance_0.simpleCheckbox == true

    // Checkbox list
    parsed.mainGroupId.instance_0.checkboxList.selectedValue[0] == "firstOption"

    parsed.mainGroupId.instance_0.radioButtons == "firstOption"
    parsed.mainGroupId.instance_0.selectOptions == "firstOption"

    // Multiselect
    parsed.mainGroupId.instance_0.mutliselect.selectedValue[0] == "firstOption"

    // "Schubser"
    parsed.mainGroupId.instance_0.schubser.selectedValue[0] == "firstOption"

    parsed.mainGroupId.instance_0.date == "2015-07-08T00:00:00.000+02:00"
    parsed.mainGroupId.instance_0.time == "1970-01-01T11:55:00.000+01:00"
    parsed.mainGroupId.instance_0.money == "12.34"
    parsed.mainGroupId.instance_0.npa == false
  }

  def "dumping a form with an illegally named placeholder field to XML"() {
    given:
    final String FILENAME = "resources/formContent_withPlaceholder.json"
    String json = getClass().getResourceAsStream(FILENAME).text
    FormContentV1 formContent = JsonToFormContentConverter.convert(json)

    when:
    FormDumper dumper = new FormDumper(formContent, mockedApi)
    String xml = dumper.dumpAsXml()

    then:
    noExceptionThrown()

    and:
    xml == """\
      <serviceportal-fields>
        <exampleGroup>
          <instance_0>
            <exampleField>hi!</exampleField>
          </instance_0>
        </exampleGroup>
      </serviceportal-fields>""".stripIndent()
  }
}


