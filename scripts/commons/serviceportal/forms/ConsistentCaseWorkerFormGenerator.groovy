package commons.serviceportal.forms

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Generates a suitable case worker form from a given applicant form and some parameters.
 */
class ConsistentCaseWorkerFormGenerator {
  final String applicantFormAsJson
  final Object applicantFormParsed
  final String desiredId
  final String desiredSourceVariableName
  final String desiredTargetVariableName
  final String desiredContinueOptionLabel
  final LinkedHashMap<String, String> desiredCustomButtons // Mapping button value to button label
  final String desiredCustomButtonTargetVariableName

  /**
   * Sets up the form generator.
   *
   * @param applicantFormAsJson the corresponding applicant form.
   * @param desiredId the id attribute of the json. Including mandant id and version. e.g:
   *   446:BadMergentheim_AufenthaltserlaubnisBlaueKarteNiederlassung_CaseWorker:v1.0
   * @param desiredSourceVariableName
   * @param desiredTargetVariableName
   * @param desiredCustomButtons a map of button values and button labels. The first entry in this ordered map is
   *   considered the primary button.
   * @param desiredCustomButtonTargetVariableName the process instance variable where the clicked custom button should
   *   be saved to.
   */
  ConsistentCaseWorkerFormGenerator(String applicantFormAsJson,
                                    String desiredId,
                                    String desiredContinueOptionLabel = "Ja, Daten an Fachverfahren übertragen",
                                    String desiredSourceVariableName = "applicantForm",
                                    String desiredTargetVariableName = "caseworkerForm",
                                    LinkedHashMap<String, String> desiredCustomButtons = null,
                                    String desiredCustomButtonTargetVariableName = "clickedButton") {
    this.applicantFormAsJson = applicantFormAsJson
    applicantFormParsed = new JsonSlurper().parseText(applicantFormAsJson)
    this.desiredId = desiredId
    this.desiredContinueOptionLabel = desiredContinueOptionLabel
    this.desiredSourceVariableName = desiredSourceVariableName
    this.desiredTargetVariableName = desiredTargetVariableName
    this.desiredCustomButtons = desiredCustomButtons
    this.desiredCustomButtonTargetVariableName = desiredCustomButtonTargetVariableName
  }

  /**
   * Generates a suitable case worker form according to the desired parameters set in the constructor. To do so, the
   * following operations will be performed:
   *
   * 1. Update the ID of the form
   * 2. Add an introduction section
   * 3. Add the sections from the supplied applicant form
   * 4. Change those values to read-only (so the case worker can't change the values of the applicant)
   * 5. Add a feedback section
   * 6. Update source and target variables
   * 7. Set custom buttons
   *
   * @return A String of a JSON that can be used as the case worker form
   */
  String generatedCaseWorkerForm() {
    Map<String, Object> result = [:]

    // Add id
    result.put("id", desiredId)

    // Add form title
    String caseWorkerTitle
    String applicantFormTitle = applicantFormParsed."title"
    if (applicantFormTitle.contains(" - Antragsformular")) {
      caseWorkerTitle = applicantFormTitle.replaceAll(" - Antragsformular", " - Sachbearbeiterformular")
    } else {
      caseWorkerTitle = applicantFormTitle
    }
    result.put("title", caseWorkerTitle)

    // Add sections
    List<Object> sections = []
    sections.add(getIntroductionSection())
    sections.addAll(getDisableFields())
    sections.add(getCaseWorkerResultSection())
    result.put("sections", sections)

    // Add 'source' process instance variable
    Map<String, String> source = [
            "service"     : "prozess",
            "variableName": desiredSourceVariableName
    ]
    result.put("source", source)

    // Add 'target' process instance variable
    Map<String, String> target = [
            "service"     : "prozess",
            "variableName": desiredTargetVariableName
    ]
    result.put("target", target)

    // Add custom buttons
    if (desiredCustomButtons != null && !desiredCustomButtons.isEmpty()) {
      List<Map<String, Object>> buttons = []
      boolean isFirstButton = true
      desiredCustomButtons.each { buttonValue, buttonLabel ->
        Map button = [
                "value"   : buttonValue,
                "label"   : buttonLabel,
                "primary" : isFirstButton,
                "helptext": ""
        ]
        isFirstButton = false
        buttons.add(button)
      }
      result.put("customButtons", [
              "buttons": buttons,
              "target" : [
                      "service"     : "prozess",
                      "variableName": desiredCustomButtonTargetVariableName
              ]
      ])
    } else {
      // Don't add custom buttons. This attribute is not required.
    }

    // Render and return the result
    return JsonOutput.toJson(result)
  }

