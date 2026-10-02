package me.bmax.apatch.dsh.merge

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import me.bmax.apatch.dsh.DshEnv

/**
 * 配置快照与自动回滚 —— 移植自 dsh-mobile-apk 的 UndoGate + dsh-undo-savepoint（MIT）。
 *
 * df 原版没有「改坏配置/装坏插件后自动回退」的机制（只有 DshConfigBackup 的手动
 * 导出导入）。合并层补上 dm 的闭环：
 *
 * - [snapshot] 把容器内配置面（.dsh/profiles/web 下的 settings.yaml 与补丁、
 *   以及 .dsh 根部的 settings.yaml 等小型配置文件）复制到 App 私有目录；
 *   只在引擎成功进入 RUNNING 后的安静期快照（避免把半截启动状态存成"好"状态）。
 * - [tryAutoRollback] 由看门狗在「重启后仍起不来」时调用：恢复最近一份快照，
 *   并遵守与 dm UndoGate 相同的防循环纪律（每个崩溃纪元只自动回滚一次、
 *   距上次自动回滚不足 RETRY_WINDOW_MS 不再执行）。
 * - 快照**绝不包括** sessions / workspaces / 附件等用户数据，只针对配置面。
 *
 * 与 df 的 DshConfigBackup 的关系：前者是「用户主动备份」，这里是「引擎坏了
 * 自动回退」，两者互补；回滚操作不经过 dsh-config-manager 的 HTTP API，
 * 因此在插件树整个起不来时也能用（这是它存在的意义）。
 */
object MergeUndoSnapshot {
    private const val TAG = "dsh-merge-undo"

    /** 崩溃纪元间隔：距上次自动回滚 < 该间隔时不再自动执行（防循环）。 */
    const val RETRY_WINDOW_MS = 30 * 60 * 1000L

    /** 自动回滚标记文件名（放在快照根目录）。 */
    private const val DONE_MARKER = ".auto-undo-done"

    /** 快照根目录（App 私有目录内）。 */
    fun rootDir(context: Context): File = File(context.filesDir, "merge-snapshots")

    /** 是否已有快照。 */
    fun hasSnapshots(context: Context): Boolean =
        rootDir(context).listFiles { f -> f.isDirectory }?.isNotEmpty() == true

    /** 列出快照（新→旧）。 */
    fun snapshots(context: Context): List<File> =
        rootDir(context).listFiles { f -> f.isDirectory }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * 对容器配置面做一份快照。只在引擎 RUNNING 且距上次快照超过 5 分钟时执行，
     * 避免把启动中间态存成「好」状态；超过 [MergeManifest.SNAPSHOT_KEEP] 份自动裁剪。
     */
    fun snapshot(context: Context) {
        val home = DshEnv.dshHome(context)
        val configRoot = File(home, "profiles/web")
        if (!configRoot.exists()) return
        val root = rootDir(context)
        root.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(root, stamp)
        if (target.exists()) return
        // 距上次快照 < 5 分钟则不建（避免重启循环把同一状态存一堆）
        val latest = snapshots(context).firstOrNull()
        if (latest != null && System.currentTimeMillis() - latest.lastModified() < 5 * 60_000L) {
            return
        }
        try {
            target.mkdirs()
            copyConfigDir(configRoot, target)
            // 顺带把 .dsh 根部的 settings.yaml 也存（存在时）
            val rootSettings = File(home, "settings.yaml")
            if (rootSettings.isFile) {
                rootSettings.copyTo(File(target, "root-settings.yaml"), overwrite = true)
            }
            trim(context)
            Log.i(TAG, "snapshot created at " + target.name)
        } catch (e: Exception) {
            Log.w(TAG, "snapshot failed: " + e.message)
            target.deleteRecursively()
        }
    }

