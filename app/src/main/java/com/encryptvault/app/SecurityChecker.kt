
package com.encryptvault.app

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

data class CheckItem(
    val name: String,
    val passed: Boolean,
    val detail: String,
    val fatal: Boolean
)

object SecurityChecker {

    fun check(ctx: Context): List<CheckItem> = listOf(
        checkRoot(),
        checkFrida(),
        checkXposed(),
        checkDebugger(),
        checkEmulator(),
        checkVirtualApp(),
        checkSignature(ctx),
        checkRepackage()
    )

    fun hasFatal(items: List<CheckItem>): Boolean = items.any { !it.passed && it.fatal }
    fun isClean(items: List<CheckItem>): Boolean = items.all { it.passed }

    private fun checkRoot(): CheckItem {
        var why = ""
        for (p in listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/su", "/su/bin/su", "/system/sd/xbin/su"
        )) {
            if (File(p).exists() && File(p).canRead()) { why = "su: $p"; break }
        }
        if (why.isEmpty()) {
            for (p in listOf("/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules")) {
                if (File(p).exists() && File(p).canRead()) { why = "Magisk: $p"; break }
            }
        }
        return CheckItem("Root", why.isEmpty(), why.ifEmpty { "无 su / Magisk" }, fatal = false)
    }

    private fun checkFrida(): CheckItem {
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }
            return CheckItem("Frida", false, "端口 27042 开放", fatal = true)
        } catch (_: Exception) {}
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida", true) || maps.contains("gum-js-loop", true))
                return CheckItem("Frida", false, "内存映射含 frida", fatal = true)
        } catch (_: Exception) {}
        for (p in listOf("/data/local/tmp/frida-server", "/data/local/tmp/re.frida.server")) {
            if (File(p).exists() && File(p).canRead())
                return CheckItem("Frida", false, "残留: $p", fatal = true)
        }
        return CheckItem("Frida", true, "未检测到", fatal = true)
    }

    private fun checkXposed(): CheckItem {
        for (cls in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage"
        )) {
            try {
                Class.forName(cls)
                return CheckItem("Xposed", false, "类: $cls", fatal = true)
            } catch (_: Throwable) {}
        }
        for (p in listOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar"
        )) {
            if (File(p).exists()) return CheckItem("Xposed", false, "文件: $p", fatal = true)
        }
        return CheckItem("Xposed", true, "未检测到", fatal = true)
    }

    private fun checkDebugger(): CheckItem {
        if (Debug.isDebuggerConnected()) return CheckItem("调试器", false, "Debugger 已连接", fatal = true)
        if (Debug.waitingForDebugger()) return CheckItem("调试器", false, "等待调试器", fatal = true)
        try {
            val s = File("/proc/self/status").readText()
            val m = Regex("TracerPid:\\s*(\\d+)").find(s)
            if (m != null && m.groupValues[1] != "0")
                return CheckItem("调试器", false, "TracerPid=${m.groupValues[1]}", fatal = true)
        } catch (_: Exception) {}
        return CheckItem("调试器", true, "未检测到", fatal = true)
    }

    private fun checkEmulator(): CheckItem {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val product = Build.PRODUCT ?: ""
        val hardware = Build.HARDWARE ?: ""
        // 只检测最明确的模拟器特征
        if (fp.startsWith("generic/") || fp.contains("sdk_gphone")) 
            return CheckItem("模拟器", false, "FP=$fp", fatal = false)
        if (product.contains("sdk_gphone") || product.contains("emulator"))
            return CheckItem("模拟器", false, "PRODUCT=$product", fatal = false)
        if (model.contains("Android SDK built for") || model.contains("sdk_gphone"))
            return CheckItem("模拟器", false, "MODEL=$model", fatal = false)
        if (hardware.contains("goldfish") || hardware.contains("ranchu"))
            return CheckItem("模拟器", false, "HW=$hardware", fatal = false)
        return CheckItem("模拟器", true, "真机特征", fatal = false)
    }

    private fun checkVirtualApp(): CheckItem {
        val n = try {
            File("/proc/${android.os.Process.myPid()}/cmdline")
                .readText().trimEnd('\u0000')
        } catch (_: Exception) { "" }
        if (n.isEmpty()) return CheckItem("虚拟多开", true, "无法判定", fatal = false)
        for (k in listOf("vxp","virtual","clone","sandbox","titan","vmos",
            "lody","parallel","dual.")) {
            if (n.contains(k, true))
                return CheckItem("虚拟多开", false, "进程含: $k", fatal = false)
        }
        return CheckItem("虚拟多开", true, "正常进程", fatal = false)
    }

    private fun checkSignature(ctx: Context): CheckItem {
        val src = ctx.packageResourcePath ?: return CheckItem("APK 路径", true, "未知", fatal = false)
        for (b in listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/")) {
            if (src.contains(b))
                return CheckItem("APK 路径", false, "可疑路径: $src", fatal = true)
        }
        return CheckItem("APK 路径", true, "正常", fatal = false)
    }

    // v13: 不再检测 MT 管理器残留 (误报率高)
    private fun checkRepackage(): CheckItem {
        return CheckItem("重打包", true, "v13 不检测", fatal = false)
    }
}
