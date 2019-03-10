package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mdowds.livedeparturesapi.config.Config
import com.mdowds.livedeparturesapi.config.tflAppId
import com.mdowds.livedeparturesapi.config.tflAppKey
import com.mdowds.livedeparturesapi.config.tflBaseUrl
import com.mdowds.livedeparturesapi.datasource.tfl.TflArrivalPrediction
import com.mdowds.livedeparturesapi.message.DeparturesResponse
import com.mdowds.livedeparturesapi.message.ResponseMessage
import io.javalin.websocket.WsSession
import io.reactivex.Observable
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit


private val logger = KotlinLogging.logger {}



class DeparturesSession(private val session: WsSession, private val stopPoints: List<StopPoint>) {
    private val currentArrivals = mutableMapOf<String, List<TflArrivalPrediction>>()

    fun startUpdatesForMode(mode: Mode) {
        val requestArrivalsFor = stopPoints
                .filter { it.modes.contains(mode) }
                .map{ it.stopId }

        requestArrivalsFor.forEach {stopPointId ->
            Observable.interval(0, 10, TimeUnit.SECONDS)
                    .map { fetch(stopPointId) }
                    .map { Gson().fromJson<List<TflArrivalPrediction>>(it, object : TypeToken<List<TflArrivalPrediction>>() {}.type) }
                    .map { arrivalPredictions -> arrivalPredictions.map { Departure(it) } }
                    .subscribe {
                        val responseMessage = ResponseMessage(DEPARTURES, DeparturesResponse(stopPointId, it))
                        session.remote.sendStringByFuture(Gson().toJson(responseMessage))
                    }
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

    fun stopUpdates() {
        // See https://medium.com/@benlesh/rxjs-dont-unsubscribe-6753ed4fda87 for stopping
//        arrivalRequestsTimer?.cancel()
    }
}