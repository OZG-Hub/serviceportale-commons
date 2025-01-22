package commons.serviceportal

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import de.seitenbau.serviceportal.scripting.api.v1.start.OrganisationseinheitParameterV1
import de.seitenbau.serviceportal.scripting.api.v1.start.StartParameterV1

class SupportContactDataGetter {

  /**
   * Returns a text containing the contact data of the relevant support channel.
   * Suitable for displaying to user in case of an issue with the process.
   * This data is read from the `startParameter` variable, as set by the process engine.
   * See https://doku.pmp.seitenbau.com/display/DFO/Automatisch+gesetzte+Prozessvariablen
   * Defaults to IM BW, in case a more suitable org unit was not provided.
   *
   * @param api the scripting API (as read from the automatically set process instance variabel `apiV1`).
   *            Used to read the `startParameter` variable.
   * @return something like:
   *         Musterbehörde
   *
   *         E-Mail: example@example.org
   *         Telefon: +49 123 456789
   *         Fax: +49 123 456 789
   *
   *         Anschrift:
   *         Musterstraße 123
   *         Postfach 123
   *         12345 Musterort
   *
   *         Anschrift:
   *         Musterstraße 123
   *         Postfach 123
   *         12345 Musterort
   */
  static String getSupportContactOnOzgHub(ScriptingApiV1 api) {
    StartParameterV1 startParameter = api.getVariable("startParameter", StartParameterV1)
    OrganisationseinheitParameterV1 orgUnit = startParameter.organisationseinheit
    if (orgUnit == null) {
      final String defaultContact = "Land Baden-Württemberg\n" +
              "E-Mail: service-bw@im.bwl.de\n" +
              "Telefax: +49 (0)711/231-5000\n" +
              "Internetseite: https://im.baden-wuerttemberg.de"
      api.logger.warn("Failed to determine contact when displaying error page for the user. Process " +
              "start parameter 'organisationseinheit' was null. (Probably because this process was not started in a " +
              "parametrized way.) Defaulting to '$defaultContact'")
      return defaultContact
    } else {
      // Name
      String result = orgUnit.name + "\n"
      result += "\n"

      // Print phone, fax and email
      if(orgUnit.erreichbarkeit?.email){
        result += "Email: ${orgUnit.erreichbarkeit?.email}\n"
      }

      if(orgUnit.erreichbarkeit?.telefon){
        result += "Telefon: ${orgUnit.erreichbarkeit?.telefon}\n"
      }

      if(orgUnit.erreichbarkeit?.fax){
        result += "Fax: ${orgUnit.erreichbarkeit?.fax}\n"
      }

      result += "\n"

      // Address
      orgUnit.anschriften.each {
        result += "Anschrift:\n"
        if (it.strasseHausnummer) {
          result += "${it.strasseHausnummer}\n"
        }
        if (it.postfach) {
          result += "Postfach: ${it.postfach}\n"
        }
        result += "${it.postleitzahl ?: ""} ${it.ort ?: ""}\n"
        result += "\n"
      }

      return result
    }
  }
}
