package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mdowds.livedeparturesapi.config.*
import com.mdowds.livedeparturesapi.datasource.tfl.TflApi
import com.mdowds.livedeparturesapi.datasource.tfl.TflArrivalPrediction
import com.mdowds.livedeparturesapi.message.DeparturesResponse
import com.mdowds.livedeparturesapi.message.ResponseMessage
import io.javalin.websocket.WsSession
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.WriteCallback
import java.util.concurrent.TimeUnit


private val logger = KotlinLogging.logger {}



class DeparturesSession(private val session: WsSession, private val stopPoints: List<StopPoint>) {
    var requests = emptyList<Disposable>()

    fun startUpdatesForMode(mode: Mode) {
        if(!requests.isEmpty()) stopUpdates()

        val requestArrivalsFor = stopPoints
                .filter { it.modes.contains(mode) }
                .map{ it.stopId }

        requests = requestArrivalsFor.map {stopPointId ->
            Observable.interval(0, Config.get(arrivalsFetchDelay), TimeUnit.SECONDS)
                    .map { TflApi.getArrivals(stopPointId) }
                    .distinct()
                    .map { arrivalPredictions -> arrivalPredictions.map { Departure(it) } }
                    .subscribe(
                            { onNextDepartures(stopPointId, it)},
                            { logError(stopPointId, it) }
                    )
        }
    }

    private fun onNextDepartures(stopPointId: String, departures: List<Departure>) {
        val responseMessage = ResponseMessage(DEPARTURES, DeparturesResponse(stopPointId, departures))
        val callback = object: WriteCallback {
            override fun writeSuccess() =
                    logger.info { "New departures for $stopPointId sent to ${session.id}" }

            override fun writeFailed(err: Throwable?) =
                    logError(stopPointId, err)
        }
        session.remote.sendString(Gson().toJson(responseMessage), callback)
    }

    private fun logError(stopPointId: String, err: Throwable?) =
            logger.error { "Error sending departures for $stopPointId to ${session.id}: $err" }


    fun stopUpdates() {
        requests.forEach{ it.dispose() }
    }
}