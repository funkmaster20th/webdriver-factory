/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.webdriver

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.edge.EdgeOptions
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities, LocalFileDetector, RemoteWebDriver}
import org.openqa.selenium.{MutableCapabilities, Proxy, WebDriver}

import java.net.URL
import scala.collection.JavaConverters._

class BrowserFactory extends LazyLogging {

  private val defaultSeleniumHubUrl: String                        = "http://localhost:4444/wd/hub"
  private val defaultZapHost: String                               = "localhost:11000"
  protected val zapHostInEnv: Option[String]                       = sys.env.get("ZAP_HOST")
  lazy val zapProxyInEnv: Option[String]                           = sys.props.get("zap.proxy")
  lazy val accessibilityTest: Boolean                              =
    sys.env.getOrElse("ACCESSIBILITY_TEST", sys.props.getOrElse("accessibility.test", "false")).toBoolean
  lazy val disableJavaScript: Boolean                              =
    sys.props.getOrElse("disable.javascript", "false").toBoolean
  private val enableProxyForLocalhostRequestsInChrome: String      = "<-loopback>"
  private val enableProxyForLocalhostRequestsInFirefox: String     = "network.proxy.allow_hijacking_localhost"
  private[webdriver] val accessibilityInHeadlessChromeNotSupported =
    "Headless Chrome not supported with accessibility-assessment tests."

  /*
   * Returns a specific WebDriver instance based on the value of the browserType String and the customOptions passed to the
   * function.  An exception is thrown if the browserType string value is not set or not recognised.  If customOptions are
   * passed to this function they will override the default settings in this library.
   */
  def createBrowser(browserType: Option[String], customOptions: Option[MutableCapabilities]): WebDriver =
    browserType match {
      case Some("chrome")          => chromeInstance(chromeOptions(customOptions))
      case Some("firefox")         => firefoxInstance(firefoxOptions(customOptions))
      case Some("remote-chrome")   => remoteWebdriverInstance(chromeOptions(customOptions))
      case Some("remote-firefox")  => remoteWebdriverInstance(firefoxOptions(customOptions))
      case Some("remote-edge")     => remoteWebdriverInstance(edgeOptions(customOptions))
      case Some("browserstack")    => browserStackInstance()
      case Some("headless-chrome") => headlessChromeInstance(chromeOptions(customOptions))
      case Some(browser)           =>
        throw BrowserCreationException(
          s"'browser' property '$browser' not supported by " +
            s"the webdriver-factory library."
        )
      case None                    =>
        throw BrowserCreationException("'browser' property is not set, this is required to instantiate a Browser")
    }

  private def chromeInstance(options: ChromeOptions): WebDriver =
    new ChromeDriver(options)

  private def headlessChromeInstance(options: ChromeOptions): WebDriver = {
    if (accessibilityTest)
      throw AccessibilityAuditConfigurationException(
        accessibilityInHeadlessChromeNotSupported
      )
    options.addArguments("headless")
    new ChromeDriver(options)
  }

  /*
   * Silences Firefox's logging when running locally with driver binary.  Ensure that the browser starts maximised.
   */
  private def firefoxInstance(options: FirefoxOptions): WebDriver = {
    System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null")
    val driver = new FirefoxDriver(options)
    driver.manage().window().maximize()
    driver
  }

  private def remoteWebdriverInstance(options: MutableCapabilities): WebDriver = {
    val driver: RemoteWebDriver = new RemoteWebDriver(new URL(defaultSeleniumHubUrl), options)
    driver.setFileDetector(new LocalFileDetector)
    driver
  }

  private[webdriver] def chromeOptions(customOptions: Option[MutableCapabilities]): ChromeOptions =
    customOptions match {
      case Some(options) =>
        val userOptions = options.asInstanceOf[ChromeOptions]
        if (accessibilityTest)
          addPageCaptureChromeExtension(userOptions)
        zapConfiguration(userOptions)
        userOptions
      case None          =>
        val defaultOptions = new ChromeOptions()
        zapConfiguration(defaultOptions)
        if (accessibilityTest)
          addPageCaptureChromeExtension(defaultOptions)
        defaultOptions.addArguments("start-maximized")
        // `--use-cmd-decoder` and `--use-gl` are added as a workaround for slow test duration in chrome 85 and higher (PBD-822)
        // Can be reverted once the issue is fixed in the future versions of Chrome.
        defaultOptions.addArguments("--use-cmd-decoder=validating")
        defaultOptions.addArguments("--use-gl=desktop")
        defaultOptions.addArguments("--disable-web-security")

        defaultOptions.setExperimentalOption("excludeSwitches", List("enable-automation").asJava)
        if (disableJavaScript) {
          defaultOptions.setExperimentalOption(
            "prefs",
            Map[String, Int]("profile.managed_default_content_settings.javascript" -> 2).asJava
          )
          logger.info(s"'javascript.enabled' system property is set to:$disableJavaScript. Disabling JavaScript.")
        }
        defaultOptions
    }

