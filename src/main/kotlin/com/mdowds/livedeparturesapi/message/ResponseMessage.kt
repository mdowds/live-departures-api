package com.mdowds.livedeparturesapi.message

import com.mdowds.livedeparturesapi.Departure
import com.mdowds.livedeparturesapi.Mode
import com.mdowds.livedeparturesapi.StopPoint

data class ResponseMessage<T>(val type: String, val message: T)
data class StopPointsResponse(val stopPoints: List<StopPoint>, val modes: List<Mode>)
data class DeparturesResponse(val stopId: String, val departures: List<Departure>)
