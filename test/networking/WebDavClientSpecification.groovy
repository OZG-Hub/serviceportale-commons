package networking

import commons.serviceportal.networking.WebDavClient
import spock.lang.Ignore
import spock.lang.Specification

class WebDavClientSpecification extends Specification {
  String webDavHost = "No unit test available as of 2023-04-14"
  String webDavUsername = "No unit test available as of 2023-04-14"
  String webDavPassword = "No unit test available as of 2023-04-14"
  boolean useProxy = false
  WebDavClient client = new WebDavClient(webDavHost, webDavUsername, webDavPassword, false)

  @Ignore
  // The sloppy.zone server is no longer available, therefore this test is currently disabled. There are currently no
  // updates for WebDavClient planned, so having not unit tests is acceptable. If we ever need to update WebDavClient,
  // we should probably mock our own server during this unit test.
  def "Transfer a json file to WebDav"() {
    given:
    String path = "/test.json"
    byte[] data = """
      {
        "test": "test"
      }
      """.stripIndent().getBytes("UTF-8")
    String mimetype = "application/json"

    when:
    client.uploadFile(path, data, mimetype)

    then:
    noExceptionThrown()
  }

}
