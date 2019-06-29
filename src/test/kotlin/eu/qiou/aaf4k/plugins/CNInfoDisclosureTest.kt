package eu.qiou.aaf4k.plugins

import kotlinx.coroutines.runBlocking
import org.junit.Test

class CNInfoDisclosureTest {

    @Test
    fun getEntityInfoById() = runBlocking {
        val v = CNInfoDisclosure.getEntityInfoById("832", 10).values.first()
        println(v)
    }
}