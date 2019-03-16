package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mdowds.livedeparturesapi.datasource.tfl.TflApi
import com.mdowds.livedeparturesapi.message.*
import io.javalin.websocket.WsSession
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val sessionMap = ConcurrentHashMap<WsSession, DeparturesSession>()

private val logger = KotlinLogging.logger {}

fun handle(message: String, session: WsSession) {
    val rawMessage = JsonParser().parse(message).asJsonObject
    val messageType = rawMessage.get("type").asString
    when(messageType) {
        LOCATION -> handleLocationMessage(LocationMessage.fromJson(rawMessage.get("message").asJsonObject), session)
        MODE -> handleModeMessage(ModeMessage.fromJson(rawMessage.get("message").asJsonObject), session)
        else -> logger.warn { "Unknown message type $messageType" }
    }
}

fun handleClose(session: WsSession) {
    val departuresSession = sessionMap.remove(session)
    departuresSession?.stopUpdates()
}

private fun handleLocationMessage(message: LocationMessage, session: WsSession) {
    TflApi.getNearbyStops(message.location, 500, { tflStopPoints ->
        val stopPoints = tflStopPoints.places.map { StopPoint(it) }
        val departuresSession = DeparturesSession(session, stopPoints)
        sessionMap[session] = departuresSession

        val modes = stopPoints
                .flatMap { it.modes }
                .asSequence()
                .distinct()
                .sorted()
                .toList()

        departuresSession.startUpdatesForMode(modes.first())

        val response = StopPointsResponse(stopPoints, modes)
        session.send(Gson().toJson(ResponseMessage(STOP_POINTS, response)))
    })
}

private fun handleModeMessage(message: ModeMessage, session: WsSession) {
    val departuresSession = sessionMap[session]
    val mode = Mode.fromModeId(message.mode)
    if(departuresSession != null && mode != null)
        departuresSession.startUpdatesForMode(mode)
}