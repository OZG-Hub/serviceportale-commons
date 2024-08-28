package commons.serviceportal.forms

import groovy.json.JsonSlurper

/**
 * This class verifies if two, or more, forms have the same structure (e.g. the same questions, in the same sections)
 * and only differ in things that are reasonable for a translated form (e.g. field labels, the form title or the form
 * id)
 */
class ConsistentTranslationValidator {
  Map<String, String> formsToContent = [:] // Mapping form name to form content (as JSON-String)

  static List<String> exportTranslatableStrings(String formDefinition) {
    def parsed = new JsonSlurper().parseText(formDefinition)

    List<String> result = collectTranslatableStringsInSubgroup(parsed)
    return result
  }

  ConsistentTranslationValidator(Map<String, String> formsToContent) {
    this.formsToContent = formsToContent
  }

  static List<String> collectTranslatableStringsInSubgroup(def thisLevel, String path = '') {
    List<String> translatableStrings = []

    if (thisLevel instanceof Map) {
      for (subElement in thisLevel) {
        String currentPath = path ? "${path}.${subElement.key}" : subElement.key
        translatableStrings.addAll(collectTranslatableStringsInSubgroup(subElement.value, currentPath))
      }
    } else if (thisLevel instanceof List) {
      for (int i = 0; i < thisLevel.size(); i++) {
        String currentPath = "${path}[${i}]"
        translatableStrings.addAll(collectTranslatableStringsInSubgroup(thisLevel.get(i), currentPath))
      }
    } else {
      if (isTranslatableAttribute(path)) {
        translatableStrings.add(thisLevel)
      } else {
        // This attribute is not translatable. So just skip this iteration.
      }
    }

    return translatableStrings
  }

  void validate() {
    // Parse forms
    Map<String, Object> parsedForms = [:]
    formsToContent.each { name, json ->
      // Parse json
      try {
        def parsed = new JsonSlurper().parseText(json)
        parsedForms.put(name, parsed)
      } catch (Exception e) {
        throw new Exception("Form '$name' is not valid json.", e)
      }
    }

    // Iterate over the list with two nested loops, where the inner loop always starts one position ahead of the outer
    // loop. This approach ensures that each pair is compared only once.
    List<String> formNames = parsedForms.keySet().toList() // Required to access elements by index later
    for (int i = 0; i < parsedForms.size(); i++) {
      for (int j = i + 1; j < parsedForms.size(); j++) {
        Object form1 = parsedForms.get(formNames.get(i))
        Object form2 = parsedForms.get(formNames.get(j))

        // Verify id's are indeed different
        if (form1."id" == form2."id") {
          throw new Exception("Form '${form1."id"}' and '${form2."id"}' have the same id attribute but should have " +
                  "differences as they are different forms! Thats was probably missed during the translation.")
        }

        validateStructurallyTheSame(form1, form2)
      }
    }
  }

  private static void validateStructurallyTheSame(Object form1, Object form2) {
    assert form1 instanceof Map
    assert form2 instanceof Map

    List<Difference> differences = findDifferences(form1, form2)

    // Remove acceptable differences
    differences.removeAll { isTranslatableAttribute(it.path) }

    // Check if there are any non-acceptable differences remaining
    if (differences.isEmpty()) {
      // all good!
    } else {
      String msg = "Forms are not structurally the same! Differences found between '${form1."id"}' and '${form2."id"}':"
      msg += "\n"
      differences.each {
        msg += "- Path: $it.path, Value of first form: '$it.value1', Value of second form: '$it.value2'\n"
      }
      throw new Exception(msg)
    }

  }

  private static List<Difference> findDifferences(Object a, Object b, String path = '') {
    List<Difference> differences = []

    if (a instanceof Map && b instanceof Map) {
      def allKeys = (a.keySet() + b.keySet()).unique()
      allKeys.each { key ->
        String currentPath = path ? "${path}.${key}" : key
        differences += findDifferences(a[key], b[key], currentPath)
      }
    } else if (a instanceof List && b instanceof List) {
      int maxIndex = Math.max(a.size(), b.size())
      for (int i = 0; i < maxIndex; i++) {
        def currentPath = "${path}[${i}]"
        def valueA = i < a.size() ? a[i] : "NO_VALUE"
        def valueB = i < b.size() ? b[i] : "NO_VALUE"
        differences += findDifferences(valueA, valueB, currentPath)
      }
    } else if (a != b) {
      differences.add(new Difference(path, a, b))
    }

    return differences.findAll { it != null }
  }

  /**
   * Returns if a give attribute (from the form json) is allowed to be translated.
   *
   * Examples:
   * - The root "id" attribute --> true, because a translated form will have a different id
   * - Attributes, ending in "label" --> true, because that's the text a user reads in front of a form field
   * - The "id" attribute of a field --> false, because that might be used in a display condition
   *
   * @param attributePath the json path to the attribute. E.g. "sections[0].fieldGroups[1].rows[3].fields[0].label"
   *
   * @return true or false
   */
  private static boolean isTranslatableAttribute(String attributePath) {
    if (attributePath == "id") return true // The id of the form has to be different, as they are separate forms

    if (attributePath.endsWith("title")) return true // title of sections or similar
    if (attributePath.endsWith("label")) return true // labels of questions
    if (attributePath.endsWith("placeholder")) return true // placeholder in questions

    if (attributePath.endsWith("additionalConfig.text")) return true // E.g. the label of hint-boxes

    if (attributePath.endsWith("caption")) return true // image caption
    if (attributePath.endsWith("alt")) return true // image alt tag

    if (attributePath.endsWith("helptext")) return true // Hover text on labels
    if (attributePath.endsWith("addRowButton")) return true // Button labels
    if (attributePath.endsWith("deleteRowButton")) return true // Button labels
    if (attributePath.endsWith("addRowButtonInfoText")) return true // hover text for AddRow buttons
    if (attributePath.endsWith("deleteRowButtonInfoText")) return true // hover text for DeleteRow buttons

    if (attributePath.endsWith("instanceTitleTemplate")) return true // template for repeatable groups / accordions

    if (attributePath.endsWith("requiredValidationFailedMessage")) return true // message if validation failed
    if (attributePath.endsWith("typeValidationFailedMessage")) return true // message if validation failed
    if (attributePath.endsWith("validationErrorMessage")) return true // message if validation failed
    if (attributePath.endsWith("validationInvalidNumberMessage")) return true // message if validation failed
    if (attributePath.endsWith("validationDecimalPointError")) return true // message if validation failed
    if (attributePath.endsWith("validationNonNegativeNumberError")) return true // message if validation failed

    return false
  }
}

class Difference {
  final String path
  final def value1
  final def value2

  Difference(String path, Object value1, Object value2) {
    this.path = path
    this.value1 = value1
    this.value2 = value2
  }
}
