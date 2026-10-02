package me.bmax.apatch.dsh.merge

import android.content.Context
import android.util.Log
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
 * 合并层入口：Application.onCreate 调用 [init]，装配 dm 的运行时实现逻辑
 * （看门狗 + 配置快照 + 桥兼容），全部为增量、可单独关闭。
 *
 * 职责一览：
 * 1. [EngineWatchdog]：引擎 RUNNING 后 5s 探活 + 指数退避 + 自动重启/回滚；
 * 2. [MergeUndoSnapshot]：引擎进入 RUNNING 的安静期自动快照配置面（低频率）；
 *    看门狗判定引擎反复启动失败时自动回滚配置；
 * 3. 状态变化挂钩：引擎从其它阶段进入 RUNNING 时复位看门狗计数并安排快照。
 *
 * 关闭开关（SharedPreferences，供设置页使用；默认全开）：
 * - "merge.watchdog.enabled"：false 时看门狗不启动；
 * - "merge.snapshot.enabled"：false 时不自动快照。
 */
object MergeRuntime {
    private const val TAG = "dsh-merge"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initJob: Job? = null
    private var initialized = false

    /** 是否已初始化。 */
    val isInitialized: Boolean get() = initialized

    /** 由 Application.onCreate 调用；幂等。 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("merge_settings", Context.MODE_PRIVATE)
        val watchdogOn = prefs.getBoolean("merge.watchdog.enabled", true)
        val snapshotOn = prefs.getBoolean("merge.snapshot.enabled", true)
        Log.i(TAG, "MergeRuntime init watchdog=" + watchdogOn + " snapshot=" + snapshotOn)

        if (watchdogOn) {
            EngineWatchdog.start(app)
        }
        if (snapshotOn) {
            initJob = scope.launch { snapshotLoop(app) }
        }
    }

    /**
     * 定期快照循环：每 3 分钟看一眼，仅在 RUNNING 且距上次快照 ≥ 5 分钟时执行
     * （快照内部还有二次间隔判断，这里只做节流）。
     */
    private suspend fun snapshotLoop(context: Context) {
        logOnce()
        while (scope.isActive) {
            delay(3 * 60_000L)
            val st = DshRuntime.state.value
            if (st.phase == DshPhase.RUNNING) {
                runCatching { MergeUndoSnapshot.snapshot(context) }
            }
        }
    }

    private var loggedOnce = false
    private fun logOnce() {
        if (loggedOnce) return
        loggedOnce = true
        Log.i(TAG, "merge layer ready: version " + MergeManifest.MERGE_VERSION +
            " (df " + MergeManifest.DF_BASE_VERSION + " + dm " + MergeManifest.DM_BASE_VERSION + ")")
    }
}
