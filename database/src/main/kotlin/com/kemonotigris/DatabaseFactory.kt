//package com.kemonotigris
//
//import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
//
//object DatabaseFactory {
//    private var driver: JdbcSqliteDriver? = null
//    private var database: V? = null
//
//    fun getDatabase(dbPath: String): VrcFriendFinderDatabase {
//        if (database == null) {
//            val sqliteDriver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
//            VrcFriendFinderDatabase.Schema.create(sqliteDriver)
//            driver = sqliteDriver
//            database = VrcFriendFinderDatabase(sqliteDriver)
//        }
//        return database!!
//    }
//}