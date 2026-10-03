package me.bmax.apatch.dsh.dm

import android.content.Context
import android.util.Log
import me.bmax.apatch.dsh.LogStore
import me.bmax.apatch.dsh.DshEnv
import java.io.File

/**
 * DSH-Fusion：dm 移植模块的日志面（替代 dm 壳的 LogCollector）。
 *
 * dm 的 LogCollector 是 logcat + engine.log 收集；df 侧的等价物是 [LogStore]，
 * 这里做一层薄适配：logcat 恒记，同时按 appContext 追加到引擎日志（与 dsh 日志同面，
 * 用户排障时一处看完）。不抛异常，绝不泄漏 token/cookie。
 */
object DmLog {
    private const val TAG = "dsh-dm"

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(source: String, message: String) {
        Log.i(TAG, "[$source] $message")
        runCatching {
            val ctx = appContext ?: return@runCatching
            val log = LogStore.named(DshEnv.serverLog(ctx))
            log.append("[dsh-dm:$source] $message")
            log.flushForExit()
        }
    }
}