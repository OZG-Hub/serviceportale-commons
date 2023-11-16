package validators

import groovy.time.TimeCategory
import spock.lang.Specification
import commons.serviceportal.validators.MinimumAgeValidator

import java.text.SimpleDateFormat

class MinimumAgeValidatorSpecification extends Specification {
  def "a old person should validate"() {
    given:
    MinimumAgeValidator minimumAgeValidator = new MinimumAgeValidator(18)
    Date birthdayOfAOldPerson = new SimpleDateFormat("yyy-MM-dd").parse("1990-01-25")

    expect:
    minimumAgeValidator.isAtLeastThatOld(birthdayOfAOldPerson) == true
  }

  def "a young person should NOT validate"() {
    given:
    MinimumAgeValidator minimumAgeValidator = new MinimumAgeValidator(18)
    Date birthdayOfA17YearOld
    use(TimeCategory) {
      birthdayOfA17YearOld = new Date() - 17.years
    }

    expect:
    minimumAgeValidator.isAtLeastThatOld(birthdayOfA17YearOld) == false
  }
}
