/*
 * Copyright 2022 HM Revenue & Customs
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
      "Q3IyNAMAAABFAgAAEqwECqYCMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4XfUUPhzlWcZGeDj1C6MuDygB0a15M4lxGd/Yf+0PdOphWD52v4lOdqn6fSZFeZH+WSZz6S0WNP44nmm6DY9U0NCqQO0qoMWx7weJnLKHNQs1rxkASwVFiy79Dz6FG/hluWCRTyNSXY3+2vVN6c/6/LiRQYfPxC6IEki6aNUSzf17qd2SAhT9xN+wAzIdNgFOHLlJWHMJd8Mq6AzayFWmUNilxQm9DnkXQSXEeqf92i/eS7qdz3mS2WHRkiZJJ71Zk2dwekXI5ty8VMkzX6EUirwiIHzFgHVgF6hyV4Gm5Tq1Law9z1TSRVuRVg7vENHldZHV5qDXbO8OVZYcKeBJQIDAQABEoACy9DlHRfe1qHg8lqte4LB35le4Od1WTen3T9l7r/55ROAEOlEWe0t7gTJ7sL6FCOvml5cVa1NaRK/mxNMEyYZJQEx8z3oH0cOKUb7up9p2YR+baqHxXHY+8X6qA4zRoNOQQZfXSET9omRHWxGRz3VCn2ZGWdB+eU2gCxOSZfRFleKproQFYivKuXr5XBz4PLBINd5R3OLcyCx85zpe/bXzT0SuX67wQgpvHXc+kzKhuTuloamlvA/E+qWxbkk1yo8frMDSUx6Omh+FaKcCzT9MPBrTHGJVsk5KmpA7OCDDea83oAj/vyk44nKLrgLkTmjajKySNh0K6Nz1PBncwtFTYLxBBIKEInIZ790kJPpKytavODiS/hQSwMELQAACAgAA4eMVU7W6NRGAQAAeAIAAA0AFABiYWNrZ3JvdW5kLmpzAQAQAAAAAAAAAAAAAAAAAAAAAACNkcFugzAMhu88RcQFkFroLjswscukHaZt3Ub3AGkwJRNNMseZhCbefaFQSm/LxYnj/P79pXZKkNSKWVDVTpeAP4CxVMZR8hswv2og0cRhQ2TyLGu14G2jLeW3m5tNxo3MBDfkENaGHyBcsfHVsI5Aja5yFr5ty124mvMN8ArQ5otSxqIHrQgUrXedgShnETemlYIP5rIvq1V0Eegv272uupw9ldvX1BJKdZB1N9k/F/XJuEmpARXHCNZoZSFhxT2bD0EfBPWZxQHoEfXxE9vYYZucfCL4IdVEY8jeDU9E4+sgRadI+qjVC1jrOaS8qp6l9QN5mrOu7/3twNLqRBtwjB+zoaGPrOeyVIxISoHS0LsD7FhRFCxa/lWUTBivPvCsMJLw9/0/pS+jz8ILGteyI1DtyB8GmMthpnQysp/gETo4eemTP1BLAwQtAAAICACsZodVd+FzR9UAAACTAQAADQAUAG1hbmlmZXN0Lmpzb24BABAAAAAAAAAAAAAAAAAAAAAAAH1QQW7CMBC88wrLR0QJore8g1sVRZvtQgyJbXmXtAjx966NqC9VfbA0Mzsza99XxtgZvDsSS79QYhe8bc37JgseZlJgAZGY3eAmJ7c3YFY0kxcT4UQGIco1kaFvIV/8xVzD7H67e3KR0uw406z8h1JKCgxcZJObxC10gMEq7opnDLrZn8ZRJLZNMwWEKU+162ZdfRi87iM9Y3JRqu1e7vJswZGq8G9mPt3mZT0X16tiq7Ar0uO3fQC8nFK4+k+dfFZaprQ4pP4rpAul/DF1KmescsDjB1BLAwQtAAAICADKbo1VfJOW+gQDAADgBwAACgAUAGNvbnRlbnQuanMBABAAAAAAAAAAAAAAAAAAAAAAAJ1VbW/TMBD+nl9hJrTYWuuCBB/IVKRJbOLDeFs7IdF2UpZel0BqB9thjNL/zvklabpSsdEPjX332PfcPWc7k0IbskxNlo9UdmLMuczS8gK0rFUGZEgu4Ob0Z0VjepUbUyXTwXRQWkgutUkmz/qvZqsXvZdr9pteTaZ8Opitnq8naf/XSf+LG04HfoLI/pTPjp6ymEXRohaZKaQg6bWWZW3gUpW0ViUjq4iQzJEqC/ENCcxlVi9BGJ4pSA2clmBn9CA9YMeItSieK1ggFDewJgWmVmLjOY7WnYgaxHwsR6B+gKKFqGoTguZKLoGrWpgCvxb2DrROb4Bat2NlMPIoU0VlPtWg7hISd3eLew7n9kz8Bw1rthX+BswZBtrKNxAWcEs+oq/QQFs8VaB7iPjqoQ/guZfrJnRg2mF7X4fgXzfALT4V6gMNH/tD28beLGVt8l7PhVSnaZa/l3PbVxS8kHZ6XmjzYdEjhQGVGqkYGb4mkxnHdVlqKOd8B8x42I62i5owRQkaA6zWwVD5kuqx/JwW5kwqdE5mwQlKSaW9JZh0ns7lbbfzmkHoPZ6VUoDlQo2qwbbzJjXql/Pvtugj5J0ht5OypLHtx4mCcqjNHVLMAcwsZj3iRjZjW88dsryqdU47XePgHA14WFVxjZrR2HZ5zHzluclBUFkb1LXZ1Qm9IK11iGdFzGFRCJh3dfTV8CHjAC70Bstj1tHcNm0jdtMmtvgTv5LbiUiXMMNaBlPoSx3gPhe9m0uPxHwQkyNybyvWJhNy4Y5yOADkyZCIuiz3pfSXJWwrAfuPZVw/VFLtzpcT0Y2aettaewvXKiOHh3tvWG5Amwa7LSquRE2bXPY0RqDf7Y/9e7VF2dcj/yGhkzGkuhPyXzI+TspHyNntyc3Ie929FNmrKYrCdctTVHOnwsyXaXP1eTJbD0gQB0NfXpwn5LYQtlPam8M+lXate4d6Lfbt+B2CQ1thGqCsxfvtra5NuqwS9yC8wTePMivnGB2UeZATKfEfb/GFScI3XL0s+gNQSwECAAAUAAAICAADh4xVTtbo1EYBAAB4AgAADQAAAAAAAAABAAAAAAAAAAAAYmFja2dyb3VuZC5qc1BLAQIAABQAAAgIAKxmh1V34XNH1QAAAJMBAAANAAAAAAAAAAEAAAAAAIUBAABtYW5pZmVzdC5qc29uUEsBAgAAFAAACAgAym6NVXyTlvoEAwAA4AcAAAoAAAAAAAAAAQAAAAAAmQIAAGNvbnRlbnQuanNQSwUGAAAAAAMAAwCuAAAA2QUAAAAA"
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
