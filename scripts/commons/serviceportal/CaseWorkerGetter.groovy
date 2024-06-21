package commons.serviceportal

import de.seitenbau.serviceportal.scripting.api.v1.message.NachrichtAbsenderV1
import de.seitenbau.serviceportal.scripting.api.v1.process.ProcessOrganisationseinheitExtendedV1
import de.seitenbau.serviceportal.scripting.api.v1.process.ProcessOrganisationseinheitKommunikationV1
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class CaseWorkerGetter {

  /**
   * Returns the case worker assigned to the process.
   *
   * @param assignedOrgUnits the result of the ZustaendigeOrganisationseinheitErmittelnService service task.
   * See https://doku.pmp.seitenbau.com/x/ZAYG for more details
   *
   * @param printLogMessages Set to false to prevent a log message from being printed. Otherwise a single log message
   * on the INFO level will be printed that displays how the case worker was determined.
   *
   * @return the assigned caseworker as a 'NachrichtAbsenderV1' so it can be used as a sender for
   * 'Servicekontonachrichten'
   *
   * @throws CouldNotDetermineCaseWorkerException when no case worker could be determined. The exceptions message
   * contains more details about the reason.
   */
  static NachrichtAbsenderV1 getAssignedCaseWorkerAsSender(List<ProcessOrganisationseinheitExtendedV1> assignedOrgUnits, boolean printLogMessages = true) throws CouldNotDetermineCaseWorkerException {
    Logger logger = LoggerFactory.getLogger("de.seitenbau.serviceportal.prozess.publicserviceteam.commonssubmodule.caseworkergetter")
    List<String> detectedServicekontoIds = []
    String logMessage = "Determining case worker...\n"
    String assignedCaseworkerName = ""
    // Null check. I don't think this can happen if the service task is used, but we need to inform developers if they
    // make mistakes while using this class
    if (assignedOrgUnits == null) {
      throw new IllegalArgumentException("Required method parameter 'assignedOrgUnits' was null. Please provide a valid value.")
    }


    // Ensure there are assigned org units
    if (assignedOrgUnits.empty) {
      throw new CouldNotDetermineCaseWorkerException("Failed to determine assigned case worker. List of assigned org " +
              "units is empty. Most likely cause is that this process was started without a 'leistung' or 'region'. " +
              "Another cause might be that this Leistung does not have a assigned org unit. Please ensure that a " +
              "list is provided to CaseWorkerGetter.")
    }

    logMessage += "Candidate orgUnits are: "
    logMessage += assignedOrgUnits.collect {
      String orgUnitName = it.oe.i18n.find {
        it.sprache == "de"
      }.name
      return "'$orgUnitName' (ID: ${it.oe.id})"
    }.join(", ")
    logMessage += "\n"

    // Iterate all org Units
    for (assignedOrgUnit in assignedOrgUnits) {
      logMessage += "Checking orgUnit '${assignedOrgUnit.oe.id}'\n"

      // Verify "Ausprägung" (i.e. if the orgUnit is actually responsible for this "Leistung")
      assert assignedOrgUnit.aufgabengebiet in ["KEINE", "ANSPRECHPUNKT", "ZUSTAENDIGE_STELLE_UND_ANSPRECHPUNKT"]:
              "Unexpected Ausprägung '${assignedOrgUnit.aufgabengebiet}' in orgUnit '${assignedOrgUnit.oe.accountId}'. " +
                      "Please inform public-service@seitenbau.com, someone needs to update the CaseWorkerGetter class."
      if (assignedOrgUnit.aufgabengebiet == "ANSPRECHPUNKT") {
        logMessage += "  Ausprägung is only 'Beraten', therefore this orgUnit can't provide a assigned case worker.\n"
        continue
      } else {
        logMessage += "  Ausprägung is ok.\n"
      }
      assignedCaseworkerName = assignedOrgUnit.oe.i18n.find{it.sprache == "de"}
      // Find relevant "Kommunikation"
      logMessage += "  Checking \"Kommunikation\" of orgUnit...\n"
      Set<ProcessOrganisationseinheitKommunikationV1> kommunikationsWithServicekonto = assignedOrgUnit.oe.kommunikation.findAll { it?.kanal == "SERVICEKONTO" }
      if (kommunikationsWithServicekonto.empty) {
        logMessage += "    No \"Kommunikation\" with kanal 'SERVICEKONTO' found.\n"
      } else {
        kommunikationsWithServicekonto.each {
          detectedServicekontoIds.add(it.kennung)
          logMessage += "    Kommunikation to Servicekonto '${it.kennung}' found.\n"
        }
      }
    }

    // Ensure there are results
    if (detectedServicekontoIds.size() < 1) {
      if (printLogMessages) logger.info(logMessage)
      throw new CouldNotDetermineCaseWorkerException("Failed to determine assigned case worker. No fitting " +
              "Servicekonto-ID could be determined. Please ensure that at least one assigned org unit has a " +
              "\"Kommunikation\" setting with a valid Servicekonto-ID.")
    }

    // Ensure only a single result
    if (detectedServicekontoIds.size() > 1) {
      if (printLogMessages) logger.info(logMessage)
      throw new CouldNotDetermineCaseWorkerException("Failed to determine assigned case worker. There are multiple " +
              "Servicekonto-IDs that could be considered a valid case worker. Please ensure that only a single ID is " +
              "valid, for example by setting a assigned org unit to 'beratend tätig'.")
    }

    String senderId = detectedServicekontoIds.first()
    NachrichtAbsenderV1 sender = new NachrichtAbsenderV1.NachrichtAbsenderV1Builder()
            .name(assignedCaseworkerName)
            .servicekontoId(Long.parseLong(senderId))
            .build()
    logMessage += "Determining assigned case worker succesfull. Result is Servicekonto '$senderId'."
    if (printLogMessages) logger.info(logMessage)
    return sender
  }


  /**
   *
   * @param assignedOrgUnits the result of the ZustaendigeOrganisationseinheitErmittelnService service task.
   * See https://doku.pmp.seitenbau.com/x/ZAYG for more details
   *
   * @return The 'Servicekonto-ID' aka. 'IDP-ID' for the given list.
   * Not including the "userId:" part.
   */
  static String getAssignedCaseWorker(List<ProcessOrganisationseinheitExtendedV1> assignedOrgUnits) {
    NachrichtAbsenderV1 sender = getAssignedCaseWorkerAsSender(assignedOrgUnits)
    return sender.servicekontoId.toString()
  }
}

class CouldNotDetermineCaseWorkerException extends Exception {
  CouldNotDetermineCaseWorkerException(String message) {
    super(message)
  }
}
