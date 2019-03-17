@file:Suppress("ClassName")

package com.mdowds.livedeparturesapi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import com.mdowds.livedeparturesapi.helpers.*
import com.mdowds.livedeparturesapi.helpers.assertThat
import org.assertj.core.api.Assertions.assertThat
import io.javalin.Javalin
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail


class TestIntegration {

    private lateinit var app: Javalin
    private lateinit var client: WebSocketClient
    private lateinit var mockTflApi: WireMockServer

    private val arrivalsPath = "/StopPoint/[\\w\\d]+/Arrivals"

    @BeforeEach
    fun beforeEach() {
        System.setProperty("live-departures.tfl-api.base-url", "http://localhost:8089")
        System.setProperty("live-departures.tfl-api.app-id", "abc123")
        System.setProperty("live-departures.tfl-api.app-key", "abcde12345")
        System.setProperty("live-departures.values.arrivals-fetch-delay-seconds", "1")

        app = start()
        client = WebSocketClient.connect()
        mockTflApi = WireMockServer(8089)
        mockTflApi.start()
    }

    @AfterEach
    fun afterEach() {
        client.close()
        app.stop()
        mockTflApi.stop()
    }

    @Nested
    inner class `On connect` {
        @Test
        fun `the connection should be open on the client`() {
            waitFor { client.isOpen }
            assertTrue(client.isOpen)
        }

        @Test
        fun `it should send an acknowledgement message to the client`() {
            waitFor { client.messages.count() > 0 }

            val lastMessage = client.messages.last()
            assertThat(lastMessage)
                    .hasProperty("type" to "STATUS")
                    .hasProperty("message" to "Connection acknowledged")
        }
    }

    @Nested
    inner class `On location message sent`{
        @Test
        fun `it should request nearby stops from the TfL API`() {
            stubStopPointsResponse()
            client.sendLocation(51.515286, -0.142016)

            waitFor { requestsFor("/Place").count() > 0 }

            mockTflApi.verify(getRequestedFor(urlPathEqualTo("/Place"))
                    .withQueryParam("type", equalTo("NaptanMetroStation,NaptanRailStation,NaptanPublicBusCoachTram,NaptanFerryPort"))
                    .withQueryParam("lat", equalTo("51.515286"))
                    .withQueryParam("lon", equalTo("-0.142016"))
                    .withQueryParam("radius", equalTo("500"))
            )
        }

    }

    @Nested
    inner class `On stop points received` {
        @Test
        fun `it should send the stop points to the client`() {
            stubStopPointsResponse()
            stubArrivalsResponse()
            client.sendLocation(51.515286, -0.142016)

            waitFor { client.lastMessageOfType("STOP_POINTS") != null }

            val message = client.lastMessageOfType("STOP_POINTS")
            val firstStopPoint = message?.messageData?.getAsJsonArray("stopPoints")?.get(0)?.asJsonObject
            val secondStopPoint = message?.messageData?.getAsJsonArray("stopPoints")?.get(1)?.asJsonObject

            assertThat(message?.messageData)
                    .hasProperty("modes" to listOf("Bus", "Tube"))
            assertThat(firstStopPoint).hasChildren(
                    "name" to "Oxford Circus",
                    "stopId" to "940GZZLUOXC",
                    "modes" to listOf("Bus", "Tube")
            )
            assertThat(secondStopPoint).hasChildren(
                    "name" to "Oxford Circus Station",
                    "stopId" to "490000173RG",
                    "indicator" to "Stop RG",
                    "modes" to listOf("Bus")
            )
        }

        @Test
        fun `it should request arrivals for stop points of the first available mode`(){
            stubStopPointsResponse()
            stubArrivalsResponse()
            client.sendLocation(51.515286, -0.142016)

            waitFor { requestsFor(arrivalsPath).count() == 2 }

            mockTflApi.verify(getRequestedFor(urlPathEqualTo("/StopPoint/940GZZLUOXC/Arrivals")))
            mockTflApi.verify(getRequestedFor(urlPathEqualTo("/StopPoint/490000173RG/Arrivals")))
        }
    }

