package com.mdowds.livedeparturesapi

import okhttp3.*
import java.util.concurrent.TimeUnit


class WebSocketClient : WebSocketListener() {

    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000

        fun connect(): WebSocketClient {
            val request = Request.Builder().url("ws://localhost:7000/socket").build()
            val client = WebSocketClient()
            val okHttpClient = OkHttpClient.Builder().readTimeout(0,  TimeUnit.MILLISECONDS).build()
            val webSocket = okHttpClient.newWebSocket(request, client)
            client.webSocket = webSocket
            okHttpClient.dispatcher().executorService().shutdown()
            return client
        }
    }

    lateinit var webSocket: WebSocket

    var isOpen = false
        private set

    val messages = mutableListOf<String>()

    fun sendLocation(lat: Double, long: Double) {
        send("""
            {
              type: "LOCATION",
              message: {
                location: {
                  lat: $lat,
                  long: $long
                }
              }
            }
        """)
    }

    fun send(message: String) {
        webSocket.send(message)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        isOpen = true
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        messages.add(text)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(NORMAL_CLOSURE_STATUS, null)
        isOpen = false
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        println("Error!")
    }


}