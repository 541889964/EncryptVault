
package com.encryptvault.app

import android.content.Context
import android.content.pm.PackageManager
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
    val fatal: Boolean = true
)

object SecurityChecker {

    private const val EXPECTED_SIG_HASH = "eaec23308f3bb1ed24ba4c644ae73c9de40ab3126b7909a2cc16cf481903379b"

    fun check(ctx: Context): List<CheckItem> = listOf(
        checkSignature(ctx),
        checkSignatureCount(ctx),
        checkInstallPath(ctx),
        checkRootBasic(),
        checkMagisk(),
        checkKernelSU(),
        checkZygisk(),
        checkFrida(),
        checkXposed(),
        checkDebugger(),
        checkEmulator(),
        checkVirtualApp(),
        checkVPNAndProxy(),
        checkSELinux(),
        checkBuildTags(),
        checkSuspiciousFiles()
    )

    fun hasFatal(items: List<CheckItem>): Boolean = items.any { !it.passed }

    // ---------- 1. 严格签名 SHA-256 ----------
    private fun checkSignature(ctx: Context): CheckItem {
        try {
            val pm = ctx.packageManager
            val pkg = ctx.packageName
            val info = if (Build.VERSION.SDK_INT >= 28)
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            else
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)

            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            } ?: return CheckItem("签名", false, "无法读取")

            if (sigs.isEmpty()) return CheckItem("签名", false, "空签名")

            val hash = MessageDigest.getInstance("SHA-256")
                .digest(sigs[0].toByteArray())
                .joinToString("") { "%02x".format(it) }

            if (hash != EXPECTED_SIG_HASH)
                return CheckItem("签名", false, "SHA=${hash.take(12)}… ≠ xuanyi")

