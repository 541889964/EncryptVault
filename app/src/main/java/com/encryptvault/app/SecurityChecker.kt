
package com.encryptvault.app

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

    private const val EXPECTED_SIG_HASH = "eaec23308f3bb1ed24ba4c644ae73c9de40ab3126b7909a2cc16cf481903379b"

    fun check(ctx: Context): List<CheckItem> = listOf(
        checkSignature(ctx),
        checkSignatureCount(ctx),
        checkInstallPath(ctx),
        checkFrida(),
        checkXposed(),
        checkDebugger(),
        checkEmulator(),
        checkVirtualApp(),
        checkRootBasic(),
        checkMagisk(),
        checkKernelSU(),
        checkZygisk(),
        checkSELinux()
    )

    fun hasFatal(items: List<CheckItem>): Boolean =
        items.any { !it.passed && it.fatal }

    private fun getSignatures(ctx: Context): Array<Signature>? = try {
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val si = info.signingInfo ?: return null
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures
        }
    } catch (_: Exception) { null }

    private fun checkSignature(ctx: Context): CheckItem {
        val sigs = getSignatures(ctx) ?: return CheckItem("签名", false, "无法读取", true)
        if (sigs.isEmpty()) return CheckItem("签名", false, "空签名", true)
        val sig = sigs.firstOrNull() ?: return CheckItem("签名", false, "空对象", true)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        if (hash != EXPECTED_SIG_HASH)
            return CheckItem("签名", false, "SHA=${hash.take(12)}...", true)
        return CheckItem("签名", true, "xuanyi 通过", true)
    }

    private fun checkSignatureCount(ctx: Context): CheckItem {
        val sigs = getSignatures(ctx) ?: return CheckItem("签名者", false, "无法读取", true)
        if (sigs.size != 1) return CheckItem("签名者", false, "数量=${sigs.size}", true)
        return CheckItem("签名者", true, "唯一", true)
    }

    private fun checkInstallPath(ctx: Context): CheckItem {
        val src = ctx.packageResourcePath ?: return CheckItem("路径", true, "未知", true)
        if (!src.startsWith("/data/app/"))
            return CheckItem("路径", false, "非正规: $src", true)
        for (b in listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/", "/mnt/"))
            if (src.contains(b)) return CheckItem("路径", false, "可疑: $src", true)
        return CheckItem("路径", true, "/data/app/", true)
    }

    private fun checkFrida(): CheckItem {
        try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }
            return CheckItem("Frida", false, "端口 27042", true) } catch (_: Exception) {}
        try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27043), 100) }
            return CheckItem("Frida", false, "端口 27043", true) } catch (_: Exception) {}
        try {
            val m = File("/proc/self/maps").readText()
            if (m.contains("frida", true)) return CheckItem("Frida", false, "maps:frida", true)
            if (m.contains("gum-js-loop", true)) return CheckItem("Frida", false, "maps:gum", true)
        } catch (_: Exception) {}
        try {
            val ps = ProcessBuilder("ps", "-A").start()
            val lines = BufferedReader(InputStreamReader(ps.inputStream)).readLines()
            val hit = lines.firstOrNull { l ->
                l.contains("frida-server", true) || l.contains("frida-helper", true) ||
                l.contains("linjector", true) }
            if (hit != null) return CheckItem("Frida", false, "进程: ${hit.take(30)}", true)
        } catch (_: Exception) {}
        for (p in listOf("/data/local/tmp/frida-server", "/data/local/tmp/re.frida.server"))
            if (File(p).exists()) return CheckItem("Frida", false, "残留: $p", true)
        return CheckItem("Frida", true, "无", true)
    }

    private fun checkXposed(): CheckItem {
        for (cls in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage",
            "de.robv.android.xposed.callbacks.XC_LoadPackage"
        )) {
            try { Class.forName(cls); return CheckItem("Xposed", false, "类: $cls", true) }
            catch (_: Throwable) {}
        }
        for (p in listOf(
            "/system/lib/libxposed_art.so", "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar"
        )) if (File(p).exists()) return CheckItem("Xposed", false, "文件: $p", true)
        return CheckItem("Xposed", true, "无", true)
    }

    private fun checkDebugger(): CheckItem {
        if (Debug.isDebuggerConnected()) return CheckItem("调试器", false, "已连接", true)
        if (Debug.waitingForDebugger()) return CheckItem("调试器", false, "等待", true)
        try {
            val s = File("/proc/self/status").readText()
            val m = Regex("TracerPid:\\s*(\\d+)").find(s)
            if (m != null && m.groupValues[1] != "0")
                return CheckItem("调试器", false, "TracerPid=${m.groupValues[1]}", true)
        } catch (_: Exception) {}
        return CheckItem("调试器", true, "无", true)
    }

    private fun checkEmulator(): CheckItem {
        val hw = Build.HARDWARE ?: ""
        val product = Build.PRODUCT ?: ""
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        if (hw.equals("goldfish", true)) return CheckItem("模拟器", false, "HW=goldfish", true)
        if (hw.equals("ranchu", true)) return CheckItem("模拟器", false, "HW=ranchu", true)
        if (product.startsWith("sdk_gphone", true)) return CheckItem("模拟器", false, "PRODUCT", true)
        if (fp.startsWith("generic/", true)) return CheckItem("模拟器", false, "FP=generic", true)
        if (model.contains("Android SDK built for", true)) return CheckItem("模拟器", false, "MODEL", true)
        if (File("/system/bin/qemu-props").exists()) return CheckItem("模拟器", false, "qemu-props", true)
        return CheckItem("模拟器", true, "真机", true)
    }

    private fun checkVirtualApp(): CheckItem {
        val n = try {
            File("/proc/${android.os.Process.myPid()}/cmdline").readText().trimEnd('\u0000')
        } catch (_: Exception) { "" }
        if (n.isEmpty()) return CheckItem("多开", true, "无法判定", true)
        for (k in listOf(
            "io.va.exposed","com.lody.virtual","com.lbe.parallel","com.excelliance.multi",
            "com.qihoo.magic","com.tencent.parallel","cn.vmos","com.vmos","com.cloneapp"
        )) if (n.contains(k, true)) return CheckItem("多开", false, "包名: $k", true)
        return CheckItem("多开", true, "正常", true)
    }

    private fun checkRootBasic(): CheckItem {
        for (p in listOf(
            "/system/bin/su","/system/xbin/su","/sbin/su","/su/bin/su",
            "/data/local/su","/data/local/bin/su","/data/local/xbin/su",
            "/system/sd/xbin/su","/system/bin/failsafe/su","/system/xbin/daemonsu",
            "/system/app/Superuser.apk","/system/app/SuperSU.apk"
        )) if (File(p).exists()) return CheckItem("Root", false, "su: $p", false)
        try {
            val line = Runtime.getRuntime().exec(arrayOf("which", "su"))
                .inputStream.bufferedReader().readLine()
            if (!line.isNullOrEmpty()) return CheckItem("Root", false, "which su=$line", false)
        } catch (_: Exception) {}
        return CheckItem("Root", true, "无", false)
    }

    private fun checkMagisk(): CheckItem {
        for (p in listOf(
            "/data/adb/magisk","/data/adb/magisk.img","/data/adb/magisk.db",
            "/data/adb/modules","/sbin/.magisk","/sbin/.core/mirror",
            "/sbin/.core/img","/cache/.disable_magisk","/dev/magisk"
        )) if (File(p).exists()) return CheckItem("Magisk", false, "文件: $p", false)
        for (prop in listOf(
            "ro.magisk.version","init.svc.magisk",
            "init.svc.magisk_pfs","persist.magisk.hide"
        )) {
            val v = getProp(prop)
            if (!v.isNullOrEmpty()) return CheckItem("Magisk", false, "$prop=$v", false)
        }
        return CheckItem("Magisk", true, "无", false)
    }

    private fun checkKernelSU(): CheckItem {
        for (p in listOf(
            "/data/adb/ksu","/data/adb/ksud","/data/adb/modules_kernelsu",
            "/dev/ksu","/dev/kernelsu",
            "/system/lib/modules/kernelsu.ko","/system/lib64/modules/kernelsu.ko"
        )) if (File(p).exists()) return CheckItem("KernelSU", false, "文件: $p", false)
        try {
            if (File("/proc/modules").readText().contains("kernelsu", true))
                return CheckItem("KernelSU", false, "/proc/modules", false)
        } catch (_: Exception) {}
        return CheckItem("KernelSU", true, "无", false)
    }

    private fun checkZygisk(): CheckItem {
        try {
            if (File("/proc/self/maps").readText().contains("zygisk", true))
                return CheckItem("Zygisk", false, "maps", false)
        } catch (_: Exception) {}
        for (p in listOf("/data/adb/modules/zygisk","/data/adb/modules/shamiko"))
            if (File(p).exists()) return CheckItem("Zygisk", false, "文件: $p", false)
        return CheckItem("Zygisk", true, "无", false)
    }

    private fun checkSELinux(): CheckItem {
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

    private fun getProp(key: String): String? = try {
        Runtime.getRuntime().exec(arrayOf("getprop", key))
            .inputStream.bufferedReader().readLine()?.trim()
    } catch (_: Exception) { null }
}
