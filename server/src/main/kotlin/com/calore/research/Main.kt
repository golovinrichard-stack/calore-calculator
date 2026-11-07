package com.calore.research

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

/**
 * `serve` (default): the HTTP server on `PORT` (8080).
 * `export-stats <dir>`: writes the CSV aggregates (groups of 10 or more only).
 * The database comes from `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`.
 */
fun main(args: Array<String>) {
    val store = Store(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/research",
        user = System.getenv("DATABASE_USER"),
        password = System.getenv("DATABASE_PASSWORD"),
    )
    store.migrate()
    when (args.firstOrNull() ?: "serve") {
        "export-stats" -> println(Stats.export(store, File(args.getOrElse(1) { "." })).absolutePath)
        "serve" -> embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080, host = "0.0.0.0") {
            researchModule(store)
        }.start(wait = true)
        else -> error("Unknown command; use serve or export-stats <dir>")
    }
}
