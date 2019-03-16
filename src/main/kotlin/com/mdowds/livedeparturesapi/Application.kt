package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.mdowds.livedeparturesapi.message.ResponseMessage
import io.javalin.Javalin
import mu.KotlinLogging


const val LOCATION = "LOCATION"
const val MODE = "MODE"
const val STATUS = "STATUS"
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
            logger.info { "Connection received ${session.id}" }
            session.send(Gson().toJson(ResponseMessage(STATUS, "Connection acknowledged")))
        }
        ws.onClose { session, _, _ ->
            logger.info { "Connection closed ${session.id}" }
            handleClose(session)
        }
        ws.onMessage { session, message ->
            logger.info { "Message received ${session.id}" }
            handle(message, session)
        }
    }
    return app
}
