package com.mdowds.livedeparturesapi.message

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mdowds.livedeparturesapi.Location

data class LocationMessage(val location: Location)  {
    companion object {
        fun fromJson(jsonObject: JsonObject): LocationMessage {
            return Gson().fromJson<LocationMessage>(jsonObject, LocationMessage::class.java)
        }
    }
}

data class ModeMessage(val mode: String)  {
    companion object {
        fun fromJson(jsonObject: JsonObject): ModeMessage {
            return Gson().fromJson<ModeMessage>(jsonObject, ModeMessage::class.java)
        }
    }
}