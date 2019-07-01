package eu.qiou.aaf4k.plugins

import org.junit.Test

import org.junit.Assert.*

class UnternehmensregisterTest {

    @Test
    fun search() {
        Unternehmensregister.search("Ehrfeld")
    }
}