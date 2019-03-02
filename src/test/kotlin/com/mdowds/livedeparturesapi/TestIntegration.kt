@file:Suppress("ClassName")

package com.mdowds.livedeparturesapi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.javalin.Javalin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration.ofMillis


private val TIMEOUT = ofMillis(1000L)

class TestIntegration {

    private lateinit var app: Javalin
    private lateinit var client: WebSocketClient
    private lateinit var mockTflApi: WireMockServer

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
            assertTimeout(TIMEOUT) {
                waitFor { client.isOpen }
                assertTrue(client.isOpen)
            }
        }

        @Test
        fun `the client should receive an acknowledgement message`() {
            assertTimeout(TIMEOUT) {
                waitFor { client.messages.count() > 0 }
                assertEquals("Connection acknowledged", client.messages.last())
            }
        }
    }

    @Nested
    inner class `On location message sent`{
        @Test
        fun `it should request nearby stops from the TfL API`() {
            mockTflApi.stubFor(get(urlPathEqualTo("/Place"))
                    .willReturn(aResponse()
                            .withBodyFile("stop-points-response.json")
                    )
            )

            client.sendLocation(51.0, 0.0)

            assertTimeout(TIMEOUT) {
                waitFor { mockTflApi.allServeEvents.count() > 0 }

                mockTflApi.verify(getRequestedFor(urlPathEqualTo("/Place"))
                        .withQueryParam("type", equalTo("NaptanMetroStation,NaptanRailStation,NaptanPublicBusCoachTram,NaptanFerryPort"))
                        .withQueryParam("lat", equalTo("51.0"))
                        .withQueryParam("lon", equalTo("0.0"))
                        .withQueryParam("radius", equalTo("500"))
                )
            }
        }

    }

    @Nested
    inner class `On stop points received` {
        @Test
        fun `the client should receive a message with the stop points`() {
            mockTflApi.stubFor(get(urlPathEqualTo("/Place"))
                    .willReturn(aResponse()
                            .withBodyFile("stop-points-response.json")
                    )
            )

            client.sendLocation(51.0, 0.0)

            assertTimeout(TIMEOUT) {
                waitFor { client.messages.count() > 2 }
            }

            val expectedMessage = """{"type":"STOP_POINTS","message":{"stopPoints":[{"name":"Oxford Circus","stopId":"940GZZLUOXC","modes":["Bus","Tube"]}],"modes":["Bus","Tube"]}}"""
            assertEquals(expectedMessage, client.messages.last())
        }
    }
}

fun waitFor(condition: () -> Boolean) {
    while (!condition()) {
        Thread.sleep(10L)
    }
}
