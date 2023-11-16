package commons.serviceportal.validators

import spock.lang.Specification
import java.text.SimpleDateFormat

class DateInFutureValidatorSpecification extends Specification {
  def "Verifying a date in the future"() {
    given:
    Date aDateInTheFuture = new SimpleDateFormat("yyyy-mm-dd").parse("9999-12-31") // If this values stops being in the future, it is no longer my problem ;)


    expect:
    //noinspection GroovyPointlessBoolean - easier to read this way
    DateInFutureValidator.validate(aDateInTheFuture) == true
  }

  def "Verifying a date in the past"() {
    given:
    Date aDateInThePast = new SimpleDateFormat("yyyy-mm-dd").parse("1993-01-02")

    expect:
    //noinspection GroovyPointlessBoolean - easier to read this way
    DateInFutureValidator.validate(aDateInThePast) == false
  }

  def "Verifying the current day"() {
    given: "Today's date"
    Date today = DateInFutureValidator.clearTimeFromDate(new Date())

    expect: "Today's date to be accepted"
    //noinspection GroovyPointlessBoolean - easier to read this way
    DateInFutureValidator.validate(today) == true
  }


}
