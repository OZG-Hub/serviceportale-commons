package commons.serviceportal.validators

class PersonalausweisnummerValidator {

/**
 *  Possible validation statuses
 */
  static enum ValidationStatus {
    VALID, TOO_SHORT, TOO_LONG, INVALID_CHARS, INVALID_CHECK_DIGIT
  }

  /**
   * Checks if an ID number is valid.
   *
   * @param the ID number to check.
   * @return enum constant as validation status.
   */
  static ValidationStatus validate(String idNumber) {

    // check for correct lengths
    if (idNumber.length() < 10) {
      return ValidationStatus.TOO_SHORT
    } else if (idNumber.length() > 10) {
      return ValidationStatus.TOO_LONG
    }

    // check for invalid chars allow only following alphanumeric chars
    // 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, C, F, G, H, J, K, L, M, N, P, R, T, V, W, X, Y, Z
    if (!idNumber.matches("[0-9CF-HJ-NPRTV-Z]+")) {
      return ValidationStatus.INVALID_CHARS
    }

    // list integers including check digit, converting letters to numeric values
    List<Integer> numArray = idNumber.toCharArray().collect {
      if (it.isDigit()) {
        return Integer.parseInt(it as String)
      } else if (it.isLetter()) {
        return Character.getNumericValue(it)
      }
      return it
    }

    // calculate weighted modulus sum
    def weights = [7, 3, 1, 7, 3, 1, 7, 3, 1]
    int sum = 0
    0.upto(8) {
      sum += (numArray[it] * weights[it]) % 10
    }

    // compare check digit (with sum mod 10)
    if ((sum % 10) != numArray[9] as int) {
      return ValidationStatus.INVALID_CHECK_DIGIT
    }

    return ValidationStatus.VALID
  }
}
