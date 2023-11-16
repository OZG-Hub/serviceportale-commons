package commons.serviceportal.validators

import groovy.time.TimeCategory


class MinimumAgeValidator {
  private int minimumAgeInYears

  MinimumAgeValidator(int minimumAgeInYears) {
    this.minimumAgeInYears = minimumAgeInYears
  }

  /**
   * Returns true if a person born on "birthday" is at least as old
   * as the value specified in the constructor
   * @param birthday
   */
  boolean isAtLeastThatOld(Date birthday) {
    use(TimeCategory) {
      Date now = new Date()
      Date someYearsAgo = now - minimumAgeInYears.year

      return birthday.before(someYearsAgo)
    }
  }
}
