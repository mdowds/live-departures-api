package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.mdowds.livedeparturesapi.datasource.tfl.TflApi
import com.mdowds.livedeparturesapi.datasource.tfl.TflArrivalPrediction
import com.mdowds.livedeparturesapi.message.DeparturesResponse
import com.mdowds.livedeparturesapi.message.ResponseMessage
import io.javalin.websocket.WsSession
import java.util.*

class DeparturesSession(private val session: WsSession, private val stopPoints: List<StopPoint>) {
    private var arrivalRequestsTimer: Timer? = null
    private val currentArrivals = mutableMapOf<String, List<TflArrivalPrediction>>()

    fun startUpdatesForMode(mode: Mode) {
        val requestArrivalsFor = stopPoints
                .filter { it.modes.contains(mode) }
                .map{ it.stopId }

        val repeatedTask = object : TimerTask() {
            override fun run() = requestArrivalsFor.forEach { stopId ->
                TflApi.getArrivals(stopId, { arrivalPredictions ->
                    if (currentArrivals[stopId] != arrivalPredictions) {
                        currentArrivals[stopId] = arrivalPredictions
                        sendArrivalPredictions(stopId, arrivalPredictions)
                    }
                })
            }
        }

        val period = (10 * 1000).toLong()
        arrivalRequestsTimer = Timer("Arrival requests")
        arrivalRequestsTimer?.scheduleAtFixedRate(repeatedTask, 0L, period)
    }

    fun stopUpdates() {
        arrivalRequestsTimer?.cancel()
    }

    fun sendArrivalPredictions(stopId: String, arrivalPredictions: List<TflArrivalPrediction>) {
        val departures = arrivalPredictions.map { Departure(it) }
        val response = DeparturesResponse(stopId, departures)
        session.send(Gson().toJson(ResponseMessage(DEPARTURES, response)))
        // Sending from a background thread is causing things to break
        // See https://stackoverflow.com/questions/36305830/blocking-message-pending-10000-for-blocking-using-spring-websockets
    }
}