  private[webdriver] def firefoxOptions(customOptions: Option[MutableCapabilities]): FirefoxOptions = {
    if (accessibilityTest)
      throw AccessibilityAuditConfigurationException(
        s"Failed to configure Firefox browser to run accessibility-assessment tests." +
          s" The accessibility-assessment can only be configured to run with Chrome."
      )
    customOptions match {
      case Some(options) =>
        val userOptions = options.asInstanceOf[FirefoxOptions]
        zapConfiguration(userOptions)
        userOptions
      case None          =>
        val defaultOptions = new FirefoxOptions()
        defaultOptions.setAcceptInsecureCerts(true)
        defaultOptions.addPreference(enableProxyForLocalhostRequestsInFirefox, true)
        zapConfiguration(defaultOptions)
        if (disableJavaScript) {
          defaultOptions.addPreference("javascript.enabled", false)
          logger.info(s"'javascript.enabled' system property is set to:$disableJavaScript. Disabling JavaScript.")
        }
        defaultOptions
    }
  }

  private[webdriver] def edgeOptions(customOptions: Option[MutableCapabilities]): EdgeOptions = {
    if (accessibilityTest)
      throw AccessibilityAuditConfigurationException(
        s"Failed to configure Edge browser to run accessibility-assessment tests." +
          s" The accessibility-assessment can only be configured to run with Chrome."
      )
    customOptions match {
      case Some(options) =>
        val userOptions = options.asInstanceOf[EdgeOptions]
        zapConfiguration(userOptions)
        userOptions
      case None          =>
        val defaultOptions = new EdgeOptions()
        zapConfiguration(defaultOptions)
        defaultOptions
    }
  }

  /**
    * Configures ZAP proxy settings in the provided browser options.
    * The configuration is set when:
    *  - the environment property ZAP_HOST is set (or)
    *  - the system property `zap.proxy` is set to true
    * @param options accepts a MutableCapabilities object to configure ZAP proxy.
    * @throws ZapConfigurationException when ZAP_HOST is not of the format localhost:port
    */
  private def zapConfiguration(options: MutableCapabilities): Unit = {
    (zapHostInEnv, zapProxyInEnv) match {
      case (Some(zapHost), _)   =>
        val hostPattern = "(localhost:[0-9]+)".r
        zapHost match {
          case hostPattern(host) => setZapHost(host)
          case _                 =>
            throw ZapConfigurationException(
              s" Failed to configure browser with ZAP proxy." +
                s" Environment variable ZAP_HOST is not of the format localhost:portNumber."
            )
        }
      case (None, Some("true")) => setZapHost(defaultZapHost)
      case _                    => ()
    }

    def setZapHost(host: String): Unit = {
      //Chrome does not allow proxying to localhost by default. This allows chrome to proxy requests to localhost.
      val noProxy: String =
        if (options.getBrowserName.equalsIgnoreCase("chrome"))
          enableProxyForLocalhostRequestsInChrome
        else ""
      options.setCapability(CapabilityType.PROXY, new Proxy().setHttpProxy(host).setSslProxy(host).setNoProxy(noProxy))
      options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true)
      logger.info(s"Zap Configuration Enabled: using $host ")
    }
  }

  /**
    * Configures page-capture-chrome-extension for the accessibility assessment in the provided browser options.
    * The configuration in set when:
    *  - the browser being used is chrome (and)
    *  - the Chrome Options does not contain Headless
    *  - the system property `accessibility.test` is set to true (or)
    *  - the environment property `ACCESSIBILITY_TEST` is set to true
    * @param options accepts a ChromeOptions object to add the page-capture-chrome-extension.
    * The encoded string can be generated by using the `encodeExtension.sh` script within the `remote-webdriver-proxy-scripts` repository
    */
  private def addPageCaptureChromeExtension(options: ChromeOptions): Unit = {
    if (options.asMap().get("goog:chromeOptions").toString.contains("headless"))
      throw AccessibilityAuditConfigurationException(
        accessibilityInHeadlessChromeNotSupported
      )

    options.addEncodedExtensions(
      "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"
    )
    logger.info(s"Configured ${options.getBrowserName} browser to run accessibility-assessment tests.")
  }

  /*
   * The tests can be ran using browserstack with different capabilities passed from the command line. e.g -Dbrowserstack.version="firefox" and this allows a flexibility to test using different configs to override the default browserstack values.
   * An exception will be thrown if username or key is not passed.
   */
  def browserStackInstance(): WebDriver = {
    val username           = sys.props.getOrElse(
      "browserstack.username",
      throw new Exception("browserstack.username is required. Enter a valid username")
    )
    val automateKey        =
      sys.props.getOrElse("browserstack.key", throw new Exception("browserstack.key is required. Enter a valid key"))
    val browserStackHubUrl = s"http://$username:$automateKey@hub.browserstack.com/wd/hub"

    val desiredCaps = new DesiredCapabilities()
    desiredCaps.setCapability("browserstack.debug", "true")
    desiredCaps.setCapability("browserstack.local", "true")

    val properties: Map[String, String] =
      sys.props.toMap[String, String].filter(key => key._1.startsWith("browserstack") && key._2 != "")

    properties.map(x => (x._1.replace("browserstack.", ""), x._2.replace("_", " ")))
    properties
      .foreach(x => desiredCaps.setCapability(x._1.replace("browserstack.", ""), x._2.replace("_", " ")))

    new RemoteWebDriver(new URL(browserStackHubUrl), desiredCaps)
  }
}
case class BrowserCreationException(message: String) extends RuntimeException(message)

case class ZapConfigurationException(message: String) extends RuntimeException(message)

case class AccessibilityAuditConfigurationException(message: String) extends RuntimeException(message)
