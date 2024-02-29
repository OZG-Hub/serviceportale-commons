package commons.serviceportal.forms

import groovy.json.JsonSlurper

/**
 * This class verifies if two, or more, forms have the same structure (e.g. the same questions, in the same sections)
 * and only differ in things that are reasonable for a translated form (e.g. field labels, the form title or the form
 * id)
 */
class ConsistentTranslationValidator {
  Map<String, String> formsToContent = [:] // Mapping form name to form content (as JSON-String)

  ConsistentTranslationValidator(Map<String, String> formsToContent) {
    this.formsToContent = formsToContent
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
    differences.removeAll { it.path == "id" } // The id of the form has to be different, as they are separate forms
    differences.removeAll { it.path.endsWith("title") } // The title attribute is only used for display purposes
    differences.removeAll { it.path.endsWith("label") } // The label attribute is only used for display purposes

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
