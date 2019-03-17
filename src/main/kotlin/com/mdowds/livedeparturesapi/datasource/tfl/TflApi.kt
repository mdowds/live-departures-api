package com.mdowds.livedeparturesapi.datasource.tfl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mdowds.livedeparturesapi.Location
import com.mdowds.livedeparturesapi.config.Config
import com.mdowds.livedeparturesapi.config.tflAppId
import com.mdowds.livedeparturesapi.config.tflAppKey
import com.mdowds.livedeparturesapi.config.tflBaseUrl
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request

private val logger = KotlinLogging.logger {}

object TflApi {
    private const val tflStopTypes = "NaptanMetroStation,NaptanRailStation,NaptanPublicBusCoachTram,NaptanFerryPort"

    private val client = OkHttpClient()

    fun getNearbyStops(location: Location, radius: Int): TflStopPoints {
        val endpoint = "/Place?type=$tflStopTypes&lat=${location.lat}&lon=${location.long}&radius=$radius"
        val response = makeGetRequest(endpoint)
        return Gson().fromJson<TflStopPoints>(response, TflStopPoints::class.java)
    }

    fun getArrivals(stopPointId: String): List<TflArrivalPrediction> {
        val endpoint = "/StopPoint/$stopPointId/Arrivals?app_id=${Config.get(tflAppId)}&app_key=${Config.get(tflAppKey)}"
        val response = makeGetRequest(endpoint)
        return Gson().fromJson<List<TflArrivalPrediction>>(
                response, object : TypeToken<List<TflArrivalPrediction>>() {}.type
        )
    }

    private fun makeGetRequest(endpoint: String): String {
        val separator = if (endpoint.contains("?")) "&" else "?"
        val url = Config.get(tflBaseUrl) + endpoint + separator + "app_id=${Config.get(tflAppId)}&app_key=${Config.get(tflAppKey)}"

        logger.info { "GET $url" }
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        return response.body()!!.string()
    }

}