    /**
     * 自动回滚：看门狗在引擎反复启动失败时调用。遵守防循环纪律：
     * - 有快照且最近一次自动回滚已超过 [RETRY_WINDOW_MS] 才执行；
     * - 执行后写标记文件 + 时间戳，供下次判断。
     */
    fun tryAutoRollback(context: Context): Boolean {
        val last = snapshotsAutoUndoTime(context)
        if (System.currentTimeMillis() - last < RETRY_WINDOW_MS) {
            Log.i(TAG, "auto-undo skipped: cooldown")
            return false
        }
        val latest = snapshots(context).firstOrNull() ?: return false
        return restore(context, latest, auto = true)
    }

    /**
     * 恢复指定快照到容器配置面。恢复前先备份当前（坏）状态到 crash-state/，
     * 恢复后标记时间戳。返回是否成功。
     */
    fun restore(context: Context, snapshot: File, auto: Boolean): Boolean {
        val home = DshEnv.dshHome(context)
        val configRoot = File(home, "profiles/web")
        if (!configRoot.exists() && auto) return false
        return try {
            // 坏状态留一份（便于人工排查），不直接覆盖
            if (configRoot.exists()) {
                val bad = File(rootDir(context), "crash-state-" + snapshot.name)
                if (!bad.exists()) configRoot.copyRecursively(bad, overwrite = true)
            }
            // 恢复：先清空目标再拷入
            configRoot.deleteRecursively()
            configRoot.mkdirs()
            copyConfigDir(snapshot, configRoot)
            val rootSettings = File(snapshot, "root-settings.yaml")
            if (rootSettings.isFile) {
                rootSettings.copyTo(File(home, "settings.yaml"), overwrite = true)
            }
            markAutoUndo(context)
            Log.i(TAG, "restored snapshot " + snapshot.name + " (auto=" + auto + ")")
            true
        } catch (e: Exception) {
            Log.e(TAG, "restore failed: " + e.message)
            false
        }
    }

    /** 用户手动恢复指定快照（可指定任意一份）。 */
    fun restoreManual(context: Context, snapshot: File): Boolean =
        restore(context, snapshot, auto = false)

    /** 统计快照总字节数。 */
    fun totalBytes(context: Context): Long =
        snapshots(context).sumOf { s -> s.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    private fun copyConfigDir(src: File, dst: File) {
        src.listFiles()?.forEach { child ->
            val out = File(dst, child.name)
            if (child.isDirectory) {
                out.mkdirs()
                copyConfigDir(child, out)
            } else if (child.isFile) {
                child.copyTo(out, overwrite = true)
            }
        }
    }

    private fun trim(context: Context) {
        val keep = MergeManifest.SNAPSHOT_KEEP
        val all = snapshots(context)
        val crash = all.firstOrNull { it.name.startsWith("crash-state-") }
        val targets = all.filter { !it.name.startsWith("crash-state-") }
        val drop = targets.size - keep
        if (drop > 0) {
            targets.sortedBy { it.lastModified() }.take(drop).forEach { it.deleteRecursively() }
        }
        // crash-state 最多留 2 份
        val crashCount = all.count { it.name.startsWith("crash-state-") }
        if (crashCount > 2) {
            all.filter { it.name.startsWith("crash-state-") }
                .sortedBy { it.lastModified() }
                .take(crashCount - 2)
                .forEach { it.deleteRecursively() }
        }
        // 防呆：crash-state 不参与裁剪计数
        if (crash != null && crash.exists()) { /* keep */ }
    }

    /** 距上次自动回滚的时间戳（毫秒），没有则 0。 */
    private fun snapshotsAutoUndoTime(context: Context): Long {
        val marker = File(rootDir(context), DONE_MARKER)
        return if (marker.isFile) marker.lastModified() else 0L
    }

    private fun markAutoUndo(context: Context) {
        val marker = File(rootDir(context), DONE_MARKER)
        rootDir(context).mkdirs()
        marker.writeText(System.currentTimeMillis().toString())
    }
}