            return CheckItem("签名", true, "xuanyi 已校验")
        } catch (e: Exception) {
            return CheckItem("签名", false, "异常: ${e.message}")
        }
    }

    // ---------- 2. 签名者数量 ----------
    private fun checkSignatureCount(ctx: Context): CheckItem {
        try {
            val pm = ctx.packageManager
            val info = if (Build.VERSION.SDK_INT >= 28)
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            else
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)

            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            } ?: return CheckItem("签名者", false, "无法读取")

            if (sigs.size != 1)
                return CheckItem("签名者", false, "数量=${sigs.size} (应=1)")
            return CheckItem("签名者", true, "唯一")
        } catch (e: Exception) {
            return CheckItem("签名者", false, "异常: ${e.message}")
        }
    }

    // ---------- 3. 安装路径 ----------
    private fun checkInstallPath(ctx: Context): CheckItem {
        val src = ctx.packageResourcePath ?: return CheckItem("路径", false, "无法读取")
        for (b in listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/",
            "/mnt/", "/storage/", "/system/")) {
            if (src.contains(b)) return CheckItem("路径", false, "可疑: $src")
        }
        if (!src.contains("/data/app/"))
            return CheckItem("路径", false, "非正规路径: $src")
        return CheckItem("路径", true, "正常")
    }

    // ---------- 4. Root 基础 ----------
    private fun checkRootBasic(): CheckItem {
        for (p in listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/system/xbin/daemonsu", "/system/xbin/sugote",
            "/system/bin/.ext/.su", "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk", "/system/app/SuperSU",
            "/system/etc/init.d/99SuperSUDaemon",
            "/system/usr/we-need-root", "/system/xbin/ku.sud"
        )) if (File(p).exists()) return CheckItem("Root", false, "su: $p")

        try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val line = p.inputStream.bufferedReader().readLine()
            if (!line.isNullOrEmpty()) return CheckItem("Root", false, "which su=$line")
        } catch (_: Exception) {}

        return CheckItem("Root", true, "无 su")
    }

    // ---------- 5. Magisk ----------
    private fun checkMagisk(): CheckItem {
        for (p in listOf(
            "/data/adb/magisk", "/data/adb/magisk.img", "/data/adb/magisk.db",
            "/data/adb/magisk_simple", "/data/adb/modules", "/data/adb/modules_update",
            "/sbin/.magisk", "/sbin/.core/mirror", "/sbin/.core/img",
            "/cache/.disable_magisk", "/cache/magisk.log", "/dev/.magisk.unblock",
            "/dev/magisk", "/data/magisk", "/data/magisk.img"
        )) if (File(p).exists()) return CheckItem("Magisk", false, "文件: $p")

        for (prop in listOf(
            "ro.magisk.version", "ro.magisk.versionCode",
            "init.svc.magisk", "init.svc.magisk_pfs", "init.svc.magisk_pfsd",
            "init.svc.magisk_service", "persist.magisk.hide",
            "ro.boot.magisk", "ro.magisk.disable"
        )) {
            val v = getProp(prop)
            if (!v.isNullOrEmpty()) return CheckItem("Magisk", false, "prop $prop=$v")
        }
        try {
            if (File("/proc/mounts").readText().contains("magisk", true))
                return CheckItem("Magisk", false, "/proc/mounts 含 magisk")
        } catch (_: Exception) {}
        return CheckItem("Magisk", true, "无")
    }

    // ---------- 6. KernelSU ----------
    private fun checkKernelSU(): CheckItem {
        for (p in listOf(
            "/data/adb/ksu", "/data/adb/ksud", "/data/adb/modules_kernelsu",
            "/data/adb/ksu/modules", "/dev/ksu", "/dev/kernelsu",
            "/system/lib/modules/kernelsu.ko",
            "/system/lib64/modules/kernelsu.ko",
            "/debug_ramdisk/ksud", "/data/adb/ksu_service"
        )) if (File(p).exists()) return CheckItem("KernelSU", false, "文件: $p")

        try {
            if (File("/proc/modules").readText().contains("kernelsu", true))
                return CheckItem("KernelSU", false, "/proc/modules")
        } catch (_: Exception) {}

        for (prop in listOf(
            "ro.kernelsu.version", "persist.kernelsu.enabled",
            "ro.kernelsu.versionCode"
        )) {
            val v = getProp(prop)
            if (!v.isNullOrEmpty()) return CheckItem("KernelSU", false, "prop $prop=$v")
        }
        return CheckItem("KernelSU", true, "无")
    }

    // ---------- 7. Zygisk ----------
    private fun checkZygisk(): CheckItem {
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("zygisk", true)) return CheckItem("Zygisk", false, "maps")
            if (maps.contains("shamiko", true)) return CheckItem("Zygisk", false, "shamiko")
        } catch (_: Exception) {}
        for (p in listOf(
            "/data/adb/modules/zygisk", "/data/adb/modules/shamiko",
            "/data/adb/modules/zygisk_lsposed"
        )) if (File(p).exists()) return CheckItem("Zygisk", false, "文件: $p")
        return CheckItem("Zygisk", true, "无")
    }

    // ---------- 8. Frida (修复: 用 readLines 而非 forEachLine) ----------
    private fun checkFrida(): CheckItem {
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }
            return CheckItem("Frida", false, "端口 27042")
        } catch (_: Exception) {}

        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida", true)) return CheckItem("Frida", false, "maps:frida")
            if (maps.contains("gum-js-loop", true)) return CheckItem("Frida", false, "maps:gum")
        } catch (_: Exception) {}

        try {
            val ps = ProcessBuilder("ps", "-A").start()
            val lines = BufferedReader(InputStreamReader(ps.inputStream)).readLines()
            val hit = lines.firstOrNull {
                it.contains("frida", true) || it.contains("linjector", true)
            }
            if (hit != null) return CheckItem("Frida", false, "进程: ${hit.take(40)}")
        } catch (_: Exception) {}

        for (p in listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/data/local/tmp/frida"
        )) if (File(p).exists()) return CheckItem("Frida", false, "残留: $p")

        return CheckItem("Frida", true, "无")
    }

    // ---------- 9. Xposed ----------
    private fun checkXposed(): CheckItem {
        for (cls in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage",
            "de.robv.android.xposed.callbacks.XC_LoadPackage"
        )) {
            try { Class.forName(cls); return CheckItem("Xposed", false, "类: $cls") }
            catch (_: Throwable) {}
        }
        for (p in listOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed.so",
            "/system/lib64/libxposed.so"
        )) if (File(p).exists()) return CheckItem("Xposed", false, "文件: $p")
        return CheckItem("Xposed", true, "无")
    }

    // ---------- 10. 调试器 ----------
    private fun checkDebugger(): CheckItem {
        if (Debug.isDebuggerConnected()) return CheckItem("调试器", false, "已连接")
        if (Debug.waitingForDebugger()) return CheckItem("调试器", false, "等待")
        try {
            val s = File("/proc/self/status").readText()
            val m = Regex("TracerPid:\\s*(\\d+)").find(s)
            if (m != null && m.groupValues[1] != "0")
                return CheckItem("调试器", false, "TracerPid=${m.groupValues[1]}")
        } catch (_: Exception) {}
        return CheckItem("调试器", true, "无")
    }

    // ---------- 11. 模拟器 ----------
    private fun checkEmulator(): CheckItem {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val product = Build.PRODUCT ?: ""
        val hw = Build.HARDWARE ?: ""
        val manuf = Build.MANUFACTURER ?: ""
        if (fp.startsWith("generic") || fp.contains("vbox") || fp.contains("test-keys"))
            return CheckItem("模拟器", false, "FP=$fp")
        if (product.contains("sdk") || product.contains("emulator") ||
            product.contains("vbox") || product.contains("x86"))
            return CheckItem("模拟器", false, "PRODUCT=$product")
        if (model.contains("Emulator") || model.contains("Android SDK built") ||
            model.contains("google_sdk"))
            return CheckItem("模拟器", false, "MODEL=$model")
        if (hw.contains("goldfish") || hw.contains("ranchu") ||
            hw.contains("vbox") || hw.contains("x86"))
            return CheckItem("模拟器", false, "HW=$hw")
        if (manuf.contains("Genymotion")) return CheckItem("模拟器", false, "MANUF")
        for (p in listOf(
            "/dev/socket/qemud", "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace", "/system/bin/qemu-props"
        )) if (File(p).exists()) return CheckItem("模拟器", false, "文件: $p")
        return CheckItem("模拟器", true, "真机")
    }

    // ---------- 12. 虚拟多开 ----------
    private fun checkVirtualApp(): CheckItem {
        val n = try {
            File("/proc/${android.os.Process.myPid()}/cmdline").readText().trimEnd('\u0000')
        } catch (_: Exception) { "" }
        if (n.isEmpty()) return CheckItem("多开", true, "无法判定")
        for (k in listOf("vxp","virtual","clone","sandbox","titan","vmos",
            "lody","parallel","dual.","dual_","me.weishu","io.va")) {
            if (n.contains(k, true)) return CheckItem("多开", false, "进程: $k")
        }
        return CheckItem("多开", true, "正常")
    }

    // ---------- 13. 代理/VPN ----------
    private fun checkVPNAndProxy(): CheckItem {
        val h = System.getProperty("http.proxyHost")
        val p = System.getProperty("http.proxyPort")
        if (!h.isNullOrEmpty() && !p.isNullOrEmpty())
            return CheckItem("代理", false, "Proxy=$h:$p")
        val e = System.getenv("http_proxy") ?: System.getenv("HTTP_PROXY") ?: ""
        if (e.isNotEmpty()) return CheckItem("代理", false, "ENV=$e")
        return CheckItem("代理", true, "无")
    }

    // ---------- 14. SELinux ----------
    private fun checkSELinux(): CheckItem {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("getenforce"))
            val line = p.inputStream.bufferedReader().readLine()?.trim() ?: ""
            if (line.equals("Permissive", true))
                return CheckItem("SELinux", false, "Permissive (非 Enforcing)")
            if (line.equals("Disabled", true))
                return CheckItem("SELinux", false, "Disabled")
        } catch (_: Exception) {}
        return CheckItem("SELinux", true, "Enforcing")
    }

    // ---------- 15. Build.TAGS ----------
    private fun checkBuildTags(): CheckItem {
        val tags = Build.TAGS ?: ""
        if (tags.contains("test-keys")) return CheckItem("Build.TAGS", false, "test-keys")
        if (tags.contains("dev-keys")) return CheckItem("Build.TAGS", false, "dev-keys")
        return CheckItem("Build.TAGS", true, "release-keys")
    }

    // ---------- 16. 可疑文件 ----------
    private fun checkSuspiciousFiles(): CheckItem {
        for (p in listOf(
            "/system/bin/busybox", "/system/xbin/busybox",
            "/system/bin/toolbox_magisk", "/dev/com.koushikdutta.superuser.daemon",
            "/data/data/com.koushikdutta.superuser",
            "/data/data/com.thirdparty.superuser",
            "/data/data/eu.chainfire.supersu",
            "/data/data/com.noshufou.android.su",
            "/data/data/com.koushikdutta.superuser",
            "/data/data/me.weishu.kernelsu"
        )) if (File(p).exists()) return CheckItem("可疑文件", false, p)
        return CheckItem("可疑文件", true, "干净")
    }

    private fun getProp(key: String): String? = try {
        Runtime.getRuntime().exec(arrayOf("getprop", key))
            .inputStream.bufferedReader().readLine()?.trim()
    } catch (_: Exception) { null }
}