  private List<Map> getDisableFields() {
    List<Map> sections = applicantFormParsed."sections"

    sections.each { section ->
      section."fieldGroups".each { fieldGroup ->
        fieldGroup."rows".each { row ->
          row."fields".each { field ->
            String type = field."type"

            final Set<String> disableableFieldTypes = ["STRING", "DATE", "TEXTAREA", "DROPDOWN_SINGLE_SELECT",
                                                       "BOOLEAN", "RADIO_BUTTONS", "MULTIPLE_FILE", "CHECKBOX"]
            final Set<String> fieldTypesThatDontNeedToBeDisabled = ["HINTBOX"]

            if (disableableFieldTypes.contains(type)) {
              field."disabled" = true
            } else if (fieldTypesThatDontNeedToBeDisabled.contains(type)) {
              // Field cannot (but also doesn't have to be) disabled
            } else {
              throw new UnsupportedOperationException("ConsistentCaseWorkerFormGenerator failed to disable the field" +
                      "'${field."id"}' as it's field type ('${type}') is unknown. Please extend " +
                      "ConsistentCaseWorkerFormGenerator to support this field type.")
            }
          }
        }
      }
    }

    return sections
  }

  private static Map getIntroductionSection() {
    final String json = """\
      {
        "title": "Einleitung zur Sachbearbeitung",
        "fieldGroups": [
          {
            "title": "Einleitung zur Sachbearbeitung",
            "rows": [
              {
                "fields": [
                  {
                    "id": "intoToCaseWorkerTasksHint",
                    "label": "Sachbearbeitung",
                    "type": "HINTBOX",
                    "width": 12,
                    "additionalConfig": {
                      "@type": "AdditionalHintboxConfig",
                      "text": "Dieses Formular führt alle Angaben auf, die die antragstellende Person gemacht hat. Bitte prüfen Sie die Angaben und entscheiden Sie dann im letzten Abschnitt, ob eine Korrektur notwendig ist, oder ob die Daten weiterverarbeitet werden können.",
                      "status": "INFO"
                    }
                  }
                ]
              }
            ],
            "id": "intoToCaseWorkerTasksGroup"
          }
        ]
      }
      """.stripIndent()

    Map parsed = new JsonSlurper().parseText(json) as Map
    return parsed
  }

  private Map getCaseWorkerResultSection() {
    final String json = """\
      {
      "title": "Ergebnis der Sachbearbeitung",
        "fieldGroups": [
          {
            "title": "Ergebnis der Sachbearbeitung",
            "rows": [
              {
                "fields": [
                  {
                    "id": "caseWorkerResult",
                    "label": "Ist der Antrag korrekt und kann weiter verarbeitet werden?",
                    "type": "RADIO_BUTTONS",
                    "layout": "",
                    "possibleValues": [
                      {
                        "value": "continue",
                        "label": "$desiredContinueOptionLabel"
                      },
                      {
                        "value": "requestCorrection",
                        "label": "Nein, antragstellende Person um Korrektur bitten"
                      }
                    ],
                    "value": "",
                    "width": 12,
                    "validationRules": [],
                    "required": true,
                    "target": {
                      "service": "prozess",
                      "variableName": "caseWorkerResult"
                    }
                  }
                ]
              },
              {
                "fields": [
                  {
                    "id": "feedbackForApplicant",
                    "label": "Feedback für die antragstellende Person",
                    "type": "TEXTAREA",
                    "width": 12,
                    "validationRules": [],
                    "required": true,
                    "helptext": "Dieser Text wird der antragstellenden Person angezeigt werden.",
                    "displayConditions": [
                      {
                        "@type": "ShowOnFieldValuesCondition",
                        "conditionFieldKey": {
                          "groupId": "caseWorkerResultGroup",
                          "groupIndex": 0,
                          "fieldId": "caseWorkerResult"
                        },
                        "values": [
                          "requestCorrection"
                        ]
                      }
                    ],
                    "target": {
                      "service": "prozess",
                      "variableName": "feedbackForApplicant"
                    }
                  }
                ]
              }
            ],
            "id": "caseWorkerResultGroup"
          }
        ]
      }
      """.stripIndent()

    Map parsed = new JsonSlurper().parseText(json) as Map
    return parsed
  }
}
