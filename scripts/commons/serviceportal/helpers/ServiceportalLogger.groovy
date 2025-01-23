package commons.serviceportal.helpers

import commons.serviceportal.ServiceportalInformationGetter
import de.seitenbau.serviceportal.scripting.api.v1.ScriptingApiV1

/**
 * Since all log messages in the serviceportal need to follow certain guidelines, this helper class
 * ensures they are always followed.
 *
 * Log messages can be read in the "admincenter": https://{baseURL}/admincenter/#!prozesslogs
 */
class ServiceportalLogger {

  private ScriptingApiV1 scriptingApiV1

  /**
   * Creates a new instance of ServiceportalLogger.
   *
   * @param api the scripting API (as read from the automatically set process instance variabel `apiV1`).
   */
  ServiceportalLogger(ScriptingApiV1 scriptingApiV1)
  {
    this.scriptingApiV1 = scriptingApiV1
  }

  /**
   * Use this severity for general log messages.
   *
   * Logs a message in the "INFO" severity. In the context of the "Serviceportal" this is the
   * correct level for all general log messages.
   *
   * @param msg The message to log
   *
   * @deprecated use the api method apiV1.logger.info(msg) instead
   */
  @Deprecated
  void log(String msg) {
    scriptingApiV1.logger.info(msg)
  }

  /**
   * Use this severity for debug messages.
   *
   * Logs a message in the "INFO" severity (because the "serviceportal" log viewer doesn't support
   * lower log severities.
   *
   * Note that a finished process should no longer include any debug messages!
   *
   * @param msg The message to log
   *
   * @deprecated use the api method apiV1.logger.info("[DEBUG] " + msg) instead
   */
  @Deprecated
  void logDebug(String msg) {
    scriptingApiV1.logger.info("[DEBUG] $msg")
  }

  /**
   * Use this severity for warning messages - i.e. for processes that can continue to run but might
   * include invalid / faulty data that was fixed automatically.
   *
   * @param msg The message to log
   *
   * @deprecated use the api method apiV1.logger.warn(msg) instead
   */
  @Deprecated
  void logWarn(String msg) {
    scriptingApiV1.logger.warn(msg)
  }

  /**
   * Logging personal data is not allowed on productive environments as per certification criteria. However, during
   * development, logging something that might contain personal data can be very helpful. This method will only print
   * logs when run in a development environment (and logs a replacement string when run in another environment).
   *
   * @param msg The message to log
   * @param scriptingApiVi The scripting API object for information about host
   */
  void logPersonalDataSecurely(String msg) {
    boolean canLogPersonalData = false // To begin, assume we must not log personal data

    if (ServiceportalInformationGetter.developmentInstances.contains(ServiceportalInformationGetter.getThisInstance(scriptingApiV1))) {
      canLogPersonalData = true
    }

    if (canLogPersonalData) {
      logDebug("[MIGHT CONTAIN PERSONAL DATA] " + msg)
    } else {
      logDebug("Log statement redacted as it might contain personal data.")
    }
  }

  /**
   * Use this severity for error messages - i.e. for processes that can no longer be run.
   *
   * @deprecated
   * You should prefer throwing an exception as that would stop execution and also shows up in the
   * log viewer
   * Anyway, use the api method apiV1.logger.warn("[ERROR] " + msg) instead
   *
   * Note that this does not actually log on the "error" level, as it is not acceptable for process
   * certification. Instead this method logs on the "warn" level and prepends "[ERROR] " to the
   * message.
   *
   * @param msg The message to log
   */
  @Deprecated
  void logError(String msg) {
    scriptingApiV1.logger.warn("[ERROR] $msg")
  }
}
