package commons.serviceportal.forms

import groovy.json.JsonSlurper

/**
 * This class checks for common errors in forms that might not be immediately obvious. It should
 * be used in a test/specification. If a error is detected, an Exception is thrown.
 *
 * It currently checks the following:
 * - Form is valid json
 * - Visibility conditions link only to fields that are also in the form
 */
class FormValidator {
  String json
  def form

  /**
   * Create a new FormValidator
   *
   * @param json
   *     the json defining the form
   */
  FormValidator(String json) {
    this.json = json
  }

  /**
   * Performs the various tests. (See main object definition)
   */
  void validate() {
    // Parse json
    try {
      form = new JsonSlurper().parseText(json)
    } catch (Exception e) {
      throw new Exception("Form is not valid json.", e)
    }

    checkVisibilityConditions()
  }

  private void checkVisibilityConditions() {
    form.sections.each { section ->
      section.fieldGroups.each { fieldGroup ->
        fieldGroup.rows.each { row ->
          row.fields.each { field ->
            if (field.displayConditions == null) {
              // Field has no display conditions --> continue
            } else {
              field.displayConditions.each { displayCondition ->
                String sourceDescription = "Field '${field.label}' (group: ${fieldGroup.id}, id: ${field.id})"

                try {
                  checkVisibilityCondition(displayCondition, sourceDescription)
                } catch (FormValidationException e) {
                  // Re-throw exception with additional information about the form that failed
                  throw new FormValidationException("Failed to validate form '${form."id"}'.", e)
                }
              }
            }
          }
        }
      }
    }
  }

  private void checkVisibilityCondition(def displayCondition, String sourceDescription) {
    String type = displayCondition."@type"
    switch (type) {
      case ["ShowOnFieldValuesCondition", "ShowOnFieldValueNotInValuesCondition", "ShowOnFilledFieldCondition", "ShowOnEmptyFieldCondition"]:
        String groupId = displayCondition.conditionFieldKey.groupId
        String fieldId = displayCondition.conditionFieldKey.fieldId
        if (!doesFieldExist(groupId, fieldId)) {
          throw new FormValidationException("$sourceDescription references field with groupId = '$groupId' and " +
                  "fieldId = '$fieldId' in a visibility condition, but that field does not exist.")
        }
        break
      case ["AndCondition", "OrCondition"]:
        displayCondition.conditions.eachWithIndex { innerCondition, idx ->
          sourceDescription = "The $idx. nested displayCondition of $sourceDescription"
          checkVisibilityCondition(innerCondition, sourceDescription)
        }
        break
      case ["ShowOnProcessVariableInValuesCondition"]:
        // Nothing to check here. We can't know if the process instance variable will be available, therefore we just
        // accept the situation.
        break
      default:
        throw new UnsupportedOperationException("Not implemented: FormValidator doesn't know how " +
                "to handle display condition type '$type'")
    }
  }

  private boolean doesFieldExist(String groupId, String fieldId) {
    for (section in form.sections) {
      def group = section.fieldGroups.find { group -> group.id == groupId }
      if (group == null) {
        continue
      }
      for (row in group.rows) {
        if (row.fields.find { field -> field.id == fieldId } != null) {
          return true
        }

      }
    }
    return false
  }
}

class FormValidationException extends Exception {
  FormValidationException(String message) {
    super(message)
  }

  FormValidationException(String message, Throwable cause) {
    super(message, cause)
  }
}

