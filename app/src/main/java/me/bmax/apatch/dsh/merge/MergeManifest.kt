package me.bmax.apatch.dsh.merge

/**
 * DSH-Folk (df) + dsh-mobile-apk (dm) 合并层版本清单。
 *
 * 合并目标：保留 df 的原生 UI 与容器化设计（proot rootfs、运行时管理、主题/备份/
 * 插件商店），引入 dm 的运行时实现逻辑（引擎看门狗、配置快照回滚、事务化更新、
 * androidBridge 兼容桥）与功能插件（@dsh-android 全家桶）。
 *
 * 实现纪律：合并层全部为**新增文件**，不修改 df 既有类；对 df 既有文件的改动
 * 只限最小侵入的几处接入点（Application.onCreate、WebView 桥注入）。
 */
object MergeManifest {
    /** 合并层版本（跟随 df 基线递增）。 */
    const val MERGE_VERSION = "0.1.0"

    /** df 基线版本（上游 DSH-Folk）。 */
    const val DF_BASE_VERSION = "1.9.8"

    /** dm 基线版本（上游 dsh-mobile-apk）。 */
    const val DM_BASE_VERSION = "0.14.3"

    /** 合并产物名称。 */
    const val PRODUCT_NAME = "DSH-Fusion"

    /** 看门狗 HTTP 探活超时（毫秒）。 */
    const val PROBE_TIMEOUT_MS = 4_000L

    /** 配置快照保留份数。 */
    const val SNAPSHOT_KEEP = 8
}
