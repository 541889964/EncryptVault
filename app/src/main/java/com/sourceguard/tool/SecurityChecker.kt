package com.sourceguard.tool

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

data class CheckItem(
    val name: String,
    val passed: Boolean,
    val detail: String,
    val fatal: Boolean
)

object SecurityChecker {

    private const val EXPECTED_SIG = "eaec23308f3bb1ed24ba4c644ae73c9de40ab3126b7909a2cc16cf481903379b"

    fun check(ctx: Context): List<CheckItem> = listOf(
        sig(ctx), sigCount(ctx), path(ctx),
        frida(), xposed(), dbg(), emu(), multi(ctx),
        root(), magisk(), ksu(), zygisk(), selinux()
    )

    fun hasFatal(l: List<CheckItem>): Boolean = l.any { !it.passed && it.fatal }

    private fun getSigs(ctx: Context): Array<Signature>? {
        return try {
            val pm = ctx.packageManager
            if (Build.VERSION.SDK_INT >= 28) {
                val i = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val s = i.signingInfo ?: return null
                if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                val i = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                i.signatures
            }
        } catch (_: Exception) { null }
    }

    private fun sig(ctx: Context): CheckItem {
        val arr = getSigs(ctx) ?: return CheckItem("签名", false, "无法读取", true)
        if (arr.isEmpty()) return CheckItem("签名", false, "空签名", true)
        val s = arr.firstOrNull() ?: return CheckItem("签名", false, "null", true)
        val h = MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (h != EXPECTED_SIG) return CheckItem("签名", false, "SHA=${h.take(12)}...", true)
        return CheckItem("签名", true, "xuanyi 通过", true)
    }

    private fun sigCount(ctx: Context): CheckItem {
        val arr = getSigs(ctx) ?: return CheckItem("签名者", false, "无法读取", true)
        if (arr.size != 1) return CheckItem("签名者", false, "数量=${arr.size}", true)
        return CheckItem("签名者", true, "唯一", true)
    }

