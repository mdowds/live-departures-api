package com.mdowds.livedeparturesapi

data class TflArrivalPrediction(val lineName: String,
                                val stationName: String,
                                val naptanId: String,
                                val destinationName: String,
                                val destinationNaptanId: String,
                                val timeToStation: Int,
                                val modeName: String,
                                val platformName: String)

data class TflStopPoints(val places: List<TflStopPoint>)

data class TflStopPoint(val commonName: String, val naptanId: String, val indicator: String?, val lines: List<Any>, val modes: List<String>)
