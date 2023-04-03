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

  private[webdriver] def chromeOptions(capabilities: Option[MutableCapabilities]): ChromeOptions = {
    var options: ChromeOptions = new ChromeOptions

    options.addArguments("start-maximized")
    options.setExperimentalOption("excludeSwitches", List("enable-automation").asJava)

    if (disableJavaScript) {
      options.setExperimentalOption(
        "prefs",
        Map[String, Int]("profile.managed_default_content_settings.javascript" -> 2).asJava
      )
      logger.info(s"'disable.javascript' system property is set to: $disableJavaScript. Disabling JavaScript.")
    }

    zapConfiguration(options)

    capabilities match {
      case Some(value) =>
        options = options.merge(value)
        if (accessibilityTest)
          addPageCaptureChromeExtension(options)
        options
      case None        =>
        if (accessibilityTest)
          addPageCaptureChromeExtension(options)
        options
    }
  }

  private[webdriver] def firefoxOptions(capabilities: Option[MutableCapabilities]): FirefoxOptions = {
    var options: FirefoxOptions = new FirefoxOptions

    if (accessibilityTest)
      throw AccessibilityAuditConfigurationException(
        s"Failed to configure Firefox browser to run accessibility-assessment tests." +
          s" The accessibility-assessment can only be configured to run with Chrome."
      )

    if (disableJavaScript) {
      options.addPreference("javascript.enabled", false)
      logger.info(s"'javascript.enabled' system property is set to:$disableJavaScript. Disabling JavaScript.")
    }

    zapConfiguration(options)

    capabilities match {
      case Some(value) =>
        options = options.merge(value)
        options
      case None        =>
        options
    }
  }

  private[webdriver] def edgeOptions(capabilities: Option[MutableCapabilities]): EdgeOptions = {
    var options: EdgeOptions = new EdgeOptions

    if (accessibilityTest)
      throw AccessibilityAuditConfigurationException(
        s"Failed to configure Edge browser to run accessibility-assessment tests." +
          s" The accessibility-assessment can only be configured to run with Chrome."
      )

    if (disableJavaScript) {
      options.setExperimentalOption(
        "prefs",
        Map[String, Int]("profile.managed_default_content_settings.javascript" -> 2).asJava
      )
      logger.info(s"'disable.javascript' system property is set to: $disableJavaScript. Disabling JavaScript.")
    }

    zapConfiguration(options)

    capabilities match {
      case Some(value) =>
        options = options.merge(value)
        options
      case None        =>
        options
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
