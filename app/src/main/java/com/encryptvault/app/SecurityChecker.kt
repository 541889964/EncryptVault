
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
    val fatal: Boolean
)

object SecurityChecker {

    // 由构建脚本注入: 从 keystore 计算出的证书 SHA-256
    private const val EXPECTED_SIG_HASH = "eaec23308f3bb1ed24ba4c644ae73c9de40ab3126b7909a2cc16cf481903379b"

    fun check(ctx: Context): List<CheckItem> = listOf(
        checkSignatureStrict(ctx),
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
        checkInstallPath(ctx)
    )

    fun hasFatal(items: List<CheckItem>): Boolean =
        items.any { !it.passed && it.fatal }

    // ---------- 1. 严格签名检测 ----------
    private fun checkSignatureStrict(ctx: Context): CheckItem {
        try {
            val pm = ctx.packageManager
            val pkg = ctx.packageName

            // 获取签名信息
            val info = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }

            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            } ?: return CheckItem("签名", false, "无法读取签名", true)

            if (sigs.isEmpty())
                return CheckItem("签名", false, "空签名", true)

            // 多重签名 = 重打包特征
            if (sigs.size > 1)
                return CheckItem("签名", false, "签名者数量=${sigs.size}", true)

            // 计算每个签名的 SHA-256
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(sigs[0].toByteArray())
                .joinToString("") { "%02x".format(it) }

            if (hash != EXPECTED_SIG_HASH) {
                return CheckItem("签名", false,
                    "SHA256: ${hash.take(16)}... ≠ 预期", true)
            }

            // 校验签名方案 (Android 7+ 应含 v2)
            if (Build.VERSION.SDK_INT >= 28) {
                val si = info.signingInfo
                if (si != null) {
                    val hasV2 = si.hasMultipleSigners() ||
                                si.signingCertificateHistory != null
                    if (!hasV2) {
                        return CheckItem("签名", false, "缺少 v2 签名方案", true)
                    }
                }
            }

