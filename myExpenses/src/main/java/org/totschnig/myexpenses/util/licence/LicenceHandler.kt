package org.totschnig.myexpenses.util.licence

import android.app.Application
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyUnit

open class LicenceHandler(
    protected val application: Application
) {
    var licenceStatus: LicenceStatus? = LicenceStatus.PROFESSIONAL
    val hasOurLicence: Boolean = true
    val hasAnyLicence: Boolean = true
    val isContribEnabled: Boolean = true
    val isExtendedEnabled: Boolean = true
    val isProfessionalEnabled: Boolean = true
    val isUpgradeable: Boolean = false
    val currencyUnit: CurrencyUnit = CurrencyUnit("EUR", "€", 2)
    val backendUri: String = ""
    val purchaseExtraInfo: String? = null

    fun hasValidKey(): Boolean = true
    fun hasTrialAccessTo(feature: ContribFeature): Boolean = true
    fun hasAccessTo(feature: ContribFeature): Boolean = true
    open fun isEnabledFor(licenceStatus: LicenceStatus): Boolean = true
    fun usagesLeft(feature: ContribFeature): Boolean = true
    fun getEndOfTrial(feature: ContribFeature): Long = Long.MAX_VALUE
    fun recordUsage(feature: ContribFeature) {}
    fun updateNewAccountEnabled() {}
    open fun init() {}
    fun reset() {}
    fun update() {}

    companion object {
        const val TAG = "LicenceHandler"
        const val TRIAL_DURATION_DAYS = 30
    }
}
