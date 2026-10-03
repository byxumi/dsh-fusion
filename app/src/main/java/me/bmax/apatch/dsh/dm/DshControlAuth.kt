package me.bmax.apatch.dsh.dm

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * DSH-Fusion：控制队列共享令牌与心跳(df 适配 dm DeviceControlService 静态面)。
 *
 * 共享令牌:壳生成一次、持久化,控制长轮询(POST /api/android/ui/pending)鉴权用;
 * 心跳:每次取活/空轮刷新,引擎侧据此判断壳侧控制面是否真的活着。
 */
object DshControlAuth {
    private const val PREFS = "dsh_control"
    private const val KEY_TOKEN = "control_token"
    private const val KEY_HEARTBEAT = "control_heartbeat"
    private const val TOKEN_BYTES = 18

    fun token(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TOKEN, null)
        if (existing.isNullOrEmpty()) {
            val random = ByteArray(TOKEN_BYTES)
            SecureRandom().nextBytes(random)
            val fresh = Base64.encodeToString(
                random,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
            prefs.edit().putString(KEY_TOKEN, fresh).apply()
            return fresh
        }
        return existing
    }

    /** 轮询心跳:每次取活/空轮都刷新,引擎侧据此判断服务是否真的活着。 */
    fun heartbeat(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_HEARTBEAT, System.currentTimeMillis()).apply()
    }
}