    private fun path(ctx: Context): CheckItem {
        val p = ctx.packageResourcePath ?: return CheckItem("路径", true, "未知", true)
        if (!p.startsWith("/data/app/")) return CheckItem("路径", false, "非正规", true)
        for (b in listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/", "/mnt/"))
            if (p.contains(b)) return CheckItem("路径", false, "可疑", true)
        return CheckItem("路径", true, "正规", true)
    }

    private fun frida(): CheckItem {
        try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }
            return CheckItem("Frida", false, "端口 27042", true) } catch (_: Exception) {}
        try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27043), 100) }
            return CheckItem("Frida", false, "端口 27043", true) } catch (_: Exception) {}
        try {
            val m = File("/proc/self/maps").readText()
            if (m.contains("frida", true)) return CheckItem("Frida", false, "maps", true)
            if (m.contains("gum-js-loop", true)) return CheckItem("Frida", false, "gum", true)
        } catch (_: Exception) {}
        try {
            val p = ProcessBuilder("ps", "-A").start()
            val hit = BufferedReader(InputStreamReader(p.inputStream)).readLines().firstOrNull {
                it.contains("frida-server", true) || it.contains("frida-helper", true) || it.contains("linjector", true)
            }
            if (hit != null) return CheckItem("Frida", false, "进程", true)
        } catch (_: Exception) {}
        for (f in listOf("/data/local/tmp/frida-server", "/data/local/tmp/re.frida.server"))
            if (File(f).exists()) return CheckItem("Frida", false, "残留", true)
        return CheckItem("Frida", true, "无", true)
    }

    private fun xposed(): CheckItem {
        for (c in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage"
        )) {
            try { Class.forName(c); return CheckItem("Xposed", false, "类:$c", true) }
            catch (_: Throwable) {}
        }
        for (f in listOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar"
        )) if (File(f).exists()) return CheckItem("Xposed", false, "文件", true)
        return CheckItem("Xposed", true, "无", true)
    }

    private fun dbg(): CheckItem {
        if (Debug.isDebuggerConnected()) return CheckItem("调试器", false, "已连接", true)
        if (Debug.waitingForDebugger()) return CheckItem("调试器", false, "等待", true)
        try {
            val m = Regex("TracerPid:\\s*(\\d+)").find(File("/proc/self/status").readText())
            if (m != null && m.groupValues[1] != "0") return CheckItem("调试器", false, "TracerPid", true)
        } catch (_: Exception) {}
        return CheckItem("调试器", true, "无", true)
    }

    private fun emu(): CheckItem {
        val h = Build.HARDWARE ?: ""; val p = Build.PRODUCT ?: ""
        val f = Build.FINGERPRINT ?: ""; val m = Build.MODEL ?: ""
        if (h.equals("goldfish", true) || h.equals("ranchu", true)) return CheckItem("模拟器", false, "HW", true)
        if (p.startsWith("sdk_gphone", true)) return CheckItem("模拟器", false, "PRODUCT", true)
        if (f.startsWith("generic/", true)) return CheckItem("模拟器", false, "FP", true)
        if (m.contains("Android SDK built for", true)) return CheckItem("模拟器", false, "MODEL", true)
        if (File("/system/bin/qemu-props").exists()) return CheckItem("模拟器", false, "qemu", true)
        return CheckItem("模拟器", true, "真机", true)
    }

    private fun multi(ctx: Context): CheckItem {
        val n = try { File("/proc/${android.os.Process.myPid()}/cmdline").readText().trimEnd('\u0000') }
            catch (_: Exception) { "" }
        if (n.isEmpty()) return CheckItem("多开", true, "无法判定", true)
        for (k in listOf("io.va.exposed","com.lody.virtual","com.lbe.parallel","com.excelliance.multi",
            "com.qihoo.magic","com.tencent.parallel","cn.vmos","com.vmos","com.cloneapp"))
            if (n.contains(k, true)) return CheckItem("多开", false, "包名:$k", true)
        return CheckItem("多开", true, "正常", true)
    }

    private fun root(): CheckItem {
        for (p in listOf(
            "/system/bin/su","/system/xbin/su","/sbin/su","/su/bin/su",
            "/data/local/su","/data/local/bin/su","/data/local/xbin/su",
            "/system/sd/xbin/su","/system/bin/failsafe/su","/system/xbin/daemonsu",
            "/system/app/Superuser.apk","/system/app/SuperSU.apk"
        )) if (File(p).exists()) return CheckItem("Root", false, "su:$p", false)
        try {
            val l = Runtime.getRuntime().exec(arrayOf("which", "su"))
                .inputStream.bufferedReader().readLine()
            if (!l.isNullOrEmpty()) return CheckItem("Root", false, "which su", false)
        } catch (_: Exception) {}
        return CheckItem("Root", true, "无", false)
    }

    private fun magisk(): CheckItem {
        for (p in listOf(
            "/data/adb/magisk","/data/adb/magisk.img","/data/adb/magisk.db",
            "/data/adb/modules","/sbin/.magisk","/sbin/.core/mirror",
            "/sbin/.core/img","/cache/.disable_magisk","/dev/magisk"
        )) if (File(p).exists()) return CheckItem("Magisk", false, "文件", false)
        for (pr in listOf("ro.magisk.version","init.svc.magisk",
            "init.svc.magisk_pfs","persist.magisk.hide")) {
            val v = getProp(pr)
            if (!v.isNullOrEmpty()) return CheckItem("Magisk", false, pr, false)
        }
        return CheckItem("Magisk", true, "无", false)
    }

    private fun ksu(): CheckItem {
        for (p in listOf(
            "/data/adb/ksu","/data/adb/ksud","/data/adb/modules_kernelsu",
            "/dev/ksu","/dev/kernelsu",
            "/system/lib/modules/kernelsu.ko","/system/lib64/modules/kernelsu.ko"
        )) if (File(p).exists()) return CheckItem("KernelSU", false, "文件", false)
        try {
            if (File("/proc/modules").readText().contains("kernelsu", true))
                return CheckItem("KernelSU", false, "modules", false)
        } catch (_: Exception) {}
        return CheckItem("KernelSU", true, "无", false)
    }

    private fun zygisk(): CheckItem {
        try {
            if (File("/proc/self/maps").readText().contains("zygisk", true))
                return CheckItem("Zygisk", false, "maps", false)
        } catch (_: Exception) {}
        for (p in listOf("/data/adb/modules/zygisk","/data/adb/modules/shamiko"))
            if (File(p).exists()) return CheckItem("Zygisk", false, "文件", false)
        return CheckItem("Zygisk", true, "无", false)
    }

    private fun selinux(): CheckItem {
        try {
            val f = File("/sys/fs/selinux/enforce")
            if (f.exists() && f.canRead()) {
                val v = f.readText().trim()
                if (v == "0") return CheckItem("SELinux", false, "Permissive", false)
                if (v == "1") return CheckItem("SELinux", true, "Enforcing", false)
            }
        } catch (_: Exception) {}
        return CheckItem("SELinux", true, "Enforcing", false)
    }

    private fun getProp(k: String): String? = try {
        Runtime.getRuntime().exec(arrayOf("getprop", k))
            .inputStream.bufferedReader().readLine()?.trim()
    } catch (_: Exception) { null }
}
