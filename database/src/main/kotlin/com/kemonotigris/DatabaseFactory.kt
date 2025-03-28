package com.kemonotigris

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

object DatabaseFactory {
    private var driver: JdbcSqliteDriver? = null
    private var database: Database? = null

    fun getDatabase(): Database {
        if (database == null) {
            val sqliteDriver = JdbcSqliteDriver("jdbc:sqlite:vrchat.db")
            Database.Schema.create(sqliteDriver)
            driver = sqliteDriver
            database = Database(sqliteDriver)
        }
        return database!!
    }
}
