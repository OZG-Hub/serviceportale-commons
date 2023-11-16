package commons.serviceportal.networking

import commons.serviceportal.ServiceportalInformationGetter

class WebDavClient {
  private String webDavBaseUrl
  private String webDavUsername
  private String webDavPassword
  private boolean useProxy

  WebDavClient(String webDavBaseUrl, String webDavUsername, String webDavPassword, boolean useProxy) {
    assert webDavBaseUrl != null
    assert !webDavBaseUrl.isEmpty()
    assert !webDavBaseUrl.isAllWhitespace()

    this.useProxy = useProxy
    this.webDavBaseUrl = webDavBaseUrl
    this.webDavUsername = webDavUsername
    this.webDavPassword = webDavPassword
  }

  /**
   * Uploads a file to the configured server.
   *
   * @param path the path where to upload the file. If neither basePath ends with a slash, nor path
   *   starts with a slash, a slash will be inserted.
   * @param content a byte array of the content that should be uploaded.
   * @param contentType the mime type of the uploaded file. E.g. "application/json"
   */
  void uploadFile(String path, byte[] content, String contentType) {
    String fullPath
    if (webDavBaseUrl.endsWith("/") || path.startsWith("/")) {
      fullPath = webDavBaseUrl + path
    } else {
      fullPath = webDavBaseUrl + "/" + path
    }

    // Setup connection
    URL url = new URL(fullPath)
    HttpURLConnection connection

    // Setup Proxy
    if (useProxy) {
      Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ServiceportalInformationGetter.proxyConfigForThisInstance.host, ServiceportalInformationGetter.proxyConfigForThisInstance.port))
      connection = url.openConnection(proxy) as HttpURLConnection
    } else {
      connection = url.openConnection() as HttpURLConnection
    }

    // Setup other attributes
    connection.setRequestMethod("PUT")
    connection.setDoOutput(true)
    connection.setRequestProperty("Content-Type", contentType)

    // Setup authentication
    if (webDavUsername != null) {
      assert !webDavUsername.isAllWhitespace()
      assert !webDavPassword.isAllWhitespace()

      String userPass = webDavUsername + ":" + webDavPassword
      String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()))
      connection.setRequestProperty("Authorization", basicAuth)
    }

    // Send data
    connection.getOutputStream().write(content)

    // Handle response
    int responseCode = connection.getResponseCode()
    switch (responseCode) {
      case 201:
        // all good. Document was created.
        break
      case 204:
        // all good. Document was updated.
        break
      default:
        throw new RuntimeException("Could not upload file. HTTP Response code is $responseCode, " +
                "answer body is:\n${connection.getErrorStream()?.text}")
    }
  }
}
