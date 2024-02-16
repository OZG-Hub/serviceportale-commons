package commons.serviceportal

import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1
import org.activiti.engine.impl.context.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Get various information about the serviceportal instance this process is running in.
 */
class ServiceportalInformationGetter {

  final static Set<SERVICEPORTAL_INSTANCE> developmentInstances = [
          SERVICEPORTAL_INSTANCE.SBW_TEST,
          SERVICEPORTAL_INSTANCE.SBW_DEV,
          SERVICEPORTAL_INSTANCE.AMT24_DEV,
          SERVICEPORTAL_INSTANCE.SERVICE_KOMMUNE,
  ].asImmutable()

  // We must not use ServiceportalLogger here as the would cause a infinite recursion during the build process (as
  // ServiceportalLogger includes ServiceportalInformationGetter already)
  static Logger logger = LoggerFactory.getLogger("de.seitenbau.serviceportal.prozess.serviceportalinformationgetter")

  /**
   * Return an identifier about the serviceportal instance this process is running in.
   *
   * @return
   */
  static SERVICEPORTAL_INSTANCE getThisInstance(ScriptingApiV1 scriptingApiV1) {
    String host = getHost(scriptingApiV1)

    switch (host) {
      case "test.service-bw.de":
        return SERVICEPORTAL_INSTANCE.SBW_TEST
      case "www.service-bw.de":
        return SERVICEPORTAL_INSTANCE.SBW_LIVE
      case "dev.service-bw.de":
        return SERVICEPORTAL_INSTANCE.SBW_DEV
      case "amt24dev.sachsen.de":
        return SERVICEPORTAL_INSTANCE.AMT24_DEV
      case "amt24.sachsen.de":
        return SERVICEPORTAL_INSTANCE.AMT24_LIVE
      case "dev.behoerden-serviceportal.de":
        return SERVICEPORTAL_INSTANCE.OZG_HUB_DEV
      case "dpdev.behoerden-serviceportal.de":
        return SERVICEPORTAL_INSTANCE.OZG_HUB_DATAPORT_DEV
      default:
        logger.warn("Failed to classify host '$host' as serviceportal instance as this value was not known to " +
                "ServiceportalInformationGetter.getThisInstance(). Please extend this method by the new host.")
        return SERVICEPORTAL_INSTANCE.OTHER
    }
  }

  /**
   * @deprecated Every serviceportal instance now offers a proxy and we should just always use the
   * provided proxy for any internet-facing connections.
   *
   * @return
   */
  @Deprecated
  static boolean doesThisInstanceRequireProxyForHttp() {
    return true
  }

  static ProxyConfig getProxyConfigForThisInstance() {
    ProxyConfig result = new ProxyConfig()

    def config = Context.getExecutionContext().getExecution().getVariable("processEngineConfig")
    final String readingErrorMsg = "ServiceportalInformationGetter failed to read " +
            "processEngineConfig. This is not an error in your process but in the " +
            "serviceplattform configuration. Please verify that the ansible build variable " +
            "'processEngineConfig' was parsed correctly when building this instance."

    result.host = config.get("internet.proxy.host")
    assert result.host != null && !result.host.allWhitespace: readingErrorMsg

    def portFromConfig = config.get("internet.proxy.port")
    assert portFromConfig != null: readingErrorMsg
    result.port = portFromConfig as Integer

    return result
  }

  /**
   * Returns the base url of a serviceportal instance, including the protocol to use. The host name is stored in a
   * process instance variable 'processEngineConfig' that is automatically set by the process engine. It might not be
   * available in call activities, in which case we throw an IllegalStateException.
   *
   * @param scriptingApiV1 The ScriptingAPI object for host information
   *
   * @return something like "https://amt24dev.sachsen.de"
   */
  static String getBaseUrl(ScriptingApiV1 scriptingApiV1) throws IllegalStateException {
    final String prefix = "https://" // AFAIK, all instances currently use HTTPS.
    String host = getHost(scriptingApiV1)
    return prefix + host
  }

  /**
   * Returns the host name of the serviceportal instance. he host name is stored in a
   * process instance variable 'processEngineConfig' that is automatically set by the process engine. It might not be
   * available in call activities, in which case we throw an IllegalStateException.
   *
   * @param scriptingApiV1 The ScriptingAPI object for host information
   *
   * @return something like "amt24dev.sachsen.de"
   */
  static String getHost(ScriptingApiV1 scriptingApiV1) {
    ScriptingApiV1 api = scriptingApiV1

    // The host name is stored in a process instance variable 'processEngineConfig' that is automatically set by the
    // process engine. It might not be available in call activities, in which case we throw an exception.
    // See https://doku.pmp.seitenbau.com/display/DFO/Automatisch+gesetzte+Prozessvariablen
    Map<String, Object> processEngineConfig = api.getVariable("processEngineConfig", Map)
    if (processEngineConfig == null) {
      throw new IllegalStateException("ServiceportalInformationGetter failed to determine this instance's host name " +
              "because the process instance variable 'processEngineConfig' was null. Since this variable is supposed " +
              "to be automatically set by the process engine this should never happen, unless " +
              "ServiceportalInformationGetter was called from inside a call activity (in which case the problem might " +
              "be fixed by supplying 'processEngineConfig' as a parameter to that call activity).")
    }
    String host = processEngineConfig.get("serviceportal.environment.main-portal-host")
    assert host != null
    assert !host.isAllWhitespace()
    return host
  }
}

enum SERVICEPORTAL_INSTANCE {
  // SBW = Service BW

  /**
   * I. e. www.service-bw.de
   */
  SBW_LIVE,

  /**
   * I. e. test.service-bw.de
   */
  SBW_TEST,

  /**
   * I. e. dev.service-bw.de
   */
  SBW_DEV,

  /**
   * I. e. amt24dev.sachsen.de
   */
  AMT24_DEV,

  /**
   * I. e. amt24.sachsen.de
   */
  AMT24_LIVE,

  /**
   * I. e. http://service-kommune.sbw.imbw.dev.seitenbau.net/
   */
  SERVICE_KOMMUNE,

  /**
   * I. e. https://www.behoerden-serviceportal.de
   */
  OZG_HUB_DEV,

  /**
   * I. e. https://dpdev.behoerden-serviceportal.de
   */
  OZG_HUB_DATAPORT_DEV,

  /**
   * All other systems, like "mtest-Kisten" used by SEITENBAU
   */
  OTHER
}


class ProxyConfig {
  String host
  int port
}
