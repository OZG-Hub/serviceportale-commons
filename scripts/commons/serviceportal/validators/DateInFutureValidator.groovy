package commons.serviceportal.validators

import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormFieldContentV1

class DateInFutureValidator {
  static public final String ERROR_MESSAGE = "Das Datum muss in der Zukunft liegen. Bitte wählen Sie den heutigen Tag, oder " +
          "einen darauf Folgenden."

  /**
   * Returns true, if the fields value is in the future.
   * Returns false and adds a validation message, if the fields value is in the past.
   *
   * See {@link #validate(Date)} for infos how edge cases (like the current day) are handled.
   *
   * @param fieldToValidate the field to check
   * @return
   */
  static boolean validateAndDisplayErrorMessage(FormFieldContentV1 fieldToValidate) {
    assert fieldToValidate != null: "Failed to perform DateInFutureValidator - field is null."
    assert fieldToValidate.value != null: "Failed to perform DateInFutureValidator - field's value is null."
    assert fieldToValidate.value.class == Date: "Failed to perform DateInFutureValidator - field's value is not a date."

    if (validate(fieldToValidate.value as Date)) {
      // All good. Field is valid.
      return true
    } else {
      fieldToValidate.validationMessages.add(ERROR_MESSAGE)
      return false
    }
  }

  /**
   * Returns true if the date is in the future. This function ignores time (i.e. "2023-08-08 11:00" is interpreted the
   * same as "2023-08-08 00:00".
   *
   * The current date is also considered to be in the future.
   *
   * @param date the date to check
   * @return
   */
  static boolean validate(Date date) {
    assert date != null: "Failed to perform DateInFutureValidator - date is null."

    date = clearTimeFromDate(date) // Serviceportal Dates should already be cleared of the time component. But we do
    // this again just to make sure.

    Date now = clearTimeFromDate(new Date())

    // The current date is considered to be in the future (as most applicants on the same day should still be allowed
    // to be sent)
    if (date == now) {
      return true
    }

    return date.after(now)
  }

  /**
   * Removes the time portions of a Date object
   *
   * @param date
   * @return
   */
  static Date clearTimeFromDate(Date date) {
    // groovy should provide the clearTime() method, but for some reason it doesn't seem to exist in the groovy version
    // that is used by the Serviceportal. So we just re-implement it.

    // Clear the time components
    Calendar calendar = Calendar.getInstance()
    calendar.setTime(date)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // Convert the Calendar back to a Date
    return calendar.time
  }
}
