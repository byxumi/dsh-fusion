package me.bmax.apatch.dsh.merge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import me.bmax.apatch.ui.MainActivity

/**
 * 合并层通知助手：androidBridge 桥与看门狗共用的小通知通道。
 * 与 df 的 DshNativeBridge 通知相区别：这是**壳侧**（页面桥/看门狗）的通知，
 * 走独立低打扰通道，避免与 agent 通知串台。
 */
object MergeNotify {

    private const val CHANNEL_ID = "dsh_merge"
    private const val NOTIFICATION_ID = 3001
    private const val TAG = "dsh-merge-notify"

    private var channelReady = false

    fun post(context: Context, title: String, text: String) {
        try {
            ensureChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // 通知失败降级为 Toast（页面桥场景下用户能看到响应）
            runCatching {
                Toast.makeText(context, title + ": " + text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (channelReady) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DSH-Fusion 壳侧通知",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "看门狗与页面桥的壳侧通知"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        channelReady = true
    }
}
