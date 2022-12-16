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
