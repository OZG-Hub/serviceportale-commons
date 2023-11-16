#!/usr/bin/env groovy

@Grab(group = 'org.ocpsoft.prettytime', module = 'prettytime', version = '5.0.2.Final')

import groovy.json.JsonSlurper
import org.ocpsoft.prettytime.PrettyTime

/*
This tool analyzes the available process models and forms in the current working directory,
determines if they are deployed on the configured OZG-Hub instance and undeploys them so they can
be re-deployed with the existing gradle plugins.
*/

// Check program arguments
if (args.size() != 3) {
  System.err.println("ERROR: This tool requires exactly 3 parameters:\n" +
          "1) The URL of the 'servicegateway'\n" +
          "2) The username to authenticate with\n" +
          "3) The password to authenticate with")
  System.exit(1)
}
String servicegatewayUrl = args[0]
assert servicegatewayUrl != null
assert servicegatewayUrl.startsWith("http://") || servicegatewayUrl.startsWith("https://")
assert !servicegatewayUrl.endsWith("/"): "Servicegateway URL '$servicegatewayUrl' must NOT end with a slash '/' as it will be added by the tool itself."
String username = args[1]
assert username != null && !username.isAllWhitespace()
String password = args[2]
assert password != null && !password.isAllWhitespace()

// Search for all processes in working directory
Set<String> localProcessIds = []
File[] bpmnFiles = new File(System.getProperty("user.dir") + "/models").listFiles(new FilenameFilter() {
  @Override
  boolean accept(File file, String name) {
    return name.endsWith(".bpmn")
  }
})
bpmnFiles.each { file ->
  Node parsedModel = new XmlParser().parse(file)
  String processId = parsedModel.process.@id.first()
  assert processId != null && !processId.allWhitespace: "Failed to read process id from file '${file.path}'"
  localProcessIds.add(processId)
}

// Search for all forms in working directory
Set<String> localFormIds = []
File[] formFiles = new File(System.getProperty("user.dir") + "/forms").listFiles(new FilenameFilter() {
  @Override
  boolean accept(File file, String name) {
    return name.endsWith(".json")
  }
})
formFiles.each { file ->
  def parsedForm = new JsonSlurper().parse(file)
  String formId = parsedForm."id"
  assert formId != null && !formId.allWhitespace: "Failed to read form id from file '${file.path}'"
  localFormIds.add(formId)
}

// Find out, which processes are deployed and offer to undeploy them
checkProcesses(servicegatewayUrl, username, password, localProcessIds)

// Find out, which forms are deployed and offer to undeploy them
checkForms(servicegatewayUrl, username, password, localFormIds)


private void checkProcesses(String servicegatewayUrl, String username, String password, Collection<String> localProcessIds) {
  String fullPath = servicegatewayUrl + "/prozess/ozghub/list"
  HttpURLConnection connection = new URL(fullPath).openConnection() as HttpURLConnection
  setupAuth(username, password, connection)

  int responseCode = connection.getResponseCode()
  assert responseCode == 200: "Failed to get list of processes. Server status code is '$responseCode'."

  String response = connection.getInputStream().getText()
  def parsed = new JsonSlurper().parseText(response)
  assert parsed."complete" == true: "OZG Hub API failed to list all processes. Please check logs of OZG-Hub plattform for more information."
  (parsed."value" as List).each { deployment ->
    String deploymentId = deployment."deploymentId"
    long deploymentTimestamp = deployment."deploymentDate"
    PrettyTime prettyTime = new PrettyTime()
    String deploymentTimeRelative = prettyTime.format(new Date(deploymentTimestamp))

    String deploymentName = deployment."deploymentName"
    List<String> remoteProcessIds = []
    (deployment."processDefinitionKeysAndNames" as Map<String, String>).keySet().each { processKey ->
      remoteProcessIds.add(processKey)
    }

    remoteProcessIds.each { remoteProcessId ->
      if (localProcessIds.contains(remoteProcessId)) {
        String userInput = ""
        while (userInput != "y" && userInput != "n") {
          println "Undeploy deployment '$deploymentName' (ID: '$deploymentId') from $deploymentTimeRelative? This will remove all process instances. (y/n)"
          userInput = System.in.newReader().readLine()
        }

        if (userInput == "y") {
          // Undeploy that process
          List<String> undeployed = undeployProcess(servicegatewayUrl, username, password, deploymentId)
          println "✅ Sucessfully undeployed process(es): ${undeployed.join(",")}"
        }
      }
    }
  }
}

