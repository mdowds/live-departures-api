package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.javalin.Javalin
import io.javalin.websocket.WsSession
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.JsonParser
import mu.KotlinLogging


const val LOCATION = "LOCATION"
const val MODE = "MODE"
const val STOP_POINTS = "STOP_POINTS"
const val DEPARTURES = "DEPARTURES"

private val logger = KotlinLogging.logger {}
private val sessionMap = ConcurrentHashMap<WsSession, DeparturesSession>()

fun main() {
    start()
}

fun start() : Javalin {
    val app = Javalin.create().start(7000)
    app.get("/") { ctx -> ctx.result("Application running") }
    app.ws("/socket") { ws ->
        ws.onConnect { session ->
            logger.info { "Connection received" }
            sendMessage(session, "Connection acknowledged")
        }
        ws.onClose { _, _, _ -> logger.info { "Connection closed" } }
        ws.onMessage { session, message ->
            logger.info { "Message received" }
            val rawMessage = JsonParser().parse(message).asJsonObject
            val messageType = rawMessage.get("type").asString
            when(messageType) {
                LOCATION -> handleLocationMessage(LocationMessage.fromJson(rawMessage.get("message").asJsonObject), session)
                MODE -> handleModeMessage(ModeMessage.fromJson(rawMessage.get("message").asJsonObject), session)
                else -> logger.warn { "Unknown message type $messageType" }
            }
            sendMessage(session, "Message acknowledged")
        }
    }
    return app
}

fun handleLocationMessage(message: LocationMessage, session: WsSession) {
    TflApi.getNearbyStops(message.location, 500, {
        val departuresSession = DeparturesSession(session, it.places)
        sessionMap[session] = departuresSession
        departuresSession.sendStopPoints(it)
    })
}

fun handleModeMessage(message: ModeMessage, session: WsSession) {
    val departuresSession = sessionMap[session]
    val mode = Mode.fromModeId(message.mode)
    if(departuresSession != null && mode != null)
        departuresSession.startUpdatesForMode(mode)
}


fun sendMessage(session: WsSession, message: String) {
    session.send(message)
}

data class LocationMessage(val location: Location)  {
    companion object {
        fun fromJson(jsonObject: JsonObject): LocationMessage {
            return Gson().fromJson<LocationMessage>(jsonObject, LocationMessage::class.java)
        }
    }
}

data class ModeMessage(val mode: String)  {
    companion object {
        fun fromJson(jsonObject: JsonObject): ModeMessage {
            return Gson().fromJson<ModeMessage>(jsonObject, ModeMessage::class.java)
        }
    }
}

data class Location(val lat: Double, val long: Double)

data class ResponseMessage<T>(val type: String, val message: T)
data class StopPointsResponse(val stopPoints: List<StopPoint>, val modes: List<Mode>)
data class DeparturesResponse(val stopId: String, val departures: List<Departure>)
