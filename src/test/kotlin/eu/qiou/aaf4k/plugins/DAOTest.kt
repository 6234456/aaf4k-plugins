package eu.qiou.aaf4k.plugins

import org.junit.Test

class DAOTest {

    @Test
    fun insert() {
        DAO.getConnection()
        DAO.insert("SHOW DATABASES")
    }
}