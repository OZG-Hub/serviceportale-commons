package commons.serviceportal.forms

import groovy.json.JsonSlurper
import spock.lang.Specification

class ConsistentCaseWorkerFormGeneratorSpecification extends Specification {
  def "Generating a case worker form"() {
    given:
    String aDesiredNewId = "446:BadMergentheim_AufenthaltserlaubnisBlaueKarteNiederlassung_CaseWorker:v1.0"
    String aFieldId = "firstNames"
    String aFieldLabel = "Vorname(n)"
    String anApplicantForm = """\
      {
        "id": "123:ExampleCity_ExampleProcess_ApplicantForm:v1.0",
        "title": "Beispielprozess",
        "sections": [
          {
            "title": "Persönliche Angaben",
            "fieldGroups": [
              {
                "title": "Persönliche Angaben",
                "rows": [
                  {
                    "fields": [
                      {
                        "id": "$aFieldId",
                        "label": "$aFieldLabel",
                        "type": "STRING",
                        "width": 12,
                        "validationRules": [],
                        "required": true
                      }
                    ]
                  }
                ],
                "id": "personalDataGroup"
              }
            ]
          }
        ],
        "source": {
          "service": "prozess",
          "variableName": "applicantForm"
        },
        "target": {
          "service": "prozess",
          "variableName": "applicantForm"
        }
      }
      """.stripIndent()

    when:
    ConsistentCaseWorkerFormGenerator formGenerator = new ConsistentCaseWorkerFormGenerator(anApplicantForm, aDesiredNewId)
    String caseWorkerForm = formGenerator.generatedCaseWorkerForm()
    def parsedCaseWorkerForm = new JsonSlurper().parseText(caseWorkerForm)

    then:
    // new ID was set
    parsedCaseWorkerForm."id" == aDesiredNewId

    // There is a new intro section
    parsedCaseWorkerForm."sections"[0]."title" ==  "Einleitung zur Sachbearbeitung"

    // The fields from the applicant form are still there, but now disabled
    parsedCaseWorkerForm."sections"[1]."fieldGroups"[0]."rows"[0]."fields"[0]."id" == aFieldId
    parsedCaseWorkerForm."sections"[1]."fieldGroups"[0]."rows"[0]."fields"[0]."label" == aFieldLabel
    parsedCaseWorkerForm."sections"[1]."fieldGroups"[0]."rows"[0]."fields"[0]."disabled" == true

    // There is a new outro section
    parsedCaseWorkerForm."sections"[2]."title" == "Ergebnis der Sachbearbeitung"
    parsedCaseWorkerForm."sections"[2]."fieldGroups"[0]."rows"[0]."fields"[0].id == "caseWorkerResult"
    parsedCaseWorkerForm."sections"[2]."fieldGroups"[0]."rows"[0]."fields"[0]."label" == "Ist der Antrag korrekt und kann weiter verarbeitet werden?"

  }
}
