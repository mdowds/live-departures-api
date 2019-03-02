package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.mdowds.livedeparturesapi.datasource.tfl.TflApi
import com.mdowds.livedeparturesapi.datasource.tfl.TflArrivalPrediction
import com.mdowds.livedeparturesapi.datasource.tfl.TflStopPoint
import com.mdowds.livedeparturesapi.datasource.tfl.TflStopPoints
import com.mdowds.livedeparturesapi.message.DeparturesResponse
import com.mdowds.livedeparturesapi.message.ResponseMessage
import com.mdowds.livedeparturesapi.message.StopPointsResponse
import io.javalin.websocket.WsSession
import java.util.*

class DeparturesSession(private val session: WsSession, private val stopPoints: List<TflStopPoint>) {
    private var arrivalRequestsTimer: Timer? = null
    private val currentArrivals = mutableMapOf<String, List<TflArrivalPrediction>>()

    fun startUpdatesForMode(mode: Mode) {
        val requestArrivalsFor = stopPoints.filter { it.modes.contains(mode.id) }.map{ it.naptanId }

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

    fun sendStopPoints(tflStopPoints: TflStopPoints) {
        val stopPoints = tflStopPoints.places.map { StopPoint(it) }

        val modes = tflStopPoints.places
                .flatMap { it.modes }
                .asSequence()
                .distinct()
                .map { Mode.fromModeId(it) }
                .filterNotNull()
                .sorted()
                .toList()

        val response = StopPointsResponse(stopPoints, modes)
        session.send(Gson().toJson(ResponseMessage(STOP_POINTS, response)))
    }

    fun sendArrivalPredictions(stopId: String, arrivalPredictions: List<TflArrivalPrediction>) {
        val departures = arrivalPredictions.map { Departure(it) }
        val response = DeparturesResponse(stopId, departures)
        session.send(Gson().toJson(ResponseMessage(DEPARTURES, response)))
    }
}