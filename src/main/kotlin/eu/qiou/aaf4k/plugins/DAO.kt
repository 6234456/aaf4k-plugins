package eu.qiou.aaf4k.plugins

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

object DAO {
    var conn: Connection? = null
    private var username = credentials.user // provide the username
    private var password = credentials.pwd // provide the corresponding password

    fun insert(sql: String) {
        conn!!.createStatement().execute(sql)
    }

    fun getConnection() {
        if (conn == null) {
            val connectionProps = Properties()
            connectionProps["user"] = username
            connectionProps["password"] = password
            connectionProps["serverTimezone"] = "Europe/Berlin"
            try {
                Class.forName("com.mysql.cj.jdbc.Driver")
                conn = DriverManager.getConnection(
                    "jdbc:" + "mysql" + "://" +
                            credentials.host +
                            ":" + "3306" + "/" +
                            credentials.db,
                    connectionProps
                )
            } catch (ex: SQLException) {
                // handle any errors
                ex.printStackTrace()
            } catch (ex: Exception) {
                // handle any errors
                ex.printStackTrace()
            }
        }
    }
}