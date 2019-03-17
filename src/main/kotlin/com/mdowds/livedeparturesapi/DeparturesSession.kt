package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mdowds.livedeparturesapi.config.*
import com.mdowds.livedeparturesapi.datasource.tfl.TflArrivalPrediction
import com.mdowds.livedeparturesapi.message.DeparturesResponse
import com.mdowds.livedeparturesapi.message.ResponseMessage
import io.javalin.websocket.WsSession
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
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
                    .map { fetch(stopPointId) }
                    .distinct()
                    .map { Gson().fromJson<List<TflArrivalPrediction>>(it, object : TypeToken<List<TflArrivalPrediction>>() {}.type) }
                    .map { arrivalPredictions -> arrivalPredictions.map { Departure(it) } }
                    .subscribe(
                            { onNextDepartures(stopPointId, it)},
                            { logError(stopPointId, it) }
                    )
        }
    }

    // TODO refactor TflApi to use okhttp sync like here
    private fun fetch(stopPointId: String): String {
        val client = OkHttpClient()
        val url = "${Config.get(tflBaseUrl)}/StopPoint/$stopPointId/Arrivals?app_id=${Config.get(tflAppId)}&app_key=${Config.get(tflAppKey)}"
        logger.info { "GET $url" }
        val request = Request.Builder()
                .url(url)
                .build()

        val response = client.newCall(request).execute()
        return response.body()!!.string()
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