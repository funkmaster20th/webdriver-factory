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

import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.edge.EdgeOptions
import org.openqa.selenium.firefox.FirefoxOptions
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BrowserFactorySpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  trait Setup {
    val browserFactory: BrowserFactory = new BrowserFactory()
  }

  override def beforeEach(): Unit = {
    System.clearProperty("zap.proxy")
    System.clearProperty("javascript")
    System.clearProperty("accessibility.test")
    System.clearProperty("disable.javascript")
  }

  override def afterEach(): Unit = {
    System.clearProperty("zap.proxy")
    System.clearProperty("javascript")
    System.clearProperty("accessibility.test")
    System.clearProperty("disable.javascript")
    SingletonDriver.closeInstance()
  }

  "BrowserFactory" should {

    "return chromeOptions with default options" in new Setup {
      val options: ChromeOptions = browserFactory.chromeOptions(None)
      options.asMap().get("browserName")          shouldBe "chrome"
      options
        .asMap()
        .get("goog:chromeOptions")
        .toString                                 shouldBe "{args=[start-maximized, --use-cmd-decoder=validating, --use-gl=desktop], excludeSwitches=[enable-automation], extensions=[]}"
      options.asMap().getOrDefault("proxy", None) shouldBe None
    }

    "return chromeOptions with zap proxy configuration when zap.proxy is true" in new Setup {
      System.setProperty("zap.proxy", "true")
      val options: ChromeOptions = browserFactory.chromeOptions(None)
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return chromeOptions with zap proxy configuration when ZAP_HOST environment variable is set" in new Setup {
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:11000")
      }

      val options: ChromeOptions = browserFactory.chromeOptions(None)
      //This check ensures zap configuration is not setup because of zap.proxy system property.
      sys.props.get("zap.proxy").isDefined  shouldBe false
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return userBrowserOptions for Chrome" in new Setup {
      val customOptions = new ChromeOptions()
      customOptions.addArguments("headless")

      val options: ChromeOptions = browserFactory.chromeOptions(Some(customOptions))
      options.asMap().get("browserName")                 shouldBe "chrome"
      options.asMap().get("goog:chromeOptions").toString shouldBe "{args=[headless], extensions=[]}"
      options.asMap().getOrDefault("proxy", None)        shouldBe None
    }

    "return userBrowserOptions with zap proxy for Chrome" in new Setup {
      System.setProperty("zap.proxy", "true")
      val customOptions = new ChromeOptions()
      customOptions.addArguments("headless")

      val options: ChromeOptions = browserFactory.chromeOptions(Some(customOptions))
      options.asMap().get("browserName")                 shouldBe "chrome"
      options.asMap().get("goog:chromeOptions").toString shouldBe "{args=[headless], extensions=[]}"
      options.asMap().get("proxy").toString              shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return userBrowserOptions with zap proxy for Chrome when ZAP_HOST environment variable is set" in new Setup {
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:11000")
      }
      val customOptions                           = new ChromeOptions()
      customOptions.addArguments("headless")

      //This check ensures zap configuration is not setup because of zap.proxy system property.
      sys.props.get("zap.proxy").isDefined shouldBe false
      val options: ChromeOptions = browserFactory.chromeOptions(Some(customOptions))
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return chromeOptions with javascript disabled when disable.javascript is true" in new Setup {
      System.setProperty("disable.javascript", "true")
      val options: ChromeOptions = browserFactory.chromeOptions(None)
      options
        .asMap()
        .get("goog:chromeOptions")
        .toString should include("prefs={profile.managed_default_content_settings.javascript=2}")
    }

    "return firefoxOptions" in new Setup {
      val options: FirefoxOptions = browserFactory.firefoxOptions(None)
      options.asMap().get("browserName") shouldBe "firefox"
    }

    "return firefoxOptions with zap proxy configuration when zap.proxy is true " in new Setup {
      System.setProperty("zap.proxy", "true")
      val options: FirefoxOptions = browserFactory.firefoxOptions(None)
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return firefoxOptions with zap proxy configuration when ZAP_HOST environment variable is set" in new Setup {
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:11000")
      }
      //This check ensures zap configuration is not setup because of zap.proxy system property.
      sys.props.get("zap.proxy").isDefined shouldBe false
      val options: FirefoxOptions                 = browserFactory.firefoxOptions(None)
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return userBrowserOptions for firefox" in new Setup {
      val customOptions = new FirefoxOptions()
      customOptions.setHeadless(true)

      val options: FirefoxOptions = browserFactory.firefoxOptions(Some(customOptions))
      options.asMap().get("moz:firefoxOptions").toString shouldBe "{args=[-headless]}"
      options.asMap().getOrDefault("proxy", None)        shouldBe None
    }

    "return userBrowserOptions with zap proxy for firefox" in new Setup {
      System.setProperty("zap.proxy", "true")

      val customOptions = new FirefoxOptions()
      customOptions.setHeadless(true)

      val options: FirefoxOptions = browserFactory.firefoxOptions(Some(customOptions))
      options.asMap().get("browserName")                 shouldBe "firefox"
      options.asMap().get("moz:firefoxOptions").toString shouldBe "{args=[-headless]}"
      options.asMap().get("proxy").toString              shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return userBrowserOptions with zap proxy for firefox when ZAP_HOST environment variable is set" in new Setup {
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:11000")
      }

      val customOptions = new FirefoxOptions()
      customOptions.setHeadless(true)

      //This check ensures zap configuration is not setup because of zap.proxy system property.
      sys.props.get("zap.proxy").isDefined shouldBe false
      val options: FirefoxOptions = browserFactory.firefoxOptions(Some(customOptions))
      options.asMap().get("browserName")                 shouldBe "firefox"
      options.asMap().get("moz:firefoxOptions").toString shouldBe "{args=[-headless]}"
      options.asMap().get("proxy").toString              shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "configure browser with ZAP_HOST when both ZAP_HOST and zap.proxy  is set" in new Setup {
      System.setProperty("zap.proxy", "true")
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:1234")
      }

      val options: ChromeOptions = browserFactory.chromeOptions(None)
      sys.props.get("zap.proxy")            shouldBe Some("true")
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:1234, ssl=localhost:1234)"
    }

    "throw an exception when ZAP_HOST environment is not of the format 'localhost:port'" in new Setup {
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:abcd")
      }
      intercept[ZapConfigurationException] {
        browserFactory.chromeOptions(None)
      }
    }

    "return chromeOptions with accessibility extension configuration when system property accessibility.test true" in new Setup {
      System.setProperty("accessibility.test", "true")
      val options: ChromeOptions = browserFactory.chromeOptions(None)

      options
        .asMap()
        .get("goog:chromeOptions")
        .toString shouldBe "{args=[start-maximized, --use-cmd-decoder=validating, --use-gl=desktop], excludeSwitches=[enable-automation], extensions=[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]}"
    }

    "return chromeOptions without accessibility extension configuration when system property accessibility.test false and system env ACCESSIBILITY_TEST false" in new Setup {
      val options: ChromeOptions = browserFactory.chromeOptions(None)

      options
        .asMap()
        .get("goog:chromeOptions")
        .toString shouldBe "{args=[start-maximized, --use-cmd-decoder=validating, --use-gl=desktop], excludeSwitches=[enable-automation], extensions=[]}"
    }

    "return error when using firefoxOptions and when configuring system property accessibility.test true" in new Setup {
      val thrown: Exception = intercept[Exception] {
        System.setProperty("accessibility.test", "true")
        browserFactory.firefoxOptions(None)
      }
      assert(
        thrown.getMessage === s"Failed to configure Firefox browser to run accessibility-assessment tests." +
          s" The accessibility-assessment can only be configured to run with Chrome."
      )
    }

    "return firefoxOptions with javascript disabled configuration when disable.javascript is true " in new Setup {
      System.setProperty("disable.javascript", "true")
      val options: FirefoxOptions = browserFactory.firefoxOptions(None)
      println(options.asMap())
      options.asMap().get("moz:firefoxOptions").toString should include("javascript.enabled=false")
    }

    "return error when using chromeOptions headless and when configuring system property accessibility.test true" in new Setup {
      val thrown: Exception = intercept[Exception] {
        System.setProperty("accessibility.test", "true")
        val customOptions = new ChromeOptions()
        customOptions.setHeadless(true)
        browserFactory.chromeOptions(Some(customOptions))
      }
      assert(
        thrown.getMessage === browserFactory.accessibilityInHeadlessChromeNotSupported
      )
    }

    "return error when browser type is headless-chrome and when configuring system property accessibility.test true" in new Setup {
      val thrown: Exception = intercept[Exception] {
        System.setProperty("accessibility.test", "true")
        browserFactory.createBrowser(Some("headless-chrome"), None)
      }
      assert(
        thrown.getMessage === browserFactory.accessibilityInHeadlessChromeNotSupported
      )
    }
    "return edgeOptions" in new Setup {
      val options: EdgeOptions = browserFactory.edgeOptions(None)
      options.asMap().get("browserName") shouldBe "MicrosoftEdge"
    }

    "return edgeOptions with zap proxy configuration when zap.proxy is true " in new Setup {
      System.setProperty("zap.proxy", "true")
      val options: EdgeOptions = browserFactory.edgeOptions(None)
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return edgeOptions with zap proxy configuration when ZAP_HOST environment variable is set" in new Setup {
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:11000")
      }
      //This check ensures zap configuration is not setup because of zap.proxy system property.
      sys.props.get("zap.proxy").isDefined shouldBe false
      val options: EdgeOptions                    = browserFactory.edgeOptions(None)
      options.asMap().get("proxy").toString shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return userBrowserOptions for edge" in new Setup {
      val customOptions = new EdgeOptions()
      customOptions.setHeadless(true)

      val options: EdgeOptions = browserFactory.edgeOptions(Some(customOptions))
      options.asMap().get("ms:edgeOptions").toString shouldBe "{args=[--headless], extensions=[]}"
      options.asMap().getOrDefault("proxy", None)    shouldBe None
    }

    "return userBrowserOptions with zap proxy for edge" in new Setup {
      System.setProperty("zap.proxy", "true")

      val customOptions = new EdgeOptions()
      customOptions.setHeadless(true)

      val options: EdgeOptions = browserFactory.edgeOptions(Some(customOptions))
      options.asMap().get("browserName")             shouldBe "MicrosoftEdge"
      options.asMap().get("ms:edgeOptions").toString shouldBe "{args=[--headless], extensions=[]}"
      options.asMap().get("proxy").toString          shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return userBrowserOptions with zap proxy for edge when ZAP_HOST environment variable is set" in new Setup {
      override val browserFactory: BrowserFactory = new BrowserFactory() {
        override protected val zapHostInEnv: Option[String] = Some("localhost:11000")
      }

      val customOptions = new EdgeOptions()
      customOptions.setHeadless(true)

      //This check ensures zap configuration is not setup because of zap.proxy system property.
      sys.props.get("zap.proxy").isDefined shouldBe false
      val options: EdgeOptions = browserFactory.edgeOptions(Some(customOptions))
      options.asMap().get("browserName")             shouldBe "MicrosoftEdge"
      options.asMap().get("ms:edgeOptions").toString shouldBe "{args=[--headless], extensions=[]}"
      options.asMap().get("proxy").toString          shouldBe "Proxy(manual, http=localhost:11000, ssl=localhost:11000)"
    }

    "return error when using edgeOptions and when configuring system property accessibility.test true" in new Setup {
      val thrown: Exception = intercept[Exception] {
        System.setProperty("accessibility.test", "true")
        browserFactory.edgeOptions(None)
      }
      assert(
        thrown.getMessage === s"Failed to configure Edge browser to run accessibility-assessment tests." +
          s" The accessibility-assessment can only be configured to run with Chrome."
      )
    }
  }
}
