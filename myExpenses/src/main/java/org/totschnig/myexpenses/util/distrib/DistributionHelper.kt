package org.totschnig.myexpenses.util.distrib

import android.content.Context
import org.totschnig.myexpenses.BuildConfig
import java.time.format.DateTimeFormatter

object DistributionHelper {
    val platform: String
        get() = "Android"

    val marketPrefix: String
        get() = distribution.marketPrefix

    val marketSelfUri: String
        get() = distribution.marketSelfUri

    val distribution: Distribution
        get() = Distribution.valueOf(BuildConfig.DISTRIBUTION)

    val distributionAsString: String
        get() = distribution.toString()

    val isPlay: Boolean
        get() = distribution == Distribution.PLAY

    val isAmazon: Boolean
        get() = distribution == Distribution.AMAZON

    val isGithub: Boolean
        get() = distribution == Distribution.GITHUB

    /**
     * retrieve information about the current version
     *
     * @return concatenation of versionName, versionCode and buildTime
     * buildTime is automatically stored in property file during build process
     */
    fun getVersionInfo(ctx: Context): String {
        val installer = try {
            ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
        } catch (_: Exception) {
            null
        }
        return "${BuildConfig.VERSION_NAME} ($versionNumber) $buildDateFormatted $distributionAsString $installer"
    }

    val buildDateFormatted: String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(BuildConfig.BUILD_DATE)

    /**
     * @return version number (versionCode)
     */
    val versionNumber: Int
        get() = BuildConfig.VERSION_CODE

    val versionName: String
        get() = BuildConfig.VERSION_NAME

    enum class Distribution(
        val hasDynamicFeatureDelivery: Boolean = false,
        val supportsTrackingAndCrashReporting: Boolean = true,
        val marketPrefix: String = "market://details?id="
    ) {
        PLAY(hasDynamicFeatureDelivery = true),
        AMAZON(marketPrefix = "amzn://apps/android?p="),
        GITHUB(supportsTrackingAndCrashReporting = false),
        HUAWEI;

        val marketSelfUri: String
            get() = marketPrefix + "org.totschnig.myexpenses"

    }
}