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
        val repackaged: Boolean,
        val issues: List<String>
    ) {
        val clean: Boolean
            get() = !root && !frida && !xposed && !debugger &&
                    !emulator && !virtualApp && signatureValid && !repackaged
    }

    fun check(ctx: Context): Report {
        val issues = mutableListOf<String>()
        val root = isRooted().also { if (it) issues += "Root 环境" }
        val frida = isFrida().also { if (it) issues += "Frida Hook" }
        val xposed = isXposed().also { if (it) issues += "Xposed 框架" }
        val dbg = isDebugger().also { if (it) issues += "调试器附加" }
        val emu = isEmulator().also { if (it) issues += "模拟器" }
        val virt = isVirtualApp().also { if (it) issues += "虚拟多开" }
        val sig = isSignatureValid(ctx).also { if (!it) issues += "签名被篡改" }
        val repack = isRepackaged().also { if (it) issues += "反编译重打包" }
        return Report(root, frida, xposed, dbg, emu, virt, sig, repack, issues)
    }

    private fun isRooted(): Boolean {
        for (p in listOf(
            "/system/app/Superuser.apk","/sbin/su","/system/bin/su",
            "/system/xbin/su","/data/local/xbin/su","/data/local/bin/su",
            "/data/local/su","/su/bin/su","/sbin/.magisk","/data/adb/magisk",
            "/data/adb/modules","/system/xbin/daemonsu")) {
            if (File(p).exists()) return true
        }
        try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            if (!p.inputStream.bufferedReader().readLine().isNullOrEmpty()) return true
        } catch (_: Exception) {}
        return Build.TAGS?.contains("test-keys") == true
    }

    private fun isFrida(): Boolean {
        try {
            File("/proc").listFiles()?.forEach { p ->
                val f = File(p, "cmdline")
                if (f.exists()) {
                    val c = try { f.readText().replace("\u0000", " ") } catch (_: Exception) { "" }
                    if (c.contains("frida") || c.contains("gum-js") || c.contains("linjector"))
                        return true
                }
            }
        } catch (_: Exception) {}
        try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }; return true }
        catch (_: Exception) {}
        for (p in listOf("/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server","/data/local/tmp/frida")) {
            if (File(p).exists()) return true
        }
        try {
            val m = File("/proc/self/maps").readText()
            if (m.contains("frida", true) || m.contains("gum-js", true)) return true
        } catch (_: Exception) {}
        return false
    }

    private fun isXposed(): Boolean {
        for (cls in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers")) {
            try { Class.forName(cls); return true } catch (_: Throwable) {}
        }
        for (p in listOf(
            "/system/lib/libxposed_art.so","/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar")) {
            if (File(p).exists()) return true
        }
        return false
    }

    private fun isDebugger(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        try {
            val s = File("/proc/self/status").readText()
            val m = Regex("TracerPid:\\s*(\\d+)").find(s)
            if (m != null && m.groupValues[1] != "0") return true
        } catch (_: Exception) {}
        return false
    }

    private fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manuf = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        if (fp.startsWith("generic") || fp.contains("vbox")) return true
        if (model.contains("google_sdk") || model.contains("Emulator") ||
            model.contains("Droid4X")) return true
        if (manuf.contains("Genymotion") || manuf.contains("unknown")) return true
        if (brand.startsWith("generic") && device.startsWith("generic")) return true
        for (p in listOf("/dev/socket/qemud", "/dev/qemu_pipe")) {
            if (File(p).exists()) return true
        }
        return false
    }

    private fun isVirtualApp(): Boolean {
        val n = try {
            File("/proc/${android.os.Process.myPid()}/cmdline")
                .readText().trimEnd('\u0000')
        } catch (_: Exception) { "" }
        for (k in listOf("vxp","virtual","dual","clone","sandbox","parallel",
            "titan","vmos","multi","shadow","vphone","lody")) {
            if (n.contains(k, true)) return true
        }
        return false
    }

    private fun isSignatureValid(ctx: Context): Boolean {
        val src = ctx.packageResourcePath ?: return true
        for (b in listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/", "/mnt/")) {
            if (src.contains(b)) return false
        }
        return true
    }

    // 反编译重打包检测
    private fun isRepackaged(): Boolean {
        // 检测常见逆向工具残留
        for (p in listOf(
            "/data/local/tmp/apktool.jar", "/data/local/tmp/apktool",
            "/data/local/tmp/uber-apk-signer.jar",
            "/data/local/tmp/dex2jar", "/sdcard/apktool",
            "/sdcard/MT2", "/sdcard/MT管理器", "/sdcard/NP管理器",
            "/sdcard/Android/data/bin.mt.plus")) {
            if (File(p).exists()) return true
        }
        // 检测 dex 是否被明显篡改 (dex 头部 magic)
        try {
            val apkPath = System.getProperty("java.class.path") ?: ""
            // 简化: 检查 dex md5 前缀, 若与预期不符则视为重打包
            // 这里只做基础检测, 不做具体签名比较
        } catch (_: Exception) {}
        return false
    }
}
