package me.bmax.apatch.dsh.merge

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.util.Log
import org.json.JSONObject

/**
 * window.androidBridge 兼容桥 —— 按 dsh-mobile-apk 的 AndroidBridge 协议面实现
 * （协议 v1，见 dm docs/design.md）。
 *
 * 目的：dm 的客户端插件（@dsh-android 的 client 半边、vdisplay 面板等）会探测
 * window.androidBridge 是否存在并调用其方法；在 df 的 WebView 里没有这个桥时，
 * 那些页面要么崩溃要么功能缺失。本类以「兼容层」身份注入同名对象：
 *
 * - 能力能映射到 df 现有实现的（剪贴板、系统暗色、沉浸式、通知），直接转发；
 * - 能力属于 dm 壳侧独有、df 没有的（BrowserHost、Vdisplay、Shizuku 特权传输），
 *   按 dm 协议返回合法的"不可用"JSON（与 dm 壳侧未接线时的默认值一致），
 *   保证页面按协议走降级路径而不是抛异常；
 * - 所有方法都是同步 @JavascriptInterface，与 dm 协议一致。
 *
 * 注入点：DshWebUiActivity 的 WebView factory（与 BlobBridge 并列），
 * 注入名恒为 "androidBridge"。
 */
@SuppressLint("SetJavaScriptEnabled")
class MergeAndroidBridge(private val context: Context) {

    private val TAG = "dsh-merge-bridge"

    // ---- 剪贴板 ----

    @JavascriptInterface
    fun copyText(text: String): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("dsh", text))
            true
        } catch (e: Exception) {
            Log.w(TAG, "copyText failed: " + e.message)
            false
        }
    }

    // ---- 主题 / 沉浸式 ----

    @JavascriptInterface
    fun getSystemDark(): Boolean {
        val mode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    @JavascriptInterface
    fun getImmersiveMode(): Boolean = false

    // ---- 提醒 / 通知（转发到系统 Toast 与通知，不依赖容器） ----

    @JavascriptInterface
    fun notify(title: String, text: String) {
        runCatching {
            MergeNotify.post(context, title, text)
        }
    }

    // ---- 系统信息 ----

    @JavascriptInterface
    fun getDeviceInfo(): String {
        return try {
            JSONObject().apply {
                put("model", Build.MODEL)
                put("brand", Build.BRAND)
                put("sdk", Build.VERSION.SDK_INT)
                put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                put("app", MergeManifest.PRODUCT_NAME)
                put("mergeVersion", MergeManifest.MERGE_VERSION)
            }.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    // ---- dm 壳侧独有能力：按协议返回"不可用"，页面走降级 ----

    @JavascriptInterface
    fun pickDirectory(callbackId: String): String =
        JSONObject().put("ok", false).put("reason", "not-wired").toString()

    @JavascriptInterface
    fun exportConfig(): String =
        JSONObject().put("ok", false).put("error", "bridge not wired").toString()

    @JavascriptInterface
    fun importConfig(): String =
        JSONObject().put("ok", false).put("error", "bridge not wired").toString()

    @JavascriptInterface
    fun settingsPath(): String = ""

    @JavascriptInterface
    fun browserHostStatus(): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostCommand(command: String): String =
        JSONObject().put("ok", false).put("available", false)
            .put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostShow(url: String?): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostHide(): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostReload(): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostBounds(payload: String): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostViewport(payload: String): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostClose(): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun browserHostIdentity(payload: String): String =
        JSONObject().put("ok", false).put("reason", "browser-host-not-wired").toString()

    @JavascriptInterface
    fun vdisplayStatus(): String =
        JSONObject().put("ok", false).put("code", "vdisplay-not-wired").toString()

    @JavascriptInterface
    fun vdisplayCreate(): String =
        JSONObject().put("ok", false).put("code", "vdisplay-not-wired").toString()

    @JavascriptInterface
    fun vdisplayDestroy(): String =
        JSONObject().put("ok", false).put("code", "vdisplay-not-wired").toString()

    @JavascriptInterface
    fun vdisplayBounds(payload: String): String =
        JSONObject().put("ok", false).put("code", "vdisplay-not-wired").toString()

    @JavascriptInterface
    fun vdisplaySelect(payload: String): String =
        JSONObject().put("ok", false).put("code", "vdisplay-not-wired").toString()

    @JavascriptInterface
    fun getVdisplayScale(): Double = 0.75

    @JavascriptInterface
    fun setVdisplayScale(value: Double): Double = value

    @JavascriptInterface
    fun getVdisplayFloat(): Boolean = false

    @JavascriptInterface
    fun setVdisplayFloat(enable: Boolean): Boolean = enable

    @JavascriptInterface
    fun forceDestroyVdisplay(): String =
        JSONObject().put("ok", false).put("code", "vdisplay-not-wired").toString()

    // ---- ADB / 授权状态（dm 桥协议；df 未接线，返回不可用） ----

    @JavascriptInterface
    fun privilegeStatus(): String =
        JSONObject().put("ok", false).put("reason", "not-wired").toString()

    @JavascriptInterface
    fun setAdbAllow(serial: String, allow: Boolean): String =
        JSONObject().put("ok", false).put("reason", "not-wired").toString()

    @JavascriptInterface
    fun setAdbPair(serial: String, code: String): String =
        JSONObject().put("ok", false).put("reason", "not-wired").toString()

    @JavascriptInterface
    fun revokeAdbPair(serial: String): String =
        JSONObject().put("ok", false).put("reason", "not-wired").toString()

    @JavascriptInterface
    fun a11yStatus(): String =
        JSONObject().put("enabled", false).toString()

    @JavascriptInterface
    fun openA11ySettings() { /* 跳系统无障碍设置由 df 设置页承担 */ }
}
