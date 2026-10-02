package me.bmax.apatch.dsh.merge

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.bmax.apatch.dsh.DshPhase
import me.bmax.apatch.dsh.DshRuntime

/**
 * 引擎看门狗 —— 移植自 dsh-mobile-apk 的 WatchdogV2（MIT）。
 *
 * df 原版只在启动/停止时检查进程存活，没有「运行起来之后挂了自动重拉」的机制；
 * 这正是 dm 运行时实现逻辑里最有价值的一块。合并层把它按 df 的 API 重新实现：
 *
 * - 引擎处于 RUNNING 阶段时，每 5s 对 http://127.0.0.1:<port>/ 做一次 HTTP 探活；
 * - 连续失败 → 指数退避 5s→10s→20s→40s→80s 封顶（与 dm WatchdogV2 同款阶梯）；
 * - 连续失败达到 TRIGGER_RESTART 次（约 30s 无响应）→ 自动 DshRuntime.restart，
 *   并把计数归零重来；连续失败达到 TRIGGER_UNDO 次 → 触发配置快照回滚
 *   （MergeUndoSnapshot.tryAutoRollback），防止坏插件/坏配置把引擎拖死。
 * - 用户手动重启或探活成功自动复位；应用退到后台仍探活（引擎靠前台服务保活）。
 *
 * 与 df 既有语义的兼容：
 * - 只在 RUNNING 阶段探活：下载/解压/启动中不打扰（df 的 phase 划分天然覆盖）；
 * - 引擎自己重启（watchdog 触发）时不计入「用户崩溃」堆栈，避免误触发 df 的
 *   CrashHandleActivity；
 * - 全部走 df 公开 API（DshRuntime.state / restart()），不触碰内部字段。
 */
object EngineWatchdog {
    private const val TAG = "dsh-merge-watchdog"

    /** 基础探活间隔。 */
    private const val POLL_MS = 5_000L

    /** 连续失败多少次自动重启引擎（30s 无响应）。 */
    const val TRIGGER_RESTART = 6

    /** 连续失败多少次尝试配置回滚（80s 无响应仍起不来）。 */
    const val TRIGGER_UNDO = 16

    /** 重启后观察窗口：范围内又挂则直接进入回滚评估。 */
    const val RESTART_COOLDOWN_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastRestartAt = 0L

    @Volatile
    var consecutiveFailures = 0
        private set

    @Volatile
    private var enabled = false

    /** 最近一次探活结论（供 UI/日志面板展示）。 */
    @Volatile
    var lastProbeHealthy = true
        private set

    /** 启动看门狗（幂等）。由 Application.onCreate 调用。 */
    fun start(context: Context) {
        if (enabled) return
        enabled = true
        job = scope.launch { loop(context) }
        Log.i(TAG, "EngineWatchdog started")
    }

    /** 停止看门狗（幂等）。 */
    fun stop() {
        if (!enabled) return
        enabled = false
        job?.cancel()
        job = null
    }

    /** 复位连续失败计数（引擎手动启动成功后调用，或用户显式关闭看门狗时）。 */
    fun reset() {
        consecutiveFailures = 0
    }

    private suspend fun loop(context: Context) {
        var lastPhase = DshPhase.NOT_READY
        var lastPort = 0
        while (scope.isActive && enabled) {
            val st = DshRuntime.state.value
            val phase = st.phase
            val port = st.port
            if (phase == DshPhase.RUNNING && port > 0) {
                val healthy = probe(context, port)
                lastProbeHealthy = healthy
                if (healthy) {
                    if (consecutiveFailures > 0) {
                        Log.i(TAG, "probe healthy after " + consecutiveFailures + " failures, reset")
                    }
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures++
                    val delayMs = delayForFailureCount(consecutiveFailures)
                    Log.w(TAG, "probe #" + consecutiveFailures + " failed, next in " + delayMs + "ms")
                    if (consecutiveFailures >= TRIGGER_RESTART) {
                        val since = System.currentTimeMillis() - lastRestartAt
                        if (since >= RESTART_COOLDOWN_MS) {
                            Log.e(TAG, "engine unresponsive for " + consecutiveFailures + " probes, restarting")
                            lastRestartAt = System.currentTimeMillis()
                            consecutiveFailures = 0
                            runCatching { DshRuntime.restart() }
                        }
                    } else if (consecutiveFailures >= TRIGGER_UNDO) {
                        Log.e(TAG, "engine still dead after restart window, attempting config rollback")
                        consecutiveFailures = 0
                        lastRestartAt = System.currentTimeMillis()
                        runCatching { MergeUndoSnapshot.tryAutoRollback(context) }
                    }
                    delay(delayMs)
                    continue
                }
            } else {
                // 非 RUNNING 阶段：不探活；阶段/端口变化时复位计数
                if (phase != lastPhase || port != lastPort) {
                    lastPhase = phase
                    lastPort = port
                    if (consecutiveFailures > 0) {
                        Log.i(TAG, "phase change to " + phase + ", reset failure counter")
                        consecutiveFailures = 0
                    }
                }
            }
            delay(POLL_MS)
        }
    }

    /**
     * 指数退避阶梯（与 dm WatchdogV2.delayForFailureCount 同款）：
     * 5s → 10s → 20s → 40s → 80s 封顶。
     */
    fun delayForFailureCount(count: Int): Long {
        val n = (count - 1).coerceAtLeast(0).coerceAtMost(4)
        return (5_000L shl n).coerceAtMost(80_000L)
    }

    /** HTTP 探活：端口可连且返回 2xx/3xx 即视为健康。 */
    private fun probe(context: Context, port: Int): Boolean {
        return try {
            val url = URL("http://127.0.0.1:" + port + "/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = MergeManifest.PROBE_TIMEOUT_MS.toInt()
                readTimeout = MergeManifest.PROBE_TIMEOUT_MS.toInt()
                requestMethod = "GET"
            }
            val code = try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }
}
