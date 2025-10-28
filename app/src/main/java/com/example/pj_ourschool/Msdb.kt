package com.example.pj_ourschool

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager


object MSSQLConnector {
    private const val URL = "jdbc:jtds:sqlserver://210.124.196.165;databaseName=DB"
    private const val USER = "엉구"
    private const val PASSWORD = "1234"

    suspend fun getConnection(): Connection? {
        return withContext(Dispatchers.IO) {
            try {
                Class.forName("net.sourceforge.jtds.jdbc.Driver")
                DriverManager.getConnection(URL, USER, PASSWORD)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}