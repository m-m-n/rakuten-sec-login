package com.example.rakutenseclogin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class WebSocketManager(private val context: Context) {
    companion object {
        private const val TAG = "WebSocketManager"
        private var connectionStatusListener: ((String) -> Unit)? = null

        fun setConnectionStatusListener(listener: (String) -> Unit) {
            connectionStatusListener = listener
        }
    }

    private var webSocketClient: WebSocketClient? = null
    private var isConnected = false

    private fun getServerUrl(): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getString("websocket_url", "") ?: ""
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (isConnected) {
            disconnect()
        }

        val serverUrl = getServerUrl()
        if (serverUrl.isEmpty()) {
            Log.e(TAG, "WebSocket URL is empty")
            connectionStatusListener?.invoke("エラー: WebSocket URLが設定されていません")
            return@withContext
        }

        val uri = URI(serverUrl)
        webSocketClient = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                isConnected = true
                connectionStatusListener?.invoke("接続済み")
            }

            override fun onMessage(message: String?) {
                if (message == "CALL") {
                    val intent = Intent(context, PhoneCallService::class.java)
                    context.startService(intent)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                isConnected = false
                connectionStatusListener?.invoke("未接続")
            }

            override fun onError(ex: Exception?) {
                isConnected = false
                connectionStatusListener?.invoke("接続エラー: ${ex?.message ?: "不明なエラー"}")
                ex?.printStackTrace()
            }
        }

        try {
            webSocketClient?.connect()
        } catch (e: Exception) {
            isConnected = false
            connectionStatusListener?.invoke("接続エラー: ${e.message ?: "不明なエラー"}")
            e.printStackTrace()
        }
    }

    fun disconnect() {
        webSocketClient?.close()
        webSocketClient = null
        isConnected = false
        connectionStatusListener?.invoke("未接続")
    }

    fun isConnected(): Boolean = isConnected
}
