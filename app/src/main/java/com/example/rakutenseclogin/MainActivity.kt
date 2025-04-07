package com.example.rakutenseclogin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.launch
import com.example.rakutenseclogin.ui.theme.RakutenSecLoginTheme

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 発信権限のリクエスト
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PHONE)
        }

        // WakeLockの初期化
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK,
            "RakutenSecLogin::WakeLock"
        )

        sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        webSocketManager = WebSocketManager(this)

        // 接続状態リスナーの設定
        WebSocketManager.setConnectionStatusListener { status ->
            // UIスレッドで状態を更新
            runOnUiThread {
                val toast = Toast.makeText(this, status, Toast.LENGTH_SHORT)
                toast.show()
            }
        }

        // WebSocketServiceを起動
        val serviceIntent = Intent(this, WebSocketService::class.java)
        startService(serviceIntent)

        // PhoneCallServiceからの要求を処理
        handleDialIntent(intent)

        setContent {
            RakutenSecLoginTheme {
                MainScreen(
                    webSocketManager = webSocketManager,
                    sharedPreferences = sharedPreferences
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // WakeLockを取得（無制限）
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
            Log.d("MainActivity", "WakeLockを取得しました（無制限）")
        }
    }

    override fun onPause() {
        super.onPause()
        // WakeLockを解放
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d("MainActivity", "WakeLockを解放しました")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.disconnect()
        // WakeLockを解放
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d("MainActivity", "WakeLockを解放しました")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: Intent?) {
        intent?.let {
            if (it.getBooleanExtra("ACTION_DIAL", false)) {
                val phoneNumber = it.getStringExtra("PHONE_NUMBER")
                if (!phoneNumber.isNullOrEmpty()) {
                    val callIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                    }
                    startActivity(callIntent)
                }
            }
        }
    }

    private fun savePhoneNumber(number: String) {
        sharedPreferences.edit {
            putString("phone_number", number)
        }
        PhoneCallService.updatePhoneNumber(number)
    }

    private fun saveWebSocketUrl(url: String) {
        sharedPreferences.edit {
            putString("websocket_url", url)
        }
        webSocketManager.disconnect()
    }

    companion object {
        private const val REQUEST_CALL_PHONE = 1
    }
}

@Composable
fun MainScreen(webSocketManager: WebSocketManager, sharedPreferences: SharedPreferences) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var wsUrl by remember {
        mutableStateOf(prefs.getString("websocket_url", "") ?: "")
    }
    var phoneNumber by remember {
        mutableStateOf(prefs.getString("phone_number", "") ?: "")
    }
    var connectionStatus by remember { mutableStateOf("未接続") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var phoneNumberError by remember { mutableStateOf(false) }
    var phoneNumberErrorMessage by remember { mutableStateOf("") }

    // 接続状態リスナーを設定
    LaunchedEffect(Unit) {
        WebSocketManager.setConnectionStatusListener { status ->
            connectionStatus = status
            showError = status.contains("エラー")
            if (showError) {
                errorMessage = status
            }
        }
    }

    // 電話番号のバリデーション関数
    fun validatePhoneNumber(number: String): Boolean {
        return number.all { it.isDigit() || it == '-' }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = wsUrl,
                onValueChange = { wsUrl = it },
                label = { Text("WebSocket URL") },
                placeholder = { Text("ws://192.168.1.10:8080") },
                modifier = Modifier.fillMaxWidth(),
                isError = wsUrl.isEmpty(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.errorContainer
                ),
                supportingText = {
                    if (wsUrl.isEmpty()) {
                        Text(
                            "WebSocket URLを入力してください",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = phoneNumber,
                onValueChange = { input ->
                    // 数字とハイフンのみを許可
                    if (input.isEmpty() || validatePhoneNumber(input)) {
                        phoneNumber = input
                        phoneNumberError = false
                        phoneNumberErrorMessage = ""
                    } else {
                        phoneNumberError = true
                        phoneNumberErrorMessage = "数字とハイフンのみ入力可能です"
                    }
                },
                label = { Text("電話番号") },
                placeholder = { Text("0120-961-678") },
                modifier = Modifier.fillMaxWidth(),
                isError = phoneNumber.isEmpty() || phoneNumberError,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.errorContainer
                ),
                supportingText = {
                    when {
                        phoneNumberError -> Text(
                            phoneNumberErrorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                        phoneNumber.isEmpty() -> Text(
                            "電話番号を入力してください",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // 入力チェック
                    if (wsUrl.isEmpty() || phoneNumber.isEmpty()) {
                        Toast.makeText(context, "WebSocket URLと電話番号を入力してください", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (phoneNumberError) {
                        Toast.makeText(context, phoneNumberErrorMessage, Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 設定を保存
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().apply {
                        putString("websocket_url", wsUrl)
                        putString("phone_number", phoneNumber)
                        apply()
                    }

                    // ハイフンを除去して電話番号を保存
                    val cleanPhoneNumber = phoneNumber.replace("-", "")
                    PhoneCallService.updatePhoneNumber(cleanPhoneNumber)

                    // WebSocketServiceを再起動
                    val serviceIntent = Intent(context, WebSocketService::class.java)
                    context.startService(serviceIntent)

                    Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("設定を保存")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = connectionStatus,
                color = when {
                    connectionStatus.contains("接続済み") -> MaterialTheme.colorScheme.primary
                    connectionStatus.contains("エラー") -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            if (showError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
