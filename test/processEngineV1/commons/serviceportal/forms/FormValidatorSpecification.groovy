package commons.serviceportal.forms

import spock.lang.Specification

class FormValidatorSpecification extends Specification {
  def "Validate correct visibility conditions in form"() {
    given:
    String json = getClass().getResourceAsStream("/resources/testformCorrectDisplayConditions.json").text

    when:
    FormValidator fv = new FormValidator(json)
    fv.validate()

    then:
    noExceptionThrown()
  }

  def "Validate wrong visibility conditions in form"() {
    given:
    String json = getClass().getResourceAsStream("/resources/testformWrongDisplayConditions.json").text

    when:
    FormValidator fv = new FormValidator(json)
    fv.validate()

    then:
    thrown(FormValidationException)
  }
}
