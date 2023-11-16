package commons.serviceportal.networking

import commons.serviceportal.ServiceportalInformationGetter
import commons.serviceportal.helpers.ServiceportalLogger
import groovy.json.JsonGenerator
import groovy.json.JsonOutput

class EnaioClient {
  final String baseUrl
  final boolean useProxy
  final String username
  final String password

  /**
   * A unique String to separate different parts of the HTTP request
   */
  final static String formDataBoundary = "FormBoundary_tK9BqnVPoQD3JJ3fJwoB"

  /**
   * Creates a new EnaioClient
   *
   * @param baseUrl
   *   The base URL of the API, including the <code>/osrest/api/</code> part.
   *   e.g. https://enaio-gateway.konstanz.info/osrest/api/
   * @param useProxy
   *   Set to true, if the outgoing connection should use the configured proxy. Set to false, if connection should be
   *   made directly.
   * @param username
   *   The username to use for HTTP BasicAuth. Can be <code>null</code> if no auth should be used.
   * @param password
   *   The password to use for HTTP BasicAuth. Required if <code>username</code> is set to something other than null.
   */
  EnaioClient(String baseUrl, boolean useProxy, String username, String password) {
    this.baseUrl = baseUrl
    this.useProxy = useProxy
    this.username = username
    this.password = password

    assert baseUrl != null: "[EnaioClient] baseUrl parameter is required, but was null"
    if (username != null) {
      assert password != null: "[EnaioClient] When username is set, password cannot be null."
    }
  }

