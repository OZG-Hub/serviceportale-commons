package commons.serviceportal

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.start.OrganisationseinheitAnschriftParameterV1
import de.seitenbau.serviceportal.scripting.api.v1.start.OrganisationseinheitKontaktParameterV1
import de.seitenbau.serviceportal.scripting.api.v1.start.OrganisationseinheitParameterV1
import de.seitenbau.serviceportal.scripting.api.v1.start.StartParameterV1
import spock.lang.Specification

class SupportContactDataGetterSpecification extends Specification {
  def "get support contact for a missing org unit"() {
    given:
    ScriptingApiV1 mockedApi = Mock()
    StartParameterV1 mockedStartParameters = Mock()

    mockedApi.getVariable("startParameter", StartParameterV1) >> mockedStartParameters
    mockedStartParameters.organisationseinheit >> null

    when:
    String result = SupportContactDataGetter.getSupportContactOnOzgHub(mockedApi)

    then:
    result.contains("Land Baden-Württemberg")
    result.contains("service-bw@im.bwl.de")
  }

  def "get support contact for a org unit"() {
    given:
    String nameOfOrgUnit = "Musterbehörde"
    String phoneNumber = "+49 1234 5678 900"
    String streetAndHouseNumber = "Musterstraße 123a"
    String zipCode = "12345"
    String city = "Musterhausen"

    ScriptingApiV1 mockedApi = Mock()
    StartParameterV1 mockedStartParameters = Mock()
    OrganisationseinheitParameterV1 mockedOrgUnit = Mock()
    OrganisationseinheitKontaktParameterV1 mockedContact = Mock()
    OrganisationseinheitAnschriftParameterV1 mockedAddress = Mock()

    mockedApi.getVariable("startParameter", StartParameterV1) >> mockedStartParameters
    mockedStartParameters.organisationseinheit >> mockedOrgUnit
    mockedOrgUnit.name >> nameOfOrgUnit
    mockedOrgUnit.erreichbarkeit >> mockedContact
    mockedContact.telefon >> phoneNumber
    // Note, we don't mock a fax number
    mockedOrgUnit.anschriften >> [mockedAddress]
    mockedAddress.strasseHausnummer >> streetAndHouseNumber
    mockedAddress.postleitzahl >> zipCode
    mockedAddress.ort >> city
    // Note, we don't mock a postfach

    when:
    String result = SupportContactDataGetter.getSupportContactOnOzgHub(mockedApi)

    then:
    !result.contains("Land Baden-Württemberg")
    !result.contains("service-bw@im.bwl.de")
    result.startsWith(nameOfOrgUnit)
    result.contains("Telefon: " + phoneNumber)
    !result.contains("Fax")
    result.contains("Anschrift")
    result.contains(streetAndHouseNumber)
    result.contains(zipCode)
    result.contains(city)
    !result.contains("Postfach")
  }
}
