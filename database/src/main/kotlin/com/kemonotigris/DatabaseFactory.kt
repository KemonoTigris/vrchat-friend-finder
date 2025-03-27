package com.kemonotigris

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

object DatabaseFactory {
    private var driver: JdbcSqliteDriver? = null
    private var database: VRChatDatabase? = null

    fun getDatabase(): VRChatDatabase {
        if (database == null) {
            val sqliteDriver = JdbcSqliteDriver("jdbc:sqlite:vrchat.db")
            VRChatDatabase.Schema.create(sqliteDriver)
            driver = sqliteDriver
            database = VRChatDatabase(sqliteDriver)
        }
        return database!!
    }
}
