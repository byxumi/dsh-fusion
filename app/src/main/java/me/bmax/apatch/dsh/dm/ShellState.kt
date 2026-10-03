package me.bmax.apatch.dsh.dm

import android.content.Context

/**
 * 进程级 application context：桥 getter 没有 Context 形参时的真源入口。
 *
 * 移植自 dm 的 ShellState.kt(兼容性保留 ShellAppContext);ImmersiveMode / DevLogControl
 * 属于 dm 的 UI/调试日志面,依赖未移植的 LogCollector 与 MainActivity.DevLogPrefs,
 * DSH-Fusion 虚拟屏链无需它们,故不随迁。
 */
internal object ShellAppContext {
    @Volatile private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context? = appContext
}