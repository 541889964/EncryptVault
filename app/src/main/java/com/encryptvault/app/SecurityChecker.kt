
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

    private const val EXPECTED_SIG_HASH = "eaec23308f3bb1ed24ba4c644ae73c9de40ab3126b7909a2cc16cf481903379b"

    fun check(ctx: Context): List<CheckItem> = listOf(
        // ==================== 致命 8 项 ====================
        checkSignature(ctx),        // 1. 签名 SHA-256
        checkSignatureCount(ctx),   // 2. 签名者数量
        checkInstallPath(ctx),      // 3. 安装路径
        checkFrida(),               // 4. Frida
        checkXposed(),              // 5. Xposed
        checkDebugger(),            // 6. 调试器
        checkEmulator(),            // 7. 模拟器
        checkVirtualApp(),          // 8. 虚拟多开
        // ==================== 警告 5 项 ====================
        checkRootBasic(),           // Root (仅警告)
        checkMagisk(),              // Magisk (仅警告)
        checkKernelSU(),            // KernelSU (仅警告)
        checkZygisk(),              // Zygisk (仅警告)
        checkSELinux()              // SELinux (仅警告)
    )

    fun hasFatal(items: List<CheckItem>): Boolean =
        items.any { !it.passed && it.fatal }

    // ============================================================
    //  致命 1: 签名 SHA-256 (防二改核心)
    // ============================================================
    private fun checkSignature(ctx: Context): CheckItem {
        try {
            val pm = ctx.packageManager
            val info = if (Build.VERSION.SDK_INT >= 28)
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            else
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)

            val sigs = if (Build.VERSION.SDK_INT >= 28)
                info.signingInfo?.apkContentsSigners
            else
                @Suppress("DEPRECATION")
                info.signatures
                ?: return CheckItem("签名", false, "无法读取签名信息", true)

            if (sigs.isEmpty()) return CheckItem("签名", false, "空签名列表", true)

            val hash = MessageDigest.getInstance("SHA-256")
                .digest(sigs[0].toByteArray())
                .joinToString("") { "%02x".format(it) }

            if (hash != EXPECTED_SIG_HASH) {
                return CheckItem("签名", false,
                    "SHA-256 不匹配 (${hash.take(12)}...)", true)
            }
            return CheckItem("签名", true, "xuanyi 已校验", true)
        } catch (e: Exception) {
            return CheckItem("签名", false, "读取异常: ${e.message}", true)
        }
    }

    // ============================================================
    //  致命 2: 签名者数量 (多签名 = 重打包特征)
    // ============================================================
    private fun checkSignatureCount(ctx: Context): CheckItem {
        try {
            val pm = ctx.packageManager
            val info = if (Build.VERSION.SDK_INT >= 28)
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            else
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)

            val sigs = if (Build.VERSION.SDK_INT >= 28)
                info.signingInfo?.apkContentsSigners
            else
                @Suppress("DEPRECATION")
                info.signatures
                ?: return CheckItem("签名者", false, "无法读取", true)

            if (sigs.size != 1) {
                return CheckItem("签名者", false,
                    "数量=${sigs.size} (应为 1)", true)
            }
            return CheckItem("签名者", true, "唯一", true)
        } catch (e: Exception) {
            return CheckItem("签名者", false, "异常: ${e.message}", true)
        }
    }

    // ============================================================
    //  致命 3: 安装路径
    // ============================================================
    private fun checkInstallPath(ctx: Context): CheckItem {
        val src = ctx.packageResourcePath
            ?: return CheckItem("安装路径", true, "无法获取", true)
        // 只信任 /data/app/
        if (!src.startsWith("/data/app/")) {
            return CheckItem("安装路径", false, "非正规: $src", true)
        }
        // 拒绝软链/临时目录
        for (b in listOf("/tmp/", "/cache/", "/sdcard/", "/data/local/", "/mnt/")) {
            if (src.contains(b)) return CheckItem("安装路径", false, "可疑: $src", true)
        }
        return CheckItem("安装路径", true, "/data/app/", true)
    }

    // ============================================================
    //  致命 4: Frida (多重精准特征)
    // ============================================================
    private fun checkFrida(): CheckItem {
        // 特征 1: Frida 默认端口
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 100) }
            return CheckItem("Frida", false, "端口 27042 开放", true)
        } catch (_: Exception) {}
        // 特征 2: Frida server 端口
        try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27043), 100) }
            return CheckItem("Frida", false, "端口 27043 开放", true)
        } catch (_: Exception) {}
        // 特征 3: maps 含 frida / gum-js-loop
        try {
            val m = File("/proc/self/maps").readText()
            if (m.contains("frida", true))
                return CheckItem("Frida", false, "/proc/self/maps 含 frida", true)
            if (m.contains("gum-js-loop", true))
                return CheckItem("Frida", false, "maps 含 gum-js-loop", true)
        } catch (_: Exception) {}
        // 特征 4: 进程列表 (精准匹配)
        try {
            val ps = ProcessBuilder("ps", "-A").start()
            val lines = BufferedReader(InputStreamReader(ps.inputStream)).readLines()
            val hit = lines.firstOrNull { l ->
                l.contains("frida-server", true) ||
                l.contains("frida-helper", true) ||
                l.contains("linjector", true)
            }
            if (hit != null) return CheckItem("Frida", false, "进程: ${hit.take(30)}", true)
        } catch (_: Exception) {}
        // 特征 5: 残留文件
        for (p in listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/data/local/tmp/frida-server-",
            "/data/local/tmp/frida"
        )) if (File(p).exists()) return CheckItem("Frida", false, "残留: $p", true)

        return CheckItem("Frida", true, "未检测到", true)
    }

    // ============================================================
    //  致命 5: Xposed
    // ============================================================
    private fun checkXposed(): CheckItem {
        for (cls in listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "de.robv.android.xposed.XposedHelpers",
            "de.robv.android.xposed.IXposedHookLoadPackage",
            "de.robv.android.xposed.callbacks.XC_LoadPackage",
            "de.robv.android.xposed.XposedInit"
        )) {
            try {
                Class.forName(cls)
                return CheckItem("Xposed", false, "类: $cls", true)
            } catch (_: Throwable) {}
        }
        for (p in listOf(
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed.so",
            "/system/lib64/libxposed.so",
            "/data/data/de.robv.android.xposed.installer"
        )) if (File(p).exists()) return CheckItem("Xposed", false, "文件: $p", true)

        return CheckItem("Xposed", true, "未检测到", true)
    }

    // ============================================================
    //  致命 6: 调试器
    // ============================================================
    private fun checkDebugger(): CheckItem {
        if (Debug.isDebuggerConnected())
            return CheckItem("调试器", false, "Debugger 已连接", true)
        if (Debug.waitingForDebugger())
            return CheckItem("调试器", false, "等待调试器附加", true)
        try {
            val s = File("/proc/self/status").readText()
            val m = Regex("TracerPid:\\s*(\\d+)").find(s)
            if (m != null && m.groupValues[1] != "0")
                return CheckItem("调试器", false, "TracerPid=${m.groupValues[1]}", true)
        } catch (_: Exception) {}
        return CheckItem("调试器", true, "未检测到", true)
    }

    // ============================================================
    //  致命 7: 模拟器 (只用最强特征, 绝不误报真机)
    // ============================================================
    private fun checkEmulator(): CheckItem {
        // 只信任 Android 官方 AOSP/goldfish/ranchu 特征
        val fp = Build.FINGERPRINT ?: ""
        val product = Build.PRODUCT ?: ""
        val hw = Build.HARDWARE ?: ""
        val model = Build.MODEL ?: ""

        // 特征 1: 硬件名 goldfish / ranchu (Android 官方模拟器)
        if (hw.equals("goldfish", true))
            return CheckItem("模拟器", false, "HARDWARE=goldfish", true)
        if (hw.equals("ranchu", true))
            return CheckItem("模拟器", false, "HARDWARE=ranchu", true)
        // 特征 2: 产品名 sdk_gphone*
        if (product.startsWith("sdk_gphone", true))
            return CheckItem("模拟器", false, "PRODUCT=$product", true)
        // 特征 3: 指纹 generic/
        if (fp.startsWith("generic/", true))
            return CheckItem("模拟器", false, "FINGERPRINT=generic", true)
        // 特征 4: 型号 Android SDK built for
        if (model.contains("Android SDK built for", true))
            return CheckItem("模拟器", false, "MODEL=$model", true)
        // 特征 5: QEMU 特征文件
        if (File("/system/bin/qemu-props").exists())
            return CheckItem("模拟器", false, "/system/bin/qemu-props", true)
        if (File("/dev/socket/qemud").exists())
            return CheckItem("模拟器", false, "/dev/socket/qemud", true)

        return CheckItem("模拟器", true, "真机", true)
    }

    // ============================================================
    //  致命 8: 虚拟多开
    // ============================================================
    private fun checkVirtualApp(): CheckItem {
        val n = try {
            File("/proc/${android.os.Process.myPid()}/cmdline")
                .readText().trimEnd('\u0000')
        } catch (_: Exception) { "" }
        if (n.isEmpty()) return CheckItem("虚拟多开", true, "无法判定", true)

        // 精准匹配多开框架的包名特征
        for (k in listOf(
            "io.va.exposed",            // VirtualApp
            "com.lody.virtual",         // VirtualApp
            "com.lbe.parallel",         // 平行空间
            "com.excelliance.multi",    // 双开助手
            "com.qihoo.magic",          // 360 分身
            "com.tencent.parallel",     // 腾讯双开
            "cn.vmos",                  // VMOS
            "com.vmos",
            "info.zzs.android",
            "com.cloneapp"
        )) if (n.contains(k, true)) return CheckItem("虚拟多开", false, "包名: $k", true)

        return CheckItem("虚拟多开", true, "正常", true)
    }

    // ============================================================
    //  警告 1: Root (su 文件)
    // ============================================================
    private fun checkRootBasic(): CheckItem {
        for (p in listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/data/local/su", "/data/local/bin/su",
            "/data/local/xbin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/system/xbin/daemonsu",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk"
        )) if (File(p).exists()) return CheckItem("Root", false, "su: $p", false)

        try {
            val line = Runtime.getRuntime().exec(arrayOf("which", "su"))
                .inputStream.bufferedReader().readLine()
            if (!line.isNullOrEmpty())
                return CheckItem("Root", false, "which su=$line", false)
        } catch (_: Exception) {}

        return CheckItem("Root", true, "未检测到", false)
    }

    // ============================================================
    //  警告 2: Magisk
    // ============================================================
    private fun checkMagisk(): CheckItem {
        for (p in listOf(
            "/data/adb/magisk", "/data/adb/magisk.img", "/data/adb/magisk.db",
            "/data/adb/modules", "/sbin/.magisk", "/sbin/.core/mirror",
            "/sbin/.core/img", "/cache/.disable_magisk", "/dev/magisk"
        )) if (File(p).exists()) return CheckItem("Magisk", false, "文件: $p", false)
        for (prop in listOf(
            "ro.magisk.version", "init.svc.magisk",
            "init.svc.magisk_pfs", "persist.magisk.hide"
        )) {
            val v = getProp(prop)
            if (!v.isNullOrEmpty())
                return CheckItem("Magisk", false, "$prop=$v", false)
        }
        return CheckItem("Magisk", true, "未检测到", false)
    }

    // ============================================================
    //  警告 3: KernelSU
    // ============================================================
    private fun checkKernelSU(): CheckItem {
        for (p in listOf(
            "/data/adb/ksu", "/data/adb/ksud", "/data/adb/modules_kernelsu",
            "/dev/ksu", "/dev/kernelsu",
            "/system/lib/modules/kernelsu.ko",
            "/system/lib64/modules/kernelsu.ko"
        )) if (File(p).exists()) return CheckItem("KernelSU", false, "文件: $p", false)
        try {
            if (File("/proc/modules").readText().contains("kernelsu", true))
                return CheckItem("KernelSU", false, "/proc/modules 含 kernelsu", false)
        } catch (_: Exception) {}
        return CheckItem("KernelSU", true, "未检测到", false)
    }

    // ============================================================
    //  警告 4: Zygisk
    // ============================================================
    private fun checkZygisk(): CheckItem {
        try {
            val m = File("/proc/self/maps").readText()
            if (m.contains("zygisk", true))
                return CheckItem("Zygisk", false, "maps 含 zygisk", false)
        } catch (_: Exception) {}
        for (p in listOf(
            "/data/adb/modules/zygisk",
            "/data/adb/modules/shamiko"
        )) if (File(p).exists()) return CheckItem("Zygisk", false, "文件: $p", false)
        return CheckItem("Zygisk", true, "未检测到", false)
    }

    // ============================================================
    //  警告 5: SELinux (只读文件, 不用 getenforce 命令)
    // ============================================================
    private fun checkSELinux(): CheckItem {
        // 只读 /sys/fs/selinux/enforce 文件
        // 内容 "1" = Enforcing, "0" = Permissive
        try {
            val f = File("/sys/fs/selinux/enforce")
            if (f.exists() && f.canRead()) {
                val v = f.readText().trim()
                if (v == "0") return CheckItem("SELinux", false, "Permissive", false)
                if (v == "1") return CheckItem("SELinux", true, "Enforcing", false)
            }
        } catch (_: Exception) {}
        // 读不到 → 视为通过 (不误报)
        return CheckItem("SELinux", true, "Enforcing", false)
    }

    private fun getProp(key: String): String? = try {
        Runtime.getRuntime().exec(arrayOf("getprop", key))
            .inputStream.bufferedReader().readLine()?.trim()
    } catch (_: Exception) { null }
}
