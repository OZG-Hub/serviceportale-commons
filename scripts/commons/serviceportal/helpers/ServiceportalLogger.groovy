package commons.serviceportal.helpers

import commons.serviceportal.ServiceportalInformationGetter
import org.activiti.engine.delegate.DelegateExecution
import org.activiti.engine.impl.context.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Since all log messages in the serviceportal need to follow certain guidelines, this helper class
 * ensures they are always followed.
 *
 * Log messages can be read in the "admincenter": https://{baseURL}/admincenter/#!prozesslogs
 */
class ServiceportalLogger {

  /**
   * Use this severity for general log messages.
   *
   * Logs a message in the "INFO" severity. In the context of the "Serviceportal" this is the
   * correct level for all general log messages.
   *
   * @param msg The message to log
   */
  static void log(String msg) {
    getLogger().info(msg)
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
   */
  static void logDebug(String msg) {
    getLogger().info("[DEBUG] $msg")
  }

  /**
   * Use this severity for warning messages - i.e. for processes that can continue to run but might
   * include invalid / faulty data that was fixed automatically.
   *
   * @param msg The message to log
   */
  static void logWarn(String msg) {
    getLogger().warn(msg)
  }

  /**
   * Logging personal data is not allowed on productive environments as per certification criteria. However, during
   * development, logging something that might contain personal data can be very helpful. This method will only print
   * logs when run in a development environment (and logs a replacement string when run in another environment).
   *
   * @param msg The message to log
   */
  static void logPersonalDataSecurely(String msg) {
    boolean canLogPersonalData = false // To begin, assume we must not log personal data

    if (ServiceportalInformationGetter.developmentInstances.contains(ServiceportalInformationGetter.thisInstance)) {
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
   *
   * Note that this does not actually log on the "error" level, as it is not acceptable for process
   * certification. Instead this method logs on the "warn" level and prepends "[ERROR] " to the
   * message.
   *
   * @param msg The message to log
   */
  @Deprecated()
  static void logError(String msg) {
    getLogger().warn("[ERROR] $msg")
  }

  private static Logger getLogger() {
    String nameOfProcess
    try {
      DelegateExecution execution = Context?.getExecutionContext()?.getExecution()
      nameOfProcess = execution?.getProcessDefinitionId()
      // usually something like "m6000357.debugstadtMusterprozess:1:432568"
    } catch (EmptyStackException ignored) {
      // Expected when run in a test suite / IDE (i.e. when NOT in the context of a "serviceportal" instance)
      nameOfProcess = "DOES_NOT_MATTER"
    }
    assert nameOfProcess != null: "Failed to determine name of process."
    assert nameOfProcess != "": "Failed to determine name of process."

    Logger logger = LoggerFactory.getLogger("de.seitenbau.serviceportal.prozess.$nameOfProcess".toString())
    return logger
  }
}
