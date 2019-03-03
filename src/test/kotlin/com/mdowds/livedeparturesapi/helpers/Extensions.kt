package com.mdowds.livedeparturesapi.helpers

import com.google.gson.JsonObject

val JsonObject.messageData: JsonObject
    get() = getAsJsonObject("message")