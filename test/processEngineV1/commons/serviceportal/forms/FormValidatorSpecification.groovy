package commons.serviceportal.forms

import spock.lang.Specification

class FormValidatorSpecification extends Specification {
  def "Validate form with correct visibility conditions in groups"() {
    given:
    String json = getClass().getResourceAsStream("/resources/testformCorrectDisplayConditions.json").text

    when:
    FormValidator fv = new FormValidator(json)
    fv.validate(true)

    then:
    noExceptionThrown()
  }

  def "Validate form with wrong visibility conditions in groups"() {
    given:
    String json = getClass().getResourceAsStream("/resources/testformWrongDisplayConditions.json").text

    when:
    FormValidator fv = new FormValidator(json)
    fv.validate(true)

    then:
    thrown(FormValidationException)
  }

  def "Validate form with wrong visibility conditions in groups (no group checks)"() {
    given:
    String json = getClass().getResourceAsStream("/resources/testformWrongDisplayConditions.json").text

    when:
    FormValidator fv = new FormValidator(json)
    fv.validate()

    then:
    noExceptionThrown()
  }
}