  /**
   * Adds a new document to the configured ENAIO system and uploads an attached file.
   *
   * @param objectTypeId
   *   ENAIO objectTypeId of the document that should be inserted.
   * @param enaioFields
   *   Map of all data fields in ENAIO, that should be filled.
   * @param locationId
   *   (optional)
   *   ENAIO locationId in which this new document should be inserted into. Usually a previously created
   *   "Aktenordner". If set to <code>null</code>, document will be inserted into root of cabinet.
   * @param filename
   *   (optional)
   *   Name of the file to attach to this newly created document. Can be <code>null</code> if no file should be
   *   uploaded.
   * @param mimetype
   *   (required if <code>filename</code> is not null)
   *   Mimetype of attached file. E.g. <code>application/pdf</code>
   * @param fileData
   *   (required if <code>filename</code> is not null)
   *   Byte array of attached files content.
   * @return
   *   ENAIO locationId of the newly created document
   */
  int insertDocument(String objectTypeId, Map<String, String> enaioFields, String locationId,
                     String filename, String mimetype, byte[] fileData) {
    // API reference https://help.optimal-systems.com/enaio_develop/pages/viewpage.action?pageId=1867855#DocumentService(/documents)-/osrest/api/documents/insert/[locationId]4

    // Parameter validation
    if (filename || mimetype || fileData) {
      assert filename != null: "EnaioClient was configured to upload a file, but filename was null."
      assert mimetype != null: "EnaioClient was configured to upload a file, but mimetype was null."
      assert fileData != null: "EnaioClient was configured to upload a file, but fileData was null."
      assert fileData.size() > 0: "EnaioClient was configured to upload a file, but fileData has no content."
      assert !filename.contains("\""): "Filename must not contain '\"' characters but was '$filename'"
      assert !filename.contains("\n"): "Filename must not contain newlines but was '$filename'."
      assert !mimetype.contains("\n"): "Mime type can not contain newlines but was '$mimetype'."

      assert !fileData.toList().contains(formDataBoundary.getBytes("UTF-8").toList()):
              "EnaioClient's 'fileData' contains the byte sequence of '$formDataBoundary', a reserved String that must " +
                      "not appear in the HTTP payload to ENAIO."
    }

    // Setup URL
    URL url
    if (locationId == null) {
      url = new URL(baseUrl + "documents/insert")
    } else {
      url = new URL(baseUrl + "documents/insert/${URLEncoder.encode(locationId, "UTF-8")}")
    }

    // Setup proxy
    HttpURLConnection conn
    if (useProxy) {
      Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ServiceportalInformationGetter.proxyConfigForThisInstance.host, ServiceportalInformationGetter.proxyConfigForThisInstance.port))
      conn = url.openConnection(proxy) as HttpURLConnection
    } else {
      conn = url.openConnection() as HttpURLConnection
    }

    // Setup authorization
    if (username != null) {
      String authHeaderValue = "Basic " + (username + ":" + password).bytes.encodeBase64()
      conn.setRequestProperty("Authorization", authHeaderValue)
    }

    // Setup general connection settings
    conn.setRequestMethod("POST")
    conn.setDoOutput(true) // Allow writing TO the server
    conn.setDoInput(true) // Allow reading FROM the server

    // Setup payload
    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$formDataBoundary")
    ByteArrayOutputStream body = new ByteArrayOutputStream()
    body.write(("--" + formDataBoundary + "\r\n").getBytes("UTF-8"))
    // Yep, these additional dashes before the boundary are on purpose! See RFC 7578
    body.write(("Content-Disposition: form-data; name=\"data\"\r\n").getBytes("UTF-8"))
    body.write(("\r\n").getBytes("UTF-8"))
    body.write((createJsonPayload(objectTypeId, enaioFields) + "\r\n").getBytes("UTF-8"))
    if (filename) {
      // filename is set, we should upload something!
      body.write(("--" + formDataBoundary + "\r\n").getBytes("UTF-8"))
      body.write(("Content-Disposition: form-data; name=\"path\"; filename=\"$filename\"" + "\r\n").getBytes("UTF-8"))
      body.write(("Content-Type: $mimetype" + "\r\n").getBytes("UTF-8"))
      body.write(("\r\n").getBytes("UTF-8"))
      body.write(fileData)
      body.write(("\r\n").getBytes("UTF-8"))
    }
    body.write(("--" + formDataBoundary).getBytes("UTF-8"))
    body.write(("--").getBytes("UTF-8")) // There is a final trailing "--"

    // Send request
    conn.outputStream.write(body.toByteArray())
    conn.outputStream.flush()
    conn.outputStream.close()

    // Handle answer
    int responseCode = conn.getResponseCode()
    if (responseCode == 200) {
      String answer = conn.inputStream.text
      ServiceportalLogger.log("[EnaioClient] Document successfully inserted. Endpoint answer is '$answer'")
      return Integer.parseInt(answer)
    } else {
      throw new Exception("Could not create new ENAIO document.\n" +
              "HTTP reponse code is '$responseCode'.\n" +
              "HTTP response message is >>> \n" +
              "${conn.getErrorStream().text}" +
              "\n<<<")
    }
  }

  /**
   * Adds a new document to the configured ENAIO system without uploading an attached file.
   * See {@link EnaioClient#insertDocument}
   */
  int insertDocument(String objectTypeId, Map<String, String> enaioFields, String locationId) {
    insertDocument(objectTypeId, enaioFields, locationId, null, null, null)
  }

  private static String createJsonPayload(String objectTypeId, Map<String, String> fields) {
    Map result = [:]
    result.objectTypeId = objectTypeId
    result.fields = [:]
    fields.each { sourceMap ->
      // each entry in the json 'fields' sub-object should include ANOTHER sub-object with a 'value' attribute
      // Example:
      //   "fields": {
      //     "FileTitle": {
      //       "value": "[Jahr des Antrags][SPACE]-[SPACE][Veranstaltungsname]"
      //     },
      //   }
      result.fields.put(sourceMap.key, [value: sourceMap.value])
    }

    // Custom JsonGenerator to handle unicode characters correctly
    JsonGenerator jsonGenerator = new JsonGenerator.Options()
            .disableUnicodeEscaping()
            .build()
    String json = jsonGenerator.toJson(result)

    // prettify json - because this is suddenly necessary for the ENAIO API
    json = JsonOutput.prettyPrint(json)

    assert !json.contains(formDataBoundary): "Payload contains boundary String '$formDataBoundary', but this String " +
            "is reserved. (It's super unlikely that a user would pick such a String by themself. So there is " +
            "probably a internal problem within EnaioClient)"

    return json
  }
}
