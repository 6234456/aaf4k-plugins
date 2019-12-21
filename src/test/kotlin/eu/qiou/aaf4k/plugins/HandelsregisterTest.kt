package eu.qiou.aaf4k.plugins

import org.junit.Test

import org.junit.Assert.*

class HandelsregisterTest {

    @Test
    fun get() {
        val s = Handelsregister.walk("BDO AG", Amtsgericht.HAMBURG)
        println(s.mapValues { Handelsregister.parseProkurist(it.value) }.filter { it.value.any { it.familyName == "Zhang" } })
    }

    @Test
    fun reg(){
        val prokuristReg = """\s*([\-\.\w\söäüßÜÖÄ]+)\,\s*([\-\.\w\söäüßÜÖÄ]+)\,\s*([\-\.\w\söäüßÜÖÄ]+)\,\s*\*(\d{2}).(\d{2}).(\d{4})""".toRegex()
        val s = "Althoff, Maik, Idstein, *22.05.1976; Auberger, Ursula, Steinhöring b München, *26.09.1982; Bartuschka, Wolfram, Berlin, *30.04.1966; Beecker, Dirk, Groß Grönau, *14.09.1965;"

        prokuristReg.findAll(s).forEach { println(it.groups) }
    }
}