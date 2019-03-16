package com.mdowds.livedeparturesapi.helpers

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import com.google.gson.JsonObject

val JsonObject.messageData: JsonObject
    get() = getAsJsonObject("message")

fun MappingBuilder.stubFirstRequest(scenario: String): ScenarioMappingBuilder =
        this.inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("1 request")

fun MappingBuilder.stubSecondRequest(scenario: String): ScenarioMappingBuilder =
        this.inScenario(scenario)
                .whenScenarioStateIs("1 request")
                .willSetStateTo("2 requests")

fun MappingBuilder.stubThirdRequest(scenario: String): ScenarioMappingBuilder =
        this.inScenario(scenario)
                .whenScenarioStateIs("2 requests")
                .willSetStateTo("3 requests")
