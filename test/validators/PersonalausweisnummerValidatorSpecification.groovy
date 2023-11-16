package validators

import commons.serviceportal.validators.PersonalausweisnummerValidator
import spock.lang.Specification

class PersonalausweisnummerValidatorSpecification extends Specification {

  def "validating a valid id number"() {
    given:
    String valid = "T220001293"

    when:
    def result = PersonalausweisnummerValidator.validate(valid)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.VALID
  }

  def "validating another valid id number"() {
    given:
    String valid = "L9F6MKYMJ0"

    when:
    def result = PersonalausweisnummerValidator.validate(valid)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.VALID
  }

  def "validating an id number with an invalid character"() {
    given:
    String containsNonAlphanumericChars = "A2666666(6"

    when:
    def result = PersonalausweisnummerValidator.validate(containsNonAlphanumericChars)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.INVALID_CHARS
  }

  def "validating an id number with an invalid characters only"() {
    given:
    String containsNonAlphanumericChars = "!(§&X{µ@]["

    when:
    def result = PersonalausweisnummerValidator.validate(containsNonAlphanumericChars)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.INVALID_CHARS
  }

  def "validating a too short id number"() {
    given:
    String tooShortNumber = "A220001"

    when:
    def result = PersonalausweisnummerValidator.validate(tooShortNumber)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.TOO_SHORT
  }

  def "validating another too short id number"() {
    given:
    String tooShortNumber = "L9F6MKYMJ"

    when:
    def result = PersonalausweisnummerValidator.validate(tooShortNumber)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.TOO_SHORT
  }

  def "validating a too long id number"() {
    given:
    String tooLongNumber = "T2200012931"

    when:
    def result = PersonalausweisnummerValidator.validate(tooLongNumber)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.TOO_LONG
  }

  def "validating another too long id number"() {
    given:
    String tooLongNumber = "T22200012931"

    when:
    def result = PersonalausweisnummerValidator.validate(tooLongNumber)

    then:
    result == PersonalausweisnummerValidator.ValidationStatus.TOO_LONG
  }
}
