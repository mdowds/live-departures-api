package com.mdowds.livedeparturesapi.config

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import mu.KotlinLogging

val tflBaseUrl = Key("live-departures.tfl-api.base-url", stringType)
val tflAppId = Key("live-departures.tfl-api.app-id", stringType)
val tflAppKey = Key("live-departures.tfl-api.app-key", stringType)

object Config {
    private val config: Configuration = EnvironmentVariables() overriding systemProperties()

    // TODO handle key not found
    fun <T> get(key: Key<T>): T {
        return config[key]
    }
}