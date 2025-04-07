package com.example.rakutenseclogin

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.telecom.TelecomManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest

class PhoneCallService : Service() {
    companion object {
        private const val TAG = "PhoneCallService"
        private var phoneNumber: String = ""

        fun updatePhoneNumber(number: String) {
            // 電話番号からハイフンを除去
            phoneNumber = number.replace("-", "")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flagsArg: Int, startId: Int): Int {
        try {
            // 電話番号を取得
            val numberToCall: String = if (phoneNumber.isNotEmpty()) {
                phoneNumber
            } else {
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                // 保存されている電話番号からハイフンを除去
                prefs.getString("phone_number", "")?.replace("-", "") ?: ""
            }

            if (numberToCall.isNotEmpty()) {
                // 直接発信するためのインテントを作成
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$numberToCall")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                // 発信権限の確認
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(callIntent)
                    Log.d(TAG, "電話発信を開始しました: $numberToCall")
                } else {
                    Log.e(TAG, "発信権限がありません")
                }
            } else {
                Log.e(TAG, "電話番号が設定されていません")
            }
        } catch (e: Exception) {
            Log.e(TAG, "電話の発信中にエラーが発生しました", e)
        }

        // すぐにサービスを終了
        stopSelf()
        return START_NOT_STICKY
    }
}
