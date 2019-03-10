package com.mdowds.livedeparturesapi.config

import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import mu.KotlinLogging

val tflBaseUrl = Key("live-departures.tfl-api.base-url", stringType)
val tflAppId = Key("live-departures.tfl-api.app-id", stringType)
val tflAppKey = Key("live-departures.tfl-api.app-key", stringType)
val arrivalsFetchDelay = Key("live-departures.values.arrivals-fetch-delay-seconds", longType)

object Config {
    private val config: Configuration = EnvironmentVariables() overriding
            systemProperties() overriding
            ConfigurationProperties.fromResource("config.properties")

    // TODO handle key not found
    fun <T> get(key: Key<T>): T {
        return config[key]
    }
}