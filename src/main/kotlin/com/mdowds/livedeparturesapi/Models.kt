package com.mdowds.livedeparturesapi

import com.mdowds.livedeparturesapi.datasource.tfl.TflArrivalPrediction
import com.mdowds.livedeparturesapi.datasource.tfl.TflStopPoint

data class Departure(val line: String, val destination: String, val departureTime: String, val mode: Mode?, val direction: String, val platform: String, val isTerminating: Boolean) {

    constructor(tflArrivalPrediction: TflArrivalPrediction) : this(
            tflArrivalPrediction.lineName,
            convertStationName(tflArrivalPrediction.destinationName),
            formatArrivalTime(tflArrivalPrediction.timeToStation),
            Mode.fromModeId(tflArrivalPrediction.modeName),
            extractDirection(tflArrivalPrediction.platformName),
            extractPlatform(tflArrivalPrediction.platformName),
            tflArrivalPrediction.naptanId == tflArrivalPrediction.destinationNaptanId
    )
}

data class StopPoint(val name: String, val stopId: String, val indicator: String?, val modes: List<Mode>) {
    constructor(tflStopPoint: TflStopPoint) : this(
            convertStationName(tflStopPoint.commonName),
            tflStopPoint.naptanId,
            tflStopPoint.indicator,
            tflStopPoint.modes.mapNotNull{ Mode.fromModeId(it) }.sorted()
    )
}

enum class Mode(val id: String) {
    Bus("bus"),
    Tube("tube"),
    DLR("dlr"),
    Overground("overground"),
    Tram("tram"),
    TflRail("tflrail"),
    RiverBus("river-bus"),
    NationalRail("national-rail");

    companion object {
        fun fromModeId(name: String) = values().find { it.id == name }
    }
}

data class Location(val lat: Double, val long: Double)

private fun convertStationName(name: String): String {
    return listOf(" Rail Station", " Underground Station", " DLR Station", " (London)")
            .fold(name) { currentName, textToReplace ->
                currentName.replace(textToReplace, "")
            }
}

private fun formatArrivalTime(arrivalInSeconds: Int): String {
    val arrivalInMinutes = (arrivalInSeconds / 60)
    return if (arrivalInMinutes == 0) "Due" else "$arrivalInMinutes mins"
}

private fun extractDirection(platformName: String): String {
    return when {
        platformName.contains(" -") -> platformName.substring(0, platformName.indexOf(" -"))
        else -> ""
    }
}

private fun extractPlatform(platformName: String): String {
    return when {
        platformName.contains(" -") -> platformName.substring(platformName.indexOf(" -") + 3)
        !platformName.contains("Platform") -> "Platform $platformName"
        else -> platformName
    }
}
