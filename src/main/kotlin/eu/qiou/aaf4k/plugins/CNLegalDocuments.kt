package eu.qiou.aaf4k.plugins


import eu.qiou.aaf4k.util.time.TimeSpan
import kotlinx.coroutines.delay
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

        DAO.getConnection()
    }
    private fun truncateCoreURL(url: String): String {
        //http://wenshu.court.gov.cn/Html_Pages/VisitRemind20180914.html?DocID=ed85e1bb-e584-45ca-8839-01b2000d137e
        if (url.contains("VisitRemind"))
            return "http://wenshu.court.gov.cn/content/content?" + url.drop(url.indexOf("DocID="))

        val pos = url.indexOf("&KeyWord=")
        return if (pos < 0) url else url.take(pos)
    }

    companion object {
        private var captchaWarning = false
    }

    // location 天津市
    // category 民事
    // subCategory 合同、无因管理、不当得利纠纷    &conditions=searchWord+002004+AY++案由:合同、无因管理、不当得利纠纷
    // courtLevel 基层 中级 高级 最高
    // tribunalLevel  一审 二审
    // date               &conditions=searchWord++CPRQ++裁判日期:2019-06-01 TO 2019-06-05
    suspend fun search(
        keyword: String? = null, location: String? = null, category: String? = null,
        subCategory: SubCategory? = null, courtLevel: String? = null,
        tribunalLevel: String? = null, year: Int? = null, date: TimeSpan? = null, toPage: Int = 1
    ) {
        val webDriver = FirefoxDriver()

        val url = "http://wenshu.court.gov.cn/list/list/?sorttype=1" +
                "${
                if (keyword != null)
                    "&conditions=searchWord+QWJS+++全文检索:$keyword"
                else
                    ""
                }${
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
                if (subCategory != null)
                    "&conditions=searchWord+${subCategory.id}+AY++案由:${subCategory.cateName}"
                else
                    ""
                }${
                if (courtLevel != null)
                    "&conditions=searchWord+${courtLevel}法院+++法院层级:${courtLevel}法院"
                else
                    ""
                }${
                if (date != null)
                    "&conditions=searchWord++CPRQ++裁判日期:${date.start} TO ${date.end}"
                else
                    ""
                }${
                if (year != null)
                    "&conditions=searchWord+$year+++裁判年份:$year"
                else
                    ""
                }${
                if (tribunalLevel != null)
                    "&conditions=searchWord+$tribunalLevel+++审判程序:$tribunalLevel"
                else
                    ""
                }"

        webDriver.get(url)

        val tolerance = 3
        var cnt = 0

        // process the current page
        suspend fun f(): Boolean? {
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
                delay(10000)

                if (webDriver.currentUrl.contains("waf_verify")) {
                    captchaWarning = true
                    return false
                }

                // resultList
                if (webDriver.findElementsByCssSelector("#resultList").isNotEmpty()
                    && webDriver.findElementsByCssSelector("#resultList").first().text.contains("无符合")
                )
                    return null

                // get current page number
                webDriver.findElementsByCssSelector("#pageNumber .current")
                    .find { """\d+""".toRegex().matches(it.text.trim()) }.let {
                        if (it == null) false
                        else {
                            currentPageNumber = it.text.trim().toInt()
                        }
                    }

                toPageLink(toPage).let {
                    if (!it) {
                        // reach the end
                        return null
                    } else {
                        currentPageNumber = toPage
                    }
                }

                while (webDriver.findElementsByCssSelector(".wstitle a").isEmpty()) {
                    delay(800)

                    if (cnt++ > tolerance) return false
                }

            } catch (
                e: Exception
            ) {
                return false
            }


            var v = listOf<WebElement>()
            var indexWindow = webDriver.windowHandles.first()

            suspend fun cropCases(): List<LegalDocument> {
                return try {
                    v = webDriver.findElementsByCssSelector(".wstitle a").apply {
                        this.forEach { it.click() }
                    }

                    if (webDriver.windowHandles.count() > 1) {
                        webDriver.switchTo().window(indexWindow)
                    }

                    webDriver.findElementsByCssSelector("#resultList .dataItem")
                        .fold(listOf()) { acc, webElement ->
                            acc + mappingToDocuments(webElement, subCategory, location)
                    }
                } catch (e: org.openqa.selenium.StaleElementReferenceException) {
                    delay(2000)

                    if (webDriver.windowHandles.count() > 1) {
                        webDriver.windowHandles.forEach {
                            if (it != indexWindow)
                                webDriver.switchTo().window(it).close()
                        }
                    }


                    // org.openqa.selenium.StaleElementReferenceException
                    cropCases()
                }
            }

            val cases = cropCases()

            // wait until all the pages are open
            while (webDriver.windowHandles.count() < v.count()) {
                delay(2000)
            }

            delay(6800)
            // loop through the tabs
            cnt = 0
            while (webDriver.windowHandles.size > 1 && cnt++ < tolerance) {
                val windows = webDriver.windowHandles.size
                webDriver.windowHandles.drop(1).asReversed().forEachIndexed { index, s ->
                    //10 tabs, 0-9, 2000-0
                    delay(windows * 80 - (index * 20).toLong())

                    webDriver.switchTo().window(s)

                    // wait until the Url properly parsed by the browser
                    if (!webDriver.currentUrl.contains("DocID=")) return@forEachIndexed

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

                webDriver.switchTo().window(indexWindow)
            }

            cases.forEach {
                println(it)
                DAO.insert(it.toSQL())
            }

            return true
        }


        val toContinue = f()
        webDriver.quit()

        // true -> move to next / false  -> retry  / null  -> reach the end
        if (toContinue != null) {
            if (captchaWarning) delay(45000)

            captchaWarning = false
            search(
                keyword,
                location,
                category,
                subCategory,
                courtLevel,
                tribunalLevel,
                year,
                date,
                toPage + if (toContinue) 1 else 0
            )
        }

    }

    private fun mappingToDocuments(
        webElement: WebElement,
        subCategory: SubCategory?,
        location: String?
    ): LegalDocument {
        // store the category and tribunalLevel
        val tmp = webElement.findElements(By.cssSelector(".ajlx_lable")).take(2).map { it.text }
        return LegalDocument(
            title = webElement.getAttribute("title"), court = webElement.getAttribute("casecourt"),
            filing = webElement.getAttribute("casenumber"),
            date = LocalDate.parse(webElement.getAttribute("judgedate")),
            category = tmp[0],
            subCategory = subCategory?.cateName ?: "",
            location = location ?: "",
            tribunalLevel = if (tmp.size == 1) "" else tmp.last()
        )
    }

}

data class LegalDocument(
    val title: String, val court: String, val filing: String, val date: LocalDate,
    val category: String,
    val subCategory: String,
    val location: String,
    val tribunalLevel: String,
    var url: String = "",
    var content: String = ""
) {
    fun toSQL(): String {
        return """INSERT INTO `docs` (`id`, `filing`, `court`, `title`, `date`, `category`, `subcategory`, `location`, `tribunal`, `url`, `content`)
            VALUES (NULL, '$filing', '$court', '$title', '$date', '$category', '$subCategory', '$location', '$tribunalLevel', '$url', '$content');"""
    }
}


enum class SubCategory(val cateName: String, val id: String) {
    Contract("合同、无因管理、不当得利纠纷", "002004")
}