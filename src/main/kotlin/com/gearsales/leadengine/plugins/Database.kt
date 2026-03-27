package com.gearsales.leadengine.plugins

import com.gearsales.leadengine.database.tables.DailyBatchLeadsTable
import com.gearsales.leadengine.database.tables.DailyBatchesTable
import com.gearsales.leadengine.database.tables.LeadInteractionsTable
import com.gearsales.leadengine.database.tables.LeadsTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

fun Application.configureDatabase() {
    val sqlitePath = environment.config.property("storage.sqlitePath").getString()
    val dbFile = File(sqlitePath)
    dbFile.parentFile?.mkdirs()

    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
        },
    )
    Database.connect(dataSource)
    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            LeadsTable,
            DailyBatchesTable,
            DailyBatchLeadsTable,
            LeadInteractionsTable,
        )
    }
}
