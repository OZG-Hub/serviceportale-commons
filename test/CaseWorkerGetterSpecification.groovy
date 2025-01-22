import commons.serviceportal.CouldNotDetermineCaseWorkerException
import commons.serviceportal.CaseWorkerGetter
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.LoggerApiV1
import de.seitenbau.serviceportal.scripting.api.v1.process.ProcessOrganisationseinheitExtendedV1
import de.seitenbau.serviceportal.scripting.api.v1.process.ProcessOrganisationseinheitI18nV1
import de.seitenbau.serviceportal.scripting.api.v1.process.ProcessOrganisationseinheitKommunikationV1
import de.seitenbau.serviceportal.scripting.api.v1.process.ProcessOrganisationseinheitV1
import spock.lang.Specification

class CaseWorkerGetterSpecification extends Specification {
  def "get assigned case worker for valid entry"() {
    given:
    ScriptingApiV1 mockedApi = Mock()
    mockedApi.logger >> Mock(LoggerApiV1)
    String expectedCaseWorkerId = "12345"

    ProcessOrganisationseinheitExtendedV1 processOeExtended = Mock()
    processOeExtended.aufgabengebiet >> "KEINE"
    processOeExtended.oe >> {
      ProcessOrganisationseinheitV1 oe = Mock()
      oe.kommunikation >> {
        ProcessOrganisationseinheitKommunikationV1 processKommunikation = Mock()
        processKommunikation.kanal >> "SERVICEKONTO"
        processKommunikation.kennung >> expectedCaseWorkerId
        return [processKommunikation]
      }
      oe.i18n >> getMockedI18n()
      return oe
    }
    List<ProcessOrganisationseinheitExtendedV1> assignedOrgUnits = [processOeExtended]

    when:
    String caseWorker = CaseWorkerGetter.getAssignedCaseWorker(assignedOrgUnits, mockedApi)

    then:
    caseWorker == expectedCaseWorkerId
  }

  def "get assigned case worker for not-servicekonto kommunikation"() {
    given:
    ScriptingApiV1 mockedApi = Mock()
    mockedApi.logger >> Mock(LoggerApiV1)
    ProcessOrganisationseinheitExtendedV1 processOeExtended = Mock()
    processOeExtended.aufgabengebiet >> "KEINE"
    processOeExtended.oe >> {
      ProcessOrganisationseinheitV1 oe = Mock()
      oe.kommunikation >> {
        ProcessOrganisationseinheitKommunikationV1 processKommunikation = Mock()
        processKommunikation.kanal >> "IRGEND WAS ANDERES"
        processKommunikation.kennung >> "123"
        return [processKommunikation]
      }
      oe.i18n >> getMockedI18n()
      return oe
    }
    List<ProcessOrganisationseinheitExtendedV1> assignedOrgUnits = [processOeExtended]

    when:
    String caseWorker = CaseWorkerGetter.getAssignedCaseWorker(assignedOrgUnits, mockedApi)

    then:
    CouldNotDetermineCaseWorkerException exception = thrown(CouldNotDetermineCaseWorkerException)
    exception.message.contains("No fitting Servicekonto-ID could be determined")
  }

  def "get assigned case worker for missing kommunikation"() {
    given:
    ScriptingApiV1 mockedApi = Mock()
    mockedApi.logger >> Mock(LoggerApiV1)
    ProcessOrganisationseinheitExtendedV1 processOeExtended = Mock()
    processOeExtended.aufgabengebiet >> "KEINE"
    processOeExtended.oe >> {
      ProcessOrganisationseinheitV1 oe = Mock()
      oe.kommunikation >> []
      oe.i18n >> getMockedI18n()
      return oe
    }
    List<ProcessOrganisationseinheitExtendedV1> assignedOrgUnits = [processOeExtended]

    when:
    String caseWorker = CaseWorkerGetter.getAssignedCaseWorker(assignedOrgUnits, mockedApi)

    then:
    CouldNotDetermineCaseWorkerException exception = thrown(CouldNotDetermineCaseWorkerException)
    exception.message.contains("No fitting Servicekonto-ID could be determined")
  }

  Set<ProcessOrganisationseinheitI18nV1> getMockedI18n() {
    ProcessOrganisationseinheitI18nV1 i18n = Mock()
    i18n.sprache >> "de"
    i18n.name >> "Musterorganisationseinheit"
    return [i18n]
  }
}
