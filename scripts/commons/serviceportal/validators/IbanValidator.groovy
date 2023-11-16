package commons.serviceportal.validators

class IbanValidator {

  /**
   * Checks if the supplied IBAN is valid.
   *
   * @param iban the IBAN to check. Spaces are possible (and will be ignored)
   * @return true if the supplied IBAN is valid, false otherwise
   */
  static boolean validateIban(String iban) {
    // Reference see: https://en.wikipedia.org/w/index.php?title=International_Bank_Account_Number&oldid=944677249#Validating_the_IBAN

    /*
    Extracted from https://en.wikipedia.org/w/index.php?title=International_Bank_Account_Number&oldid=944677249

    Creative Commons Attribution-ShareAlike 3.0 Unported License
    https://en.wikipedia.org/w/index.php?title=Wikipedia:Text_of_Creative_Commons_Attribution-ShareAlike_3.0_Unported_License&oldid=880226358
    */
    final Map<String, Integer> countriesToIbanLength =
            [
                    "AL": 28, "AD": 24, "AT": 20, "AZ": 28, "BH": 22, "BY": 28, "BE": 16, "BA": 20, "BR": 29, "BG": 22,
                    "CR": 22, "HR": 21, "CY": 28, "CZ": 24, "DK": 18, "DO": 28, "TL": 23, "EE": 20, "FO": 18, "FI": 18,
                    "FR": 27, "GE": 22, "DE": 22, "GI": 23, "GR": 27, "GL": 18, "GT": 28, "HU": 28, "IS": 26, "IQ": 23,
                    "IE": 22, "IL": 23, "IT": 27, "JO": 30, "KZ": 20, "XK": 20, "KW": 30, "LV": 21, "LB": 28, "LI": 21,
                    "LT": 20, "LU": 20, "MK": 19, "MT": 31, "MR": 27, "MU": 30, "MC": 27, "MD": 24, "ME": 22, "NL": 18,
                    "NO": 15, "PK": 24, "PS": 29, "PL": 28, "PT": 25, "QA": 29, "RO": 24, "LC": 32, "SM": 27, "SA": 24,
                    "RS": 22, "SC": 31, "SK": 24, "SI": 19, "ES": 24, "SE": 24, "CH": 21, "TN": 24, "TR": 26, "UA": 29,
                    "AE": 23, "GB": 22, "VA": 22, "VG": 24
            ]

    // Null Strings are not valid. Exit immediately.
    if (iban == null) {
      return false
    }

    // Ignore spaces
    iban = iban.replace(" ", "")

    // Length check: Is the IBAN in the correct length for the given country code?
    if (iban.length() < 2) {
      return false
    }
    String countryCode = iban.substring(0, 2)
    if (iban.length() != countriesToIbanLength.get(countryCode)) {
      return false
    }

    // Move the four initial characters to the end of the string
    String initialFourCharacters = iban.substring(0, 4)
    iban = iban.substring(4) + initialFourCharacters

    // Convert to integer (where A = 10, B = 11, etc.)
    iban = iban.toUpperCase(Locale.GERMANY)
    String replacedString = ""
    iban.each {
      char currChar = it as char
      if (currChar.isLetter()) {
        int asciiValue = (byte) currChar
        int ibanValue = asciiValue - 55 // ASCII value of 'A' is 65, to get that to 10, we need to subtract 55
        replacedString += ibanValue
      } else {
        replacedString += currChar
      }
    }

    // Parse as integer and calculate remainder
    BigInteger parsedInteger = new BigInteger(replacedString)
    int remainder = parsedInteger % 97
    return remainder == 1
  }

}
