package commons.serviceportal.forms

import de.seitenbau.serviceportal.scripting.api.v1.form.PossibleValueV1
import de.seitenbau.serviceportal.scripting.api.v1.form.VerifiedFormFieldValueV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.BinaryContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormContentV1
import de.seitenbau.serviceportal.scripting.api.v1.form.content.FormFieldContentV1
import groovy.json.JsonSlurper

import java.text.ParseException
import java.text.SimpleDateFormat

class JsonToFormContentConverter {
  static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ") // Default format for JSON-ified dates

  /**
   * Convert a FormContent exported to JSON back into a FormContent object.
   * This can be very useful if a process developer wants to write a test (where FormContent is usually not available)
   *
   * @param json A string of a json-ifed FormContent. I.e. <code>from JsonOutput.toJson(formContent)</code>
   * @return A FormContent that equals the original FormContent
   */
  static FormContentV1 convert(String json) {
    def parsed = new JsonSlurper().parseText(json)
    FormContentV1 formContent = new FormContentV1("formIdDoesNotMatterHere")

    parsed.each { key, value ->
      switch (key) {
        case "createdOn":
          // Parse date. Depending on the exporter the date is either formatted as string or an epoch timestamp.
          formContent."${key}" = parseDate(value)
          break

        case ["notNullFieldValuesAsMap", "valid"]:
          // Read-Only property. Ignore them.
          break

        case "fields":
          // We need to parse fields individually to perform some type conversions for some fields
          value = value as Map<String, FormFieldContentV1>
          formContent.fields = [:]

          value.each { fieldKey, formFieldContentMap ->

            // Some exports wrap the actual value together with its Java type, e.g.
            // { "type": "java.lang.String", "value": "Müller" }. Unwrap those before further processing.
            if (formFieldContentMap.value instanceof Map
                    && formFieldContentMap.value.containsKey("type")
                    && formFieldContentMap.value.containsKey("value")) {
              formFieldContentMap.value = formFieldContentMap.value.value
            }

            // check if this field could be a date
            try {
              Date fieldValueAsDate = DATE_FORMAT.parse(formFieldContentMap.value as String)
              formFieldContentMap.value = fieldValueAsDate // override in Date format
            } catch (ParseException | NullPointerException ignored) {
              // field could not be parsed as Date. Just continue and leave it as is.
            }

            // check if this field could be a BinaryContent
            if (formFieldContentMap.value != null && formFieldContentMap.value instanceof Map && formFieldContentMap.value.get("uploadedFilename") != null) {
              // This is actually a BinaryContent - let's interpret it this way by re-creating a new one.
              def unparsed = formFieldContentMap.value
              String bcKey = unparsed.key
              String uploadedFilename = unparsed.uploadedFilename
              String label = unparsed.label
              String mimetype = unparsed.mimetype
              byte[] data = (unparsed.data as ArrayList) as byte[]
              BinaryContentV1 bc = new BinaryContentV1(bcKey, uploadedFilename, label, mimetype, data)
              formFieldContentMap.value = bc
            }

            // check if this field could be a VerifiedFormFieldValueV1
            if (formFieldContentMap.value != null && formFieldContentMap.value instanceof Map && formFieldContentMap.value.get("verificationToken") != null) {
              def unparsed = formFieldContentMap.value
              def newValue = unparsed.value
              String verificationToken = unparsed.verificationToken
              VerifiedFormFieldValueV1 verifiedFormFieldValueV1 = new VerifiedFormFieldValueV1(newValue, verificationToken)
              formFieldContentMap.value = verifiedFormFieldValueV1
            }

            // formFieldContentMap and possibleValues are LazyMaps (from JSON)!
            // We need to explicitly convert them to the correct class
            List<PossibleValueV1> possibleValues = null;
            if (formFieldContentMap.possibleValues != null)
            {
              possibleValues = new ArrayList<>();
              formFieldContentMap.possibleValues.each {
                possibleValues.add(PossibleValueV1.builder().label(it.label).value(it.value).build())
              }
            }
            FormFieldContentV1 formFieldContent = new FormFieldContentV1(formFieldContentMap.value, formFieldContentMap.validationMessages, possibleValues)
            formContent.fields.put(fieldKey, formFieldContent)
          }
          break

        default:
          // in all other cases, simply set the value
          formContent."${key}" = value
          break
      }
    }

    return formContent
  }

  private static Date parseDate(Object value) {
    if (value instanceof Number) {
      // Epoch timestamp in milliseconds
      return new Date(((Number) value).longValue())
    }
    return DATE_FORMAT.parse(value as String)
  }
}
