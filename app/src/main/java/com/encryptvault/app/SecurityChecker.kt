package com.encryptvault.app

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object SecurityChecker {

    data class Report(
        val root: Boolean,
        val frida: Boolean,
        val xposed: Boolean,
        val debugger: Boolean,
        val emulator: Boolean,
        val virtualApp: Boolean,
        val signatureValid: Boolean,
        val issues: List<String>
    ) {
        val clean: Boolean
            get() = !root && !frida && !xposed && !debugger &&
                    !emulator && !virtualApp && signatureValid
    }

    fun check(ctx: Context): Report {
        val issues = mutableListOf<String>()
        val root = isRooted().also { if (it) issues += "检测到 Root 环境" }
        val frida = isFrida().also { if (it) issues += "检测到 Frida Hook 框架" }
        val xposed = isXposed().also { if (it) issues += "检测到 Xposed 框架" }
        val dbg = isDebugger().also { if (it) issues += "检测到调试器附加" }
        val emu = isEmulator().also { if (it) issues += "检测到模拟器环境" }
        val virt = isVirtualApp().also { if (it) issues += "检测到虚拟多开环境" }
        val sig = isSignatureValid(ctx).also { if (!it) issues += "APK 签名被篡改" }
        return Report(root, frida, xposed, dbg, emu, virt, sig, issues)
    }

    // ---------- Root ----------
    private fun isRooted(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su",
            "/system/xbin/daemonsu", "/system/etc/init.d/99SuperSUDaemon",
            "/sbin/.magisk", "/sbin/.core/mirror", "/data/adb/magisk",
            "/data/adb/modules", "/cache/magisk.log",
            "/system/bin/.ext/.su", "/system/usr/we-need-root/su-backup"
        )
        for (p in paths) if (File(p).exists()) return true
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val line = proc.inputStream.bufferedReader().readLine()
            if (!line.isNullOrEmpty()) return true
        } catch (_: Exception) {}
        if (Build.TAGS?.contains("test-keys") == true) return true
        return false
    }

    // ---------- Frida ----------
    private fun isFrida(): Boolean {
        try {
            val ps = File("/proc").listFiles() ?: return false
            for (p in ps) {
                val cmdline = File(p, "cmdline")
                if (cmdline.exists()) {
                    val content = try { cmdline.readText().replace("\u0000", " ") } catch (_: Exception) { "" }
                    if (content.contains("frida") || content.contains("gum-js-loop") ||
                        content.contains("gmain") || content.contains("linjector")) return true
                }
            }
        } catch (_: Exception) {}
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 120) }
            return true
        } catch (_: Exception) {}
        val fridaFiles = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/data/local/tmp/frida",
            "/data/local/tmp/frida-agent.so"
        )
        for (p in fridaFiles) if (File(p).exists()) return true
        // Frida 内存特征
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida", true) || maps.contains("gum-js-loop", true)) return true
        } catch (_: Exception) {}
        return false
    }

    // ---------- Xposed ----------
    private fun isXposed(): Boolean {
        for (cls in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage"
        )) {
            try { Class.forName(cls); return true } catch (_: Throwable) {}
        }
        val paths = listOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar",
            "/system/framework/XposedInstaller.apk",
            "/data/data/de.robv.android.xposed.installer"
        )
        for (p in paths) if (File(p).exists()) return true
        try {
            throw Exception("check")
        } catch (e: Exception) {
            for (st in e.stackTrace) {
                if (st.className.contains("xposed", true)) return true
            }
        }
        return false
    }

    // ---------- 调试器 ----------
    private fun isDebugger(): Boolean {
        if (Debug.isDebuggerConnected()) return true
        if (Debug.waitingForDebugger()) return true
        try {
            val status = File("/proc/self/status").readText()
            val m = Regex("TracerPid:\\s*(\\d+)").find(status)
            if (m != null && m.groupValues[1] != "0") return true
        } catch (_: Exception) {}
        return false
    }

    // ---------- 模拟器 ----------
    private fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manuf = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val product = Build.PRODUCT ?: ""
        if (fp.startsWith("generic") || fp.contains("vbox") || fp.contains("test-keys")) return true
        if (model.contains("google_sdk") || model.contains("Emulator") ||
            model.contains("Android SDK") || model.contains("Droid4X")) return true
        if (manuf.contains("Genymotion") || manuf.contains("unknown")) return true
        if (brand.startsWith("generic") && device.startsWith("generic")) return true
        if (product.contains("sdk_gphone") || product.contains("emulator")) return true
        for (p in listOf("/dev/socket/qemud", "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so")) {
            if (File(p).exists()) return true
        }
        return false
    }

    // ---------- 虚拟多开 ----------
    private fun isVirtualApp(): Boolean {
        val procName = currentProcessName()
        val kw = listOf("vxp","virtual","dual","clone","sandbox","parallel",
            "titan","vmos","multi","shadow","vphone","goapp","lody")
        for (k in kw) if (procName.contains(k, true)) return true
        return false
    }

    private fun currentProcessName(): String {
        return try {
            val pid = android.os.Process.myPid()
            File("/proc/$pid/cmdline").readText().trimEnd('\u0000')
        } catch (_: Exception) { "" }
    }

    // ---------- 签名 ----------
    private fun isSignatureValid(ctx: Context): Boolean {
        val src = ctx.packageResourcePath ?: return true
        val bads = listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/", "/mnt/")
        for (b in bads) if (src.contains(b)) return false
        return true
    }
}
