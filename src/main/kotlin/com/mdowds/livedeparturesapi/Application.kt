package com.mdowds.livedeparturesapi

import io.javalin.Javalin
import mu.KotlinLogging


const val LOCATION = "LOCATION"
const val MODE = "MODE"
const val STOP_POINTS = "STOP_POINTS"
const val DEPARTURES = "DEPARTURES"

private val logger = KotlinLogging.logger {}

fun main() {
    start()
}

fun start() : Javalin {
    val app = Javalin.create().start(7000)
    app.get("/") { ctx -> ctx.result("Application running") }
    app.ws("/socket") { ws ->
        ws.onConnect { session ->
            logger.info { "Connection received" }
            session.send("Connection acknowledged")
        }
        ws.onClose { _, _, _ -> logger.info { "Connection closed" } }
        ws.onMessage { session, message ->
            logger.info { "Message received" }
            handle(message, session)
            session.send("Message acknowledged")
        }
    }
    return app
}
