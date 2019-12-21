package eu.qiou.aaf4k.plugins

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import eu.qiou.aaf4k.reportings.base.Address
import eu.qiou.aaf4k.reportings.base.Person
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.time.LocalDate

object Handelsregister {
    var requestFactory = NetHttpTransport().createRequestFactory()
    private val urlPart = """'([^']+)'""".toRegex()
    private const val root = """https://www.handelsregisterbekanntmachungen.de/skripte/hrb.php?"""
    private val dateReg = """(\d{2}).(\d{2}).(\d{4})""".toRegex()
    private val prokuristReg =  """\s*([\-\.\w\söäüßÜÖÄ]+)\,\s*([\-\.\w\söäüßÜÖÄ]+)\,\s*([\-\.\w\söäüßÜÖÄ]+)\,\s*\*(\d{2}).(\d{2}).(\d{4})""".toRegex()

    // list url of the all the entries
    fun get(name: String, gericht: Amtsgericht, page: Int = 0): List<String> {
        with(
                requestFactory.buildPostRequest(
                        GenericUrl("https://www.handelsregisterbekanntmachungen.de/?aktion=suche"),
                        ByteArrayContent.fromString(null, "suchart=detail&land=${gericht.land}&gericht=${gericht.id}&seite=$page&fname=${URLEncoder.encode(name, "UTF-8")}&gegenstand=0&anzv=50&order=4")
                )
        ) {
            with(Jsoup.parse(this.execute().parseAsString())) {
                return this.select("li > a[href^=javascript:]")
                        .map { root + urlPart.find(it.attr("href"))!!.groups[1]!!.value }
            }
        }
    }

    // Bouziani, Adnan, Gelsenkirchen, *15.10.1975
    fun walk(name: String, gericht: Amtsgericht): Map<LocalDate, String> {
        return get(name, gericht).map { s ->
            with(requestFactory.buildGetRequest(GenericUrl(s))) {
                with(Jsoup.parse(this.execute().parseAsString())) {
                    this.select("tbody > tr > td")
                            .map { it.text().trim() }.filter { it.isNotBlank() }
                }
            }
        }.map {
            parseGermanDate(it.find { x -> dateReg.matches(x) }!!) to it.last()
        }.toMap()
    }

    private fun parseGermanDate(str: String): LocalDate {
        with(dateReg.find(str)!!.groups) {
            return LocalDate.parse("${this[3]!!.value}-${this[2]!!.value}-${this[1]!!.value}")
        }
    }

    // "Althoff, Maik, Idstein, *22.05.1976; Auberger, Ursula, Steinhöring b München, *26.09.1982; Bartuschka, Wolfram, Berlin, *30.04.1966; Beecker, Dirk, Groß Grönau, *14.09.1965;"
    // parse the HR-String
    fun parseProkurist( content:String ): List<Person> {
        return prokuristReg.findAll(content).map {
            val tmp = it.groups
            Person(0, tmp[1]!!.value, tmp[2]!!.value, dateOfBirth = LocalDate.parse("${tmp[6]!!.value}-${tmp[5]!!.value}-${tmp[4]!!.value}"), address = Address(0, city = tmp[3]!!.value) )
        }.toList()
    }

}