    @Nested
    inner class `On arrivals response`{
        @Test
        fun `it should send the arrivals to the client`(){
            stubStopPointsResponse()
            stubArrivalsResponse("940GZZLUOXC")
            stubArrivalsResponse("490000173RG")
            client.sendLocation(51.515286, -0.142016)

            waitFor { client.departuresMessages.count() == 2 }

            assertThat(client.departuresMessages).anySatisfy {
                assertThat(it.messageData).hasProperty("stopId" to "940GZZLUOXC")
            }

            assertThat(client.departuresMessages).anySatisfy {
                assertThat(it.messageData).hasProperty("stopId" to "490000173RG")
            }

            assertThat(client.departuresMessages).allSatisfy {
                val departures = it.messageData.getAsJsonArray("departures")
                assertThat(departures.size()).isGreaterThan(0)
            }
        }

        @Test
        fun `it should not send the arrivals to the client if they haven't changed`(){
            stubStopPointsResponse()

            mockTflApi.stubFor(get(urlPathMatching("/StopPoint/940GZZLUOXC/Arrivals"))
                    .stubFirstRequest("Arrivals change")
                    .willReturn(aResponse()
                            .withBodyFile("arrivals/940GZZLUOXC.json")
                    )
            )

            mockTflApi.stubFor(get(urlPathMatching("/StopPoint/940GZZLUOXC/Arrivals"))
                    .stubSecondRequest("Arrivals change")
                    .willReturn(aResponse()
                            .withBodyFile("arrivals/940GZZLUOXC.json")
                    )
            )

            mockTflApi.stubFor(get(urlPathMatching("/StopPoint/940GZZLUOXC/Arrivals"))
                    .stubThirdRequest("Arrivals change")
                    .willReturn(aResponse()
                            .withBodyFile("arrivals/940GZZLUOXC_2.json")
                    )
            )

            stubArrivalsResponse("490000173RG")
            client.sendLocation(51.515286, -0.142016)

            waitFor { requestsFor(arrivalsPath).count() == 6 }
            waitFor { client.departuresMessages.count() == 3 }

            assertThat(client.departuresMessages).hasSize(3)
            assertThat(requestsFor(arrivalsPath)).hasSize(6)
        }
    }

    @Nested
    inner class `On connection closed`{
        @Test
        fun `it should not make further requests`(){
            stubStopPointsResponse()
            stubArrivalsResponse("940GZZLUOXC")
            stubArrivalsResponse("490000173RG")
            client.sendLocation(51.515286, -0.142016)

            waitFor { requestsFor(arrivalsPath).count() == 2 }
            client.close()
            Thread.sleep(1100)
            assertThat(requestsFor(arrivalsPath)).hasSize(2)
        }
    }

    @Nested
    inner class `On mode change`{
        @Test
        fun `it should start sending departures for the correct stops`(){
            stubStopPointsResponse()
            stubArrivalsResponse("940GZZLUOXC")
            stubArrivalsResponse("490000173RG")
            client.sendLocation(51.515286, -0.142016)

            waitFor { client.departuresMessages.count() == 2 }

            assertThat(client.departuresMessages).anySatisfy {
                assertThat(it.messageData).hasProperty("stopId" to "940GZZLUOXC")
            }

            assertThat(client.departuresMessages).anySatisfy {
                assertThat(it.messageData).hasProperty("stopId" to "490000173RG")
            }

            client.clearMessages()
            client.sendMode("tube")

            waitFor { client.departuresMessages.count() == 1 }

            assertThat(client.departuresMessages).allSatisfy {
                assertThat(it.messageData).hasProperty("stopId" to "940GZZLUOXC")
            }
        }

        @Test
        fun `it should stop making requests for stops of the previous mode`(){
            stubStopPointsResponse()
            stubArrivalsResponse("940GZZLUOXC")
            stubArrivalsResponse("490000173RG")
            client.sendLocation(51.515286, -0.142016)

            waitFor { client.departuresMessages.count() == 2 }
            client.sendMode("tube")

            waitFor { requestsFor(arrivalsPath).count() == 3 }

            mockTflApi.verify(2, getRequestedFor(urlPathEqualTo("/StopPoint/940GZZLUOXC/Arrivals")))
            mockTflApi.verify(1, getRequestedFor(urlPathEqualTo("/StopPoint/490000173RG/Arrivals")))
        }
    }

    @Nested
    inner class `On location change`{

    }

    private fun stubStopPointsResponse() {
        mockTflApi.stubFor(get(urlPathEqualTo("/Place"))
                .willReturn(aResponse()
                        .withBodyFile("stop-points-response.json")
                )
        )
    }

    private fun stubArrivalsResponse(stopPointId: String? = null) {
        val fileName = stopPointId ?: "940GZZLUOXC"
        mockTflApi.stubFor(get(urlPathMatching("/StopPoint/$stopPointId/Arrivals"))
                .willReturn(aResponse()
                        .withBodyFile("arrivals/$fileName.json")
                )
        )
    }

    private fun requestsFor(pattern: String): List<LoggedRequest> =
            mockTflApi.findAll(getRequestedFor(urlPathMatching(pattern)))
}

private const val TIMEOUT = 5000L

fun waitFor(condition: () -> Boolean) = waitFor(TIMEOUT, condition)

fun waitFor(timeout: Long, condition: () -> Boolean) {
    val startTime = System.currentTimeMillis()
    val isTimedOut = { System.currentTimeMillis() - startTime > timeout }
    while (!condition() && !isTimedOut()) {
        Thread.sleep(10L)
    }
    if(!condition()) fail<Any>("Timeout exceeded waiting for condition")
}
