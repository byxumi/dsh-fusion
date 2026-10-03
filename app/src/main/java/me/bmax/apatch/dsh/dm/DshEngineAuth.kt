package me.bmax.apatch.dsh.dm

import android.content.Context
import android.util.Log
import me.bmax.apatch.dsh.DshRuntime
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/**
 * DSH-Fusion：dm 移植模块的引擎认证（替代 dm 壳的 EngineAuth）。
 *
 * 与 dm 的差异：df 侧的 launch token 已经从 dsh 启动日志提取并存在
 * [DshRuntime] 的 webToken 状态里（DSH_WEB_TOKEN_RE），所以不需要再解析
 * engine.log —— 直接取 DshRuntime.state.webToken 换 cookie。
 *
 * P0 交换：`GET http://127.0.0.1:<port>/?token=<token>` → 引擎 303 + Set-Cookie
 * （cookie 名前缀 dsh-auth-）。cookie 缓存在 prefs，401/403 时作废重换。
 */
object DshEngineAuth {
    private const val TAG = "dsh-dm-auth"
    private const val PREFS = "dsh_dm_engine_auth"
    private const val KEY_COOKIE = "cookie"
    private const val COOKIE_NAME_PREFIX = "dsh-auth-"

    @Volatile private var cached: String? = null

    fun cookie(context: Context): String? {
        cached?.let { return it }
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COOKIE, null)
        if (stored != null && stored.startsWith(COOKIE_NAME_PREFIX)) {
            cached = stored
            return stored
        }
        return null
    }

    /** 为 mux WS 握手取 cookie;没有缓存就换一次。 */
    fun attachMux(context: Context): String? {
        cookie(context)?.let { return it }
        return refresh(context)
    }

    /** 401/403 后作废并重换一次。 */
    fun handleUnauthorized(context: Context): String? = refresh(context)

    fun invalidate(context: Context) {
        cached = null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_COOKIE).apply()
    }

    /** 用 DshRuntime.webToken 换 cookie(code 303 → Set-Cookie)。 */
    fun refresh(context: Context): String? {
        val token = DshRuntime.state.value.webToken
        if (token.isNullOrBlank()) return null
        val app = context.applicationContext
        return synchronized(this) {
            val port = DshRuntime.port()
            var conn: HttpURLConnection? = null
            try {
                conn = URL("http://127.0.0.1:$port/?token=$token")
                    .openConnection(Proxy.NO_PROXY) as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 4_000
                conn.readTimeout = 4_000
                val code = conn.responseCode
                if (code != 303) {
                    Log.w(TAG, "token exchange unexpected status: $code")
                    return null
                }
                val cookies = conn.headerFields?.get("Set-Cookie") ?: return null
                val ours = cookies.firstOrNull { it.startsWith(COOKIE_NAME_PREFIX) } ?: return null
                val value = ours.substringBefore(";").takeIf { it.contains("=") } ?: return null
                cached = value
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_COOKIE, value).apply()
                return value
            } catch (e: Exception) {
                Log.w(TAG, "token exchange failed: ${e.javaClass.simpleName}")
                null
            } finally {
                conn?.disconnect()
            }
        }
    }
}