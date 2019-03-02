package com.mdowds.livedeparturesapi

import io.javalin.Javalin
import junit.framework.TestCase.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.junit.Rule
import com.github.tomakehurst.wiremock.client.WireMock.*


private const val TIMEOUT = 1000L

class TestIntegration {

    private lateinit var app: Javalin
    private lateinit var client: WebSocketClient

    @Rule @JvmField
    val wireMockRule = WireMockRule(8089)

    @Before
    fun setUp() {
        System.setProperty("live-departures.tfl-api.base-url", "http://localhost:8089")
        System.setProperty("live-departures.tfl-api.app-id", "abc123")
        System.setProperty("live-departures.tfl-api.app-key", "abcde12345")

        app = start()
        client = WebSocketClient.connect()
    }

    @After
    fun tearDown() {
        app.stop()
    }

    @Test(timeout = TIMEOUT)
    fun `connecting to the WebSocket should open the connection`() {
        waitFor { client.isOpen }
        assertTrue(client.isOpen)
    }

    @Test(timeout = TIMEOUT)
    fun `the client should receive an acknowledgement message on connection`() {
        waitFor { client.messages.count() > 0 }
        assertEquals("Connection acknowledged", client.messages.last())
    }

    @Test(timeout = TIMEOUT)
    fun `sending a location message should trigger a request to the TfL API for nearby stops`() {
        stubFor(get(urlPathEqualTo("/Place"))
                .willReturn(aResponse()
                        .withBodyFile("stop-points-response.json")
                )
        )

        client.sendLocation(51.0, 0.0)

        waitFor { getAllServeEvents().count() > 0 }

        verify(getRequestedFor(urlPathEqualTo("/Place"))
                .withQueryParam("type", equalTo("NaptanMetroStation,NaptanRailStation,NaptanPublicBusCoachTram,NaptanFerryPort"))
                .withQueryParam("lat", equalTo("51.0"))
                .withQueryParam("lon", equalTo("0.0"))
                .withQueryParam("radius", equalTo("500"))
        )
    }

    @Test(timeout = TIMEOUT)
    fun `the client should receive a message once the stop points are retrieved`() {
        stubFor(get(urlPathEqualTo("/Place"))
                .willReturn(aResponse()
                        .withBodyFile("stop-points-response.json")
                )
        )

        client.sendLocation(51.0, 0.0)

        waitFor { client.messages.count() > 1 }

        val expectedMessage = """{"type":"STOP_POINTS","message":{"stopPoints":[{"name":"Oxford Circus","stopId":"940GZZLUOXC","modes":["Tube","Bus"]}],"modes":["Bus","Tube"]}}"""
        assertEquals(expectedMessage, client.messages.last())
    }

    private fun waitFor(condition: () -> Boolean) {
        while (!condition()) {
            Thread.sleep(10L)
        }
    }
}