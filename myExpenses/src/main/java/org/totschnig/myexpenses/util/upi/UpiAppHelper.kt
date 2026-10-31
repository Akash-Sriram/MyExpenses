package org.totschnig.myexpenses.util.upi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

data class UpiAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isKnownUpiApp: Boolean = false
)

object UpiAppHelper {

    val KNOWN_UPI_PACKAGES = mapOf(
        "com.csam.icici.bank.imobile" to "iMobile Pay (ICICI)",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
        "com.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.sbi.upi" to "BHIM SBI Pay",
        "com.axis.mobile" to "Axis Mobile",
        "com.msf.kbank.mobile" to "Kotak Bank",
        "com.hdfcbank.payzapp" to "HDFC PayZapp",
        "com.whatsapp" to "WhatsApp Pay"
    )

    /**
     * Discovers all installed applications capable of handling `upi://pay` intents.
     */
    fun getInstalledUpiApps(context: Context): List<UpiAppInfo> {
        val packageManager = context.packageManager
        val apps = mutableListOf<UpiAppInfo>()
        val seenPackages = mutableSetOf<String>()

        // 1. Explicitly check known UPI apps declared in queries
        for ((pkg, defaultLabel) in KNOWN_UPI_PACKAGES) {
            try {
                val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(pkg, 0)
                }
                if (appInfo.enabled && !seenPackages.contains(pkg)) {
                    seenPackages.add(pkg)
                    val label = packageManager.getApplicationLabel(appInfo).toString().ifBlank { defaultLabel }
                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (_: Exception) {
                        null
                    }
                    apps.add(
                        UpiAppInfo(
                            packageName = pkg,
                            appName = label,
                            icon = icon,
                            isKnownUpiApp = true
                        )
                    )
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            } catch (_: Exception) {
                // Ignore
            }
        }

        // 2. Query any additional generic UPI-compatible activities
        val intentList = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")),
            Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=dummy@upi"))
        )
        for (upiIntent in intentList) {
            val resolveInfos = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(upiIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(upiIntent, 0)
                }
            } catch (_: Exception) {
                emptyList()
            }

            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || seenPackages.contains(pkg)) continue
                seenPackages.add(pkg)

                val label = KNOWN_UPI_PACKAGES[pkg] ?: info.loadLabel(packageManager).toString().ifBlank { pkg }
                val icon = try {
                    info.loadIcon(packageManager)
                } catch (_: Exception) {
                    null
                }

                apps.add(
                    UpiAppInfo(
                        packageName = pkg,
                        appName = label,
                        icon = icon,
                        isKnownUpiApp = KNOWN_UPI_PACKAGES.containsKey(pkg)
                    )
                )
            }
        }

        return apps.sortedWith(
            compareByDescending<UpiAppInfo> { it.isKnownUpiApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Builds an explicit Intent directed to the chosen UPI app with sanitized URI.
     */
    fun createUpiIntent(app: UpiAppInfo, qrData: UpiQrData): Intent {
        val uri = qrData.toCleanUri()
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(app.packageName)
        }
    }
}
