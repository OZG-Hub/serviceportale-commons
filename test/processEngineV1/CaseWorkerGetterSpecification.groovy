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

  private ScriptingApiV1 mockedApi

  def setup()
  {
    mockedApi = Mock()
    mockedApi.logger >> Mock(LoggerApiV1)
  }

  def "get assigned case worker for valid entry"() {
    given:
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
    1 * mockedApi.logger.info('Determining case worker...\nCandidate orgUnits are: \'Musterorganisationseinheit\' (ID: null)\nChecking orgUnit \'null\'\n  Ausprägung is ok.\n  Checking "Kommunikation" of orgUnit...\n    Kommunikation to Servicekonto \'12345\' found.\nDetermining assigned case worker succesfull. Result is Servicekonto \'12345\'.')
    0 * mockedApi.logger.info(_ as String, _ as Exception)
    0 * mockedApi.logger.warn(_ as String)
    0 * mockedApi.logger.warn(_ as String, _ as Exception)
  }

  def "get assigned case worker for not-servicekonto kommunikation"() {
    given:
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
    1 * mockedApi.logger.info('Determining case worker...\nCandidate orgUnits are: \'Musterorganisationseinheit\' (ID: null)\nChecking orgUnit \'null\'\n  Ausprägung is ok.\n  Checking "Kommunikation" of orgUnit...\n    No "Kommunikation" with kanal \'SERVICEKONTO\' found.\n')
    0 * mockedApi.logger.info(_ as String, _ as Exception)
    0 * mockedApi.logger.warn(_ as String)
    0 * mockedApi.logger.warn(_ as String, _ as Exception)
  }

  def "get assigned case worker for missing kommunikation"() {
    given:
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
    1 * mockedApi.logger.info('Determining case worker...\nCandidate orgUnits are: \'Musterorganisationseinheit\' (ID: null)\nChecking orgUnit \'null\'\n  Ausprägung is ok.\n  Checking "Kommunikation" of orgUnit...\n    No "Kommunikation" with kanal \'SERVICEKONTO\' found.\n')
    0 * mockedApi.logger.info(_ as String, _ as Exception)
    0 * mockedApi.logger.warn(_ as String)
    0 * mockedApi.logger.warn(_ as String, _ as Exception)
  }

  Set<ProcessOrganisationseinheitI18nV1> getMockedI18n() {
    ProcessOrganisationseinheitI18nV1 i18n = Mock()
    i18n.sprache >> "de"
    i18n.name >> "Musterorganisationseinheit"
    return [i18n]
  }
}
