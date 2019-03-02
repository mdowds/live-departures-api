package com.mdowds.livedeparturesapi

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import com.mdowds.livedeparturesapi.config.Config
import com.mdowds.livedeparturesapi.config.tflAppId
import com.mdowds.livedeparturesapi.config.tflAppKey
import com.mdowds.livedeparturesapi.config.tflBaseUrl
import mu.KotlinLogging

typealias ArrivalsCallback = (List<TflArrivalPrediction>) -> Unit
typealias NearbyStopsCallback = (TflStopPoints) -> Unit
typealias ErrorCallback = (Exception) -> Unit
typealias Params = List<Pair<String, String>>

private val logger = KotlinLogging.logger {}

object TflApi {

    private const val tflStopTypes = "NaptanMetroStation,NaptanRailStation,NaptanPublicBusCoachTram,NaptanFerryPort"

    fun getNearbyStops(location: Location, radius: Int, callback: NearbyStopsCallback, errorCallback: ErrorCallback? = null) {
        val params = listOf(
                "type" to tflStopTypes,
                "lat" to location.lat.toString(),
                "lon" to location.long.toString(),
                "radius" to radius.toString()
        )

        makeGetRequest("/Place", params, { response ->
            val stopPoints = Gson().fromJson<TflStopPoints>(response, TflStopPoints::class.java)
            callback(stopPoints)
        }, errorCallback)
    }

    fun getArrivals(stopPointId: String, callback: ArrivalsCallback, errorCallback: ErrorCallback? = null) {
        val endpoint = "/StopPoint/$stopPointId/Arrivals"

        makeGetRequest(endpoint, emptyList(), { response ->
            val responseModel = Gson().fromJson<List<TflArrivalPrediction>>(response, object : TypeToken<List<TflArrivalPrediction>>() {}.type)
            callback(responseModel)
        }, errorCallback)
    }

    private fun makeGetRequest(endpoint: String, params: Params, responseCallback: (String) -> Unit, errorCallback: ErrorCallback?) {
        val url = Config.get(tflBaseUrl) + endpoint
        val fullParams = params + listOf("app_id" to Config.get(tflAppId), "app_key" to Config.get(tflAppKey))

        logger.info { "GET $url?${fullParams.joinToString("&") { "${it.first}=${it.second}" }}" }

        Fuel.get(url, fullParams).responseString { request, response, result ->
            when (result) {
                is Result.Failure -> {
                    val exception = result.getException()
                    logger.error(exception) { "Error getting $url" }
                    if(errorCallback != null) {
                        errorCallback(exception)
                    }
                }
                is Result.Success -> {
                    responseCallback(result.get())
                }
            }
        }
    }
}