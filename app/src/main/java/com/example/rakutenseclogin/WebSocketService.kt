package com.example.rakutenseclogin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketService : Service() {
    private lateinit var webSocketManager: WebSocketManager
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var connectionJob: Job? = null
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WebSocketService onCreate")

        // SharedPreferencesからURLを取得
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val websocketUrl = prefs.getString("websocket_url", "")

        if (websocketUrl.isNullOrEmpty()) {
            Log.e(TAG, "WebSocket URLが設定されていません")
            updateNotification("WebSocket URLが設定されていません")
            return
        }

        // webSocketManagerを初期化
        webSocketManager = WebSocketManager(this)
        createNotificationChannel()
        startWebSocketConnection()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebSocket接続",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WebSocket接続維持用のチャネル"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // フォアグラウンドサービスとして実行
        startForeground(NOTIFICATION_ID, createNotification("WebSocket接続待機中..."))

        startWebSocketConnection()
        return START_STICKY
    }

    private fun createNotification(message: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("楽天証券ログイン支援")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startWebSocketConnection() {
        if (!::webSocketManager.isInitialized) {
            Log.e(TAG, "webSocketManagerが初期化されていません")
            return
        }

        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            while (isActive) {
                try {
                    // URLの存在確認
                    val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val websocketUrl = prefs.getString("websocket_url", "")

                    if (websocketUrl.isNullOrEmpty()) {
                        Log.e(TAG, "WebSocket URLが設定されていません")
                        updateNotification("WebSocket URLが設定されていません")
                        delay(5000)
                        continue
                    }

                    if (isNetworkAvailable() && !webSocketManager.isConnected()) {
                        Log.d(TAG, "WebSocket接続を試みます")
                        updateNotification("WebSocket接続中...")
                        webSocketManager.connect()
                        updateNotification("WebSocket接続済み")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket接続エラー", e)
                    updateNotification("WebSocket接続エラー: ${e.message}")
                }
                delay(5000) // 5秒ごとに再接続を試みる
            }
        }

        // WebSocketManagerの接続状態監視
        WebSocketManager.setConnectionStatusListener { status ->
            updateNotification(status)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WebSocketService onDestroy")
        connectionJob?.cancel()
        webSocketManager.disconnect()
        webSocket?.close(1000, "Service destroyed")
        reconnectJob?.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 5秒待ってから再接続を試みる
                kotlinx.coroutines.delay(5000)
                startWebSocketConnection()
            } catch (e: Exception) {
                Log.e(TAG, "Error in reconnect schedule", e)
            }
        }
    }

    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "websocket_channel"
    }
}
