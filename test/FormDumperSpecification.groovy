import commons.serviceportal.forms.JsonToFormContentConverter
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormFieldContentV1
import spock.lang.Specification

import java.text.SimpleDateFormat

import commons.serviceportal.forms.FormDumper

class FormDumperSpecification extends Specification {
  private ScriptingApiV1 mockedApi = Mock()

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
    parsed.mainGroupId.instance_0.textfield == "TEXTFIELD"
    parsed.mainGroupId.instance_0.textarea == "dsafsafa"

    // File Upload
    parsed.mainGroupId.instance_0.fileupload.filename == "dummy.pdf"
    parsed.mainGroupId.instance_0.fileupload.mimetype == "application/pdf"
    (parsed.mainGroupId.instance_0.fileupload.base64Data as String).startsWith("JVBERi0xLjQKJcOkw7zD")

    parsed.mainGroupId.instance_0.yesno == true
    parsed.mainGroupId.instance_0.simpleCheckbox == true

    // Checkbox list
    parsed.mainGroupId.instance_0.checkboxList.selectedValue[0] == "VAL1"
    parsed.mainGroupId.instance_0.checkboxList.selectedValue[1] == "VAL2"

    parsed.mainGroupId.instance_0.radioButtons == "VAL1"
    parsed.mainGroupId.instance_0.selectOptions == "VAL2"

    // Multiselect
    parsed.mainGroupId.instance_0.mutliselect.selectedValue[0] == "VAL1"
    parsed.mainGroupId.instance_0.mutliselect.selectedValue[1] == "VAL2"

    // "Schubser"
    parsed.mainGroupId.instance_0.schubser.selectedValue[0] == "VAL1"
    parsed.mainGroupId.instance_0.schubser.selectedValue[1] == "VAL2"

    parsed.mainGroupId.instance_0.date == "2015-07-08T00:00:00.000+02:00"
    parsed.mainGroupId.instance_0.time == "1970-01-01T11:55:00.000+01:00"
    parsed.mainGroupId.instance_0.money == "5.66"
    parsed.mainGroupId.instance_0.npa == false
  }

}


