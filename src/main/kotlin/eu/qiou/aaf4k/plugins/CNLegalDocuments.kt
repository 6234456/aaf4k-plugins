package eu.qiou.aaf4k.plugins

import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import java.time.LocalDate

class CNLegalDocuments {
    init {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            System.setProperty("webdriver.gecko.driver", "C:\\Users\\qiou\\Documents\\geckodriver.exe")
            System.setProperty("webdriver.firefox.bin", "C:\\Program Files\\Mozilla Firefox\\firefox.exe")
        } else {
            System.setProperty("webdriver.gecko.driver", "/home/yang/Documents/geckodriver")
            System.setProperty("webdriver.firefox.bin", "/usr/bin/firefox")
        }
    }

    companion object {
        var captchaWarning: Boolean = false
    }

    // after x sec waiting for the busy status, re-run the query
    private val patience: Int = 10

    private fun truncateCoreURL(url: String): String {
        //http://wenshu.court.gov.cn/Html_Pages/VisitRemind20180914.html?DocID=ed85e1bb-e584-45ca-8839-01b2000d137e
        if (url.contains("VisitRemind"))
            return "http://wenshu.court.gov.cn/content/content?" + url.drop(url.indexOf("DocID="))

        val pos = url.indexOf("&KeyWord=")
        return if (pos < 0) url else url.take(pos)
    }

    // location 天津市
    // category 民事
    // courtLevel 基层 中级 高级 最高
    // tribunalLevel  一审 二审
    fun search(keyword: String = "", location: String? = null, category: String? = null, courtLevel: String? = null
               , tribunalLevel: String? = null, toPage: Int = 1
    ) {
        val webDriver = FirefoxDriver()

        val url = "http://wenshu.court.gov.cn/list/list/?sorttype=1&conditions=searchWord+QWJS+++全文检索:$keyword${
        if (location != null)
            "&conditions=searchWord+$location+++法院地域:$location"
        else
            ""
        }${
        if (category != null)
            "&conditions=searchWord+${category}案由+++一级案由:${category}案由"
        else
            ""
        }${
        if (courtLevel != null)
            "&conditions=searchWord+${courtLevel}法院+++法院层级:${courtLevel}法院"
        else
            ""
        }${
        if (tribunalLevel != null)
            "&conditions=searchWord+$tribunalLevel+++审判程序:$tribunalLevel"
        else
            ""
        }"

        webDriver.get(url)

        var cnt = 0

        // process the current page
        fun f(toPage: Int) {

            fun reconnect(toPage: Int = 1) {
                if (webDriver.windowHandles.size > 1)
                    return

                webDriver.close()
                if (captchaWarning) {
                    Thread.sleep(60000)
                    captchaWarning = false
                }

                search(keyword, location, category, courtLevel, tribunalLevel, toPage)
            }

            var v = listOf<WebElement>()
            var currentPageNumber = 0

            fun toPageLink(number: Int): Boolean {
                if (currentPageNumber == number)
                    return true

                val pages = webDriver.findElementsByCssSelector("#pageNumber>*")
                val lastPage = pages.findLast { """\d+""".toRegex().matches(it.text.trim()) }!!

                // move forward only
                if (lastPage.text.trim().toInt() < number) {
                    if (webDriver.findElementsByCssSelector("#pageNumber .next").isEmpty()) return false

                    lastPage.click()
                    return toPageLink(number)
                } else {
                    pages.find { it.text.trim() == number.toString() }!!.click()
                    return true
                }
            }

            // in case of blank page or captcha retry
            try {
                // get current page number
                webDriver.findElementsByCssSelector("#pageNumber .current")
                    .find { """\d+""".toRegex().matches(it.text.trim()) }.let {
                        if (it == null) reconnect()
                        else {
                            currentPageNumber = it.text.trim().toInt()
                        }
                    }

                toPageLink(toPage).let {
                    if (!it) {
                        // reach the end
                    } else {
                        currentPageNumber = toPage
                    }
                }

                v = webDriver.findElementsByCssSelector(".wstitle a")

            } catch (
                e: Exception
            ) {
                reconnect(toPage)
            }

            if (v.isEmpty()) {
                val t = try {
                    webDriver.findElementById("resultList").text
                } catch (e: Exception) {
                    "繁忙"
                }

                if (t.contains("繁忙")) {
                    reconnect(toPage)
                } else if (t.contains("无符合")) {
                    println("not found")
                } else {
                    if (patience > cnt++) {
                        // wait until the page fully loaded
                        Thread.sleep(800)
                        f(toPage)
                    } else {
                        cnt = 0
                        reconnect(toPage)
                    }
                }
            } else {
                cnt = 0
                val cases = webDriver.findElementsByCssSelector("#resultList .dataItem")
                    .fold(listOf<LegalDocument>()) { acc, webElement ->
                        acc + mappingToDocuments(webElement)
                    }

                v.forEach {
                    it.click()
                }

                // wait until all the pages are open
                while (webDriver.windowHandles.count() < v.count()) {
                    Thread.sleep(2000)
                }

                Thread.sleep(6800)
                // loop through the tabs
                var cnt = 0
                val tolerance = 3
                while (webDriver.windowHandles.size > 1 && cnt++ < tolerance) {
                    val windows = webDriver.windowHandles.size
                    webDriver.windowHandles.drop(1).asReversed().forEachIndexed { index, s ->
                        //10 tabs, 0-9, 2000-0
                        Thread.sleep(windows * 25 - (index * 20).toLong())

                        webDriver.switchTo().window(s)

                        // wait until the Url properly parsed by the browser
                        if (!webDriver.currentUrl.contains("DocID="))
                            return@forEachIndexed


                        val target = cases.find { it.title.startsWith(webDriver.title.take(20)) }.apply {
                            if (this == null) {
                                webDriver.close()
                                return@forEachIndexed
                            }
                            this.url = truncateCoreURL(webDriver.currentUrl)
                        }


                        // #txtValidateCode  -> capchat
                        if (webDriver.currentUrl.contains("VisitRemind")) {
                            // handel
                            captchaWarning = true
                            webDriver.close()
                            return@forEachIndexed
                        }

                        target!!.content = try {
                            webDriver.findElementById("Content").text
                        } catch (e: Exception) {
                            ""
                        }

                        if (target.content.trim().length in 1..10)
                            return@forEachIndexed

                        webDriver.close()

                    }
                }

                cases.forEach {
                    println(it)
                }

                webDriver.switchTo().window(webDriver.windowHandles.first())
            }
        }
        f(toPage)

        webDriver.close()

        search(keyword, location, category, courtLevel, tribunalLevel, toPage + 1)
    }

    private fun mappingToDocuments(webElement: WebElement): LegalDocument {
        // store the category and tribunalLevel
        val tmp = webElement.findElements(By.cssSelector(".ajlx_lable")).take(2).map { it.text }
        return LegalDocument(
            title = webElement.getAttribute("title"), court = webElement.getAttribute("casecourt"),
            filing = webElement.getAttribute("casenumber"),
            date = LocalDate.parse(webElement.getAttribute("judgedate")),
            category = tmp[0], tribunalLevel = if (tmp.size == 1) "" else tmp.last()
        )
    }

}

data class LegalDocument(
    val title: String, val court: String, val filing: String, val date: LocalDate,
    val category: String, val tribunalLevel: String, var url: String = "", var content: String = ""
)