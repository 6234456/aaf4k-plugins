package eu.qiou.aaf4k.plugins

import org.junit.Test

class CNLegalDocumentsTest {

    @Test
    fun search() {
        CNLegalDocuments().search("上海大众", courtLevel = "中级")
    }
}