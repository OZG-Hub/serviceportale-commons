package validators

import spock.lang.*
import commons.serviceportal.validators.IbanValidator

class IbanValidatorSpecification extends Specification {

  def "validating valid IBANs"() {
    given:
    /*
    Extracted from https://en.wikipedia.org/w/index.php?title=International_Bank_Account_Number&oldid=944677249

    Creative Commons Attribution-ShareAlike 3.0 Unported License
    https://en.wikipedia.org/w/index.php?title=Wikipedia:Text_of_Creative_Commons_Attribution-ShareAlike_3.0_Unported_License&oldid=880226358
    */
    final List<String> validIbans = [
            "BE71 0961 2345 6769",
            "BE71096123456769",
            "FR76 3000 6000 0112 3456 7890 189",
            "DE91 1000 0000 0123 4567 89",
            "GR96 0810 0010 0000 0123 4567 890",
            "RO09 BCYP 0000 0012 3456 7890",
            "SA44 2000 0001 2345 6789 1234",
            "ES79 2100 0813 6101 2345 6789",
            "CH56 0483 5012 3456 7800 9",
            "GB98 MIDL 0700 9312 3456 78",
    ]

    expect:
    validIbans.each { iban ->
      //noinspection GroovyPointlessBoolean - improves readability
      assert IbanValidator.validateIban(iban) == true
    }
  }

  def "validating an invalid IBAN"() {
    given:
    final List<String> invalidIbans = [
            "TEST",
            "",
            "u",
            "GB82 TEST 1234 5698 7654 32",
            "GB82TEST12345698765432",
            "DE91 1000 0000 0123 4567 88", // last digit wrong
            "DE91 0000 0000 0123 4567 89", // first digit wrong
            "DE91 1000 0000 0123 4567 98", // last digits switched
    ]

    expect:
    invalidIbans.each { iban ->
      //noinspection GroovyPointlessBoolean - improves readability
      assert IbanValidator.validateIban(iban) == false
    }
  }
}
