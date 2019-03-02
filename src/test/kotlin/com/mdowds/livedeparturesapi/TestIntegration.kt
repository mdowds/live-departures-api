@file:Suppress("ClassName")

package com.mdowds.livedeparturesapi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.javalin.Javalin
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*


class TestIntegration {

    private lateinit var app: Javalin
    private lateinit var client: WebSocketClient
    private lateinit var mockTflApi: WireMockServer

    private val arrivalsPath = "/StopPoint/[\\w\\d]+/Arrivals";

    @BeforeEach
    fun setUp() {
        System.setProperty("live-departures.tfl-api.base-url", "http://localhost:8089")
        System.setProperty("live-departures.tfl-api.app-id", "abc123")
        System.setProperty("live-departures.tfl-api.app-key", "abcde12345")

        app = start()
        client = WebSocketClient.connect()
        mockTflApi = WireMockServer(8089)
        mockTflApi.start()
    }

    @AfterEach
    fun tearDown() {
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
        fun `the client should receive an acknowledgement message`() {
            waitFor { client.messages.count() > 0 }
            assertEquals("Connection acknowledged", client.messages.last())
        }
    }

    @Nested
    inner class `On location message sent`{
        @Test
        fun `it should request nearby stops from the TfL API`() {
            stubStopPointsResponse()
            client.sendLocation(51.0, 0.0)

            waitFor { requestsFor("/Place").count() > 0 }

            mockTflApi.verify(getRequestedFor(urlPathEqualTo("/Place"))
                    .withQueryParam("type", equalTo("NaptanMetroStation,NaptanRailStation,NaptanPublicBusCoachTram,NaptanFerryPort"))
                    .withQueryParam("lat", equalTo("51.0"))
                    .withQueryParam("lon", equalTo("0.0"))
                    .withQueryParam("radius", equalTo("500"))
            )
        }

    }

    @Nested
    inner class `On stop points received` {
        @Test
        fun `the client should receive a message with the stop points`() {
            stubStopPointsResponse()
            stubArrivalsResponse()
            client.sendLocation(51.0, 0.0)

            waitFor { client.messages.count() > 2 }

            val expectedMessage = """{"type":"STOP_POINTS","message":{"stopPoints":[{"name":"Oxford Circus","stopId":"940GZZLUOXC","modes":["Bus","Tube"]},{"name":"Oxford Circus Station","stopId":"490000173RG","indicator":"Stop RG","modes":["Bus"]}],"modes":["Bus","Tube"]}}"""
            assertEquals(expectedMessage, client.messages.last())
        }

        @Test
        fun `it should request arrivals for stop points of the first available mode`(){
            stubStopPointsResponse()
            stubArrivalsResponse()
            client.sendLocation(51.0, 0.0)

            waitFor { requestsFor(arrivalsPath).count() > 0 }

            mockTflApi.verify(getRequestedFor(urlPathEqualTo("/StopPoint/940GZZLUOXC/Arrivals")))
            mockTflApi.verify(getRequestedFor(urlPathEqualTo("/StopPoint/490000173RG/Arrivals")))
        }
    }

    private fun stubStopPointsResponse() {
        mockTflApi.stubFor(get(urlPathEqualTo("/Place"))
                .willReturn(aResponse()
                        .withBodyFile("stop-points-response.json")
                )
        )
    }

    private fun stubArrivalsResponse() {
        mockTflApi.stubFor(get(urlPathMatching(arrivalsPath))
                .willReturn(aResponse()
                        .withBodyFile("arrivals/940GZZLUOXC.json")
                )
        )
    }

    private fun requestsFor(pattern: String): List<LoggedRequest> =
            mockTflApi.findAll(getRequestedFor(urlPathMatching(pattern)))
}

private const val TIMEOUT = 1000L

fun waitFor(condition: () -> Boolean) {
    val startTime = System.currentTimeMillis()
    val isTimedOut = { System.currentTimeMillis() - startTime > TIMEOUT }
    while (!condition() && !isTimedOut()) {
        Thread.sleep(10L)
    }
    if(!condition()) fail<Any>("Timeout exceeded waiting for condition")
}