            return CheckItem("签名", true,
                "SHA256 匹配 · v1/v2/v3", true)
        } catch (e: Exception) {
            return CheckItem("签名", false, "异常: ${e.message}", true)
        }
    }

    // ---------- 2. Root 基础检测 ----------
    private fun checkRootBasic(): CheckItem {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/system/xbin/daemonsu", "/system/xbin/sugote",
            "/system/xbin/sugote-mksh", "/system/xbin/ku.sud",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
            "/system/etc/init.d/99SuperSUDaemon",
            "/system/bin/.ext/.su", "/system/usr/we-need-root"
        )
        for (p in paths) {
            if (File(p).exists() && File(p).canRead())
                return CheckItem("Root 基础", false, "su: $p", false)
        }
        // which su
        try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val line = p.inputStream.bufferedReader().readLine()
            if (!line.isNullOrEmpty())
                return CheckItem("Root 基础", false, "which su: $line", false)
        } catch (_: Exception) {}
        // Build.TAGS
        if (Build.TAGS?.contains("test-keys") == true)
            return CheckItem("Root 基础", false, "test-keys", false)
        return CheckItem("Root 基础", true, "未检测到 su", false)
    }

    // ---------- 3. Magisk ----------
    private fun checkMagisk(): CheckItem {
        val paths = listOf(
            "/data/adb/magisk", "/data/adb/magisk.img", "/data/adb/magisk.db",
            "/data/adb/magisk_simple", "/data/adb/modules", "/data/adb/modules_update",
            "/sbin/.magisk", "/sbin/.core/mirror", "/sbin/.core/img",
            "/cache/.disable_magisk", "/cache/magisk.log",
            "/dev/.magisk.unblock", "/dev/magisk"
        )
        for (p in paths) {
            if (File(p).exists()) return CheckItem("Magisk", false, "文件: $p", true)
        }
        for (prop in listOf(
            "ro.magisk.version", "ro.magisk.versionCode",
            "init.svc.magisk", "init.svc.magisk_pfs", "init.svc.magisk_pfsd",
            "init.svc.magisk_service", "persist.magisk.hide",
            "ro.boot.magisk"
        )) {
            val v = getProp(prop)
            if (!v.isNullOrEmpty())
                return CheckItem("Magisk", false, "prop $prop=$v", true)
        }
        try {
            val mounts = File("/proc/mounts").readText()
            if (mounts.contains("magisk", true))
                return CheckItem("Magisk", false, "mounts 含 magisk", true)
        } catch (_: Exception) {}
        return CheckItem("Magisk", true, "未检测到", true)
    }

    // ---------- 4. KernelSU ----------
    private fun checkKernelSU(): CheckItem {
        val paths = listOf(
            "/data/adb/ksu", "/data/adb/ksud", "/data/adb/modules_kernelsu",
            "/data/adb/ksu/modules", "/dev/ksu", "/dev/kernelsu",
            "/system/lib/modules/kernelsu.ko",
            "/system/lib64/modules/kernelsu.ko",
            "/debug_ramdisk/ksud", "/data/adb/ksu_service"
        )
        for (p in paths) {
            if (File(p).exists()) return CheckItem("KernelSU", false, "文件: $p", true)
        }
        try {
            val m = File("/proc/modules").readText()
            if (m.contains("kernelsu", true))
                return CheckItem("KernelSU", false, "/proc/modules", true)
        } catch (_: Exception) {}
        for (prop in listOf("ro.kernelsu.version", "persist.kernelsu.enabled")) {
            val v = getProp(prop)
            if (!v.isNullOrEmpty())
                return CheckItem("KernelSU", false, "prop $prop=$v", true)
        }
        return CheckItem("KernelSU", true, "未检测到", true)
    }

    // ---------- 5. Zygisk ----------
    private fun checkZygisk(): CheckItem {
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("zygisk", true) || maps.contains("shamiko", true))
                return CheckItem("Zygisk/Shamiko", false, "内存映射", true)
        } catch (_: Exception) {}
        for (p in listOf("/data/adb/modules/zygisk", "/data/adb/modules/shamiko")) {
            if (File(p).exists()) return CheckItem("Zygisk/Shamiko", false, "文件: $p", true)
        }
        return CheckItem("Zygisk/Shamiko", true, "未检测到", true)
    }

    // ---------- 6. Frida ----------
    private fun checkFrida(): CheckItem {
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }
            return CheckItem("Frida", false, "端口 27042 开放", true)
        } catch (_: Exception) {}
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida", true) || maps.contains("gum-js-loop", true))
                return CheckItem("Frida", false, "内存映射", true)
        } catch (_: Exception) {}
        try {
            val ps = ProcessBuilder("ps", "-A").start()
            val r = BufferedReader(InputStreamReader(ps.inputStream))
            r.forEachLine { line ->
                if (line.contains("frida", true) || line.contains("linjector", true))
                    return CheckItem("Frida", false, "进程: $line", true)
            }
        } catch (_: Exception) {}
        return CheckItem("Frida", true, "未检测到", true)
    }

    // ---------- 7. Xposed ----------
    private fun checkXposed(): CheckItem {
        for (cls in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage",
            "de.robv.android.xposed.callbacks.XC_LoadPackage"
        )) {
            try {
                Class.forName(cls)
                return CheckItem("Xposed", false, "类: $cls", true)
            } catch (_: Throwable) {}
        }
        for (p in listOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar"
        )) {
            if (File(p).exists()) return CheckItem("Xposed", false, "文件: $p", true)
        }
        return CheckItem("Xposed", true, "未检测到", true)
    }

    // ---------- 8. 调试器 ----------
    private fun checkDebugger(): CheckItem {
        if (Debug.isDebuggerConnected())
            return CheckItem("调试器", false, "已连接", true)
        if (Debug.waitingForDebugger())
            return CheckItem("调试器", false, "等待调试", true)
        try {
            val s = File("/proc/self/status").readText()
            val m = Regex("TracerPid:\\s*(\\d+)").find(s)
            if (m != null && m.groupValues[1] != "0")
                return CheckItem("调试器", false, "TracerPid=${m.groupValues[1]}", true)
        } catch (_: Exception) {}
        return CheckItem("调试器", true, "未检测到", true)
    }

    // ---------- 9. 模拟器 ----------
    private fun checkEmulator(): CheckItem {
        val fp = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val product = Build.PRODUCT ?: ""
        val hw = Build.HARDWARE ?: ""
        if (fp.startsWith("generic/") || fp.contains("sdk_gphone"))
            return CheckItem("模拟器", false, "FP=$fp", false)
        if (product.contains("sdk_gphone") || product.contains("emulator"))
            return CheckItem("模拟器", false, "PRODUCT=$product", false)
        if (model.contains("Android SDK built for") || model.contains("sdk_gphone"))
            return CheckItem("模拟器", false, "MODEL=$model", false)
        if (hw.contains("goldfish") || hw.contains("ranchu"))
            return CheckItem("模拟器", false, "HW=$hw", false)
        return CheckItem("模拟器", true, "真机", false)
    }

    // ---------- 10. 虚拟多开 ----------
    private fun checkVirtualApp(): CheckItem {
        val n = try {
            File("/proc/${android.os.Process.myPid()}/cmdline")
                .readText().trimEnd('\u0000')
        } catch (_: Exception) { "" }
        if (n.isEmpty()) return CheckItem("虚拟多开", true, "无法判定", false)
        for (k in listOf("vxp","virtual","clone","sandbox","titan","vmos",
            "lody","parallel","dual.","dual_")) {
            if (n.contains(k, true))
                return CheckItem("虚拟多开", false, "进程含 $k", false)
        }
        return CheckItem("虚拟多开", true, "正常", false)
    }

    // ---------- 11. VPN/代理 ----------
    private fun checkVPNAndProxy(): CheckItem {
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        if (!proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty())
            return CheckItem("代理", false, "Proxy=$proxyHost:$proxyPort", false)
        try {
            val env = System.getenv("http_proxy") ?: System.getenv("HTTP_PROXY")
            if (!env.isNullOrEmpty())
                return CheckItem("代理", false, "ENV=$env", false)
        } catch (_: Exception) {}
        return CheckItem("代理", true, "未检测到", false)
    }

    // ---------- 12. 安装路径 ----------
    private fun checkInstallPath(ctx: Context): CheckItem {
        val src = ctx.packageResourcePath ?: return CheckItem("安装路径", true, "未知", false)
        for (b in listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/", "/mnt/")) {
            if (src.contains(b))
                return CheckItem("安装路径", false, "可疑: $src", true)
        }
        return CheckItem("安装路径", true, "正常", false)
    }

    // ---------- 工具 ----------
    private fun getProp(key: String): String? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
        p.inputStream.bufferedReader().readLine()?.trim()
    } catch (_: Exception) { null }
}