private static void checkForms(String servicegatewayUrl, String username, String password, Collection<String> localFormIds) {
  String fullPath = servicegatewayUrl + "/formulare/ozghub/list"
  HttpURLConnection connection = new URL(fullPath).openConnection() as HttpURLConnection
  setupAuth(username, password, connection)

  int responseCode = connection.getResponseCode()
  assert responseCode == 200: "Failed to get list of forms. Server status code is '$responseCode'."

  String response = connection.getInputStream().getText()
  def parsed = new JsonSlurper().parseText(response)
  assert parsed."deploymentList" instanceof List: "OZG Hub API failed to list all forms. Please check logs of OZG-Hub plattform for more information."

  (parsed."deploymentList").each { deployment ->
    String deploymentId = deployment."deploymentId"
    String deployedFormKey = deployment."mandantId" + ":" + deployment."formName" + ":" + deployment."formVersion"
    long deploymentTimestamp = deployment."deploymentDate"
    PrettyTime prettyTime = new PrettyTime()
    String deploymentTimeRelative = prettyTime.format(new Date(deploymentTimestamp))

    if (localFormIds.contains(deployedFormKey)) {
      String userInput = ""
      while (userInput != "y" && userInput != "n") {
        println "Undeploy form '$deployedFormKey' (deployment ID '$deploymentId') from $deploymentTimeRelative? (y/n)"
        userInput = System.in.newReader().readLine()
      }

      if (userInput == "y") {
        // Undeploy that form
        String undeployedForm = undeployForm(servicegatewayUrl, username, password, deploymentId)
        println "✅ Sucessfully undeployed form: ${undeployedForm}"
      }
    }

  }
}

private static void setupAuth(String username, String password, HttpURLConnection connection) {
  String userPass = username + ":" + password
  String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPass.getBytes()))
  connection.setRequestProperty("Authorization", basicAuth)
}

private static List<String> undeployProcess(String servicegatewayUrl, String username, String password, String deploymentId) {
  String fullPath = servicegatewayUrl + "/prozess/ozghub/undeploy"
  HttpURLConnection connection = new URL(fullPath).openConnection() as HttpURLConnection
  setupAuth(username, password, connection)

  connection.setRequestMethod("DELETE")

  connection.setRequestProperty("X-OZG-Deployment-ID", deploymentId)
  connection.setRequestProperty("X-OZG-Deployment-DeleteProcessInstances", "true")

  int responseCode = connection.getResponseCode()
  assert responseCode == 200: "Failed to undeploy deployment '$deploymentId'. Server status code is '$responseCode'."

  String response = connection.getInputStream().getText()
  def parsed = new JsonSlurper().parseText(response)
  assert parsed."processKeys" instanceof List<String>

  return parsed."processKeys"
}

private static String undeployForm(String servicegatewayUrl, String username, String password, String deploymentId) {
  String fullPath = servicegatewayUrl + "/formulare/ozghub/undeploy"
  HttpURLConnection connection = new URL(fullPath).openConnection() as HttpURLConnection
  setupAuth(username, password, connection)

  connection.setRequestMethod("DELETE")

  connection.setRequestProperty("X-OZG-Deployment-ID", deploymentId)

  int responseCode = connection.getResponseCode()
  assert responseCode == 200: "Failed to undeploy deployment '$deploymentId'. Server status code is '$responseCode'."

  String response = connection.getInputStream().getText()
  def parsed = new JsonSlurper().parseText(response)

  return parsed."id"
}
