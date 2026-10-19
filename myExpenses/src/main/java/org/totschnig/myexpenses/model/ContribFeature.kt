package org.totschnig.myexpenses.model

import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.util.licence.LicenceStatus

class ContribFeatureNotAvailableException(message: String): Exception(message)

enum class ContribFeature(
    val licenceStatus: LicenceStatus = LicenceStatus.PROFESSIONAL
) {
    ACCOUNTS_UNLIMITED,
    PLANS_UNLIMITED,
    SPLIT_TRANSACTION,
    DISTRIBUTION,
    PRINT,
    AD_FREE,
    CSV_IMPORT,
    SYNCHRONIZATION,
    SPLIT_TEMPLATE,
    PRO_SUPPORT,
    ROADMAP_VOTING,
    HISTORY,
    BUDGET,
    OCR,
    WEB_UI,
    CATEGORY_TREE,
    BANKING,
    AUTOMATIC_FX_DOWNLOAD,
    PORTFOLIO;

    override fun toString(): String {
        return name.lowercase()
    }

    val prefKey: String
        get() = "FEATURE_${name}_FIRST_USAGE"

    val labelResId: Int
        get() = when (this) {
            ACCOUNTS_UNLIMITED -> R.string.contrib_feature_accounts_unlimited_label
            PLANS_UNLIMITED -> R.string.contrib_feature_plans_unlimited_label
            SPLIT_TRANSACTION -> R.string.contrib_feature_split_transaction_label
            DISTRIBUTION -> R.string.contrib_feature_distribution_label
            PRINT -> R.string.contrib_feature_print_label
            AD_FREE -> R.string.contrib_feature_ad_free_label
            CSV_IMPORT -> R.string.contrib_feature_csv_import_label
            SYNCHRONIZATION -> R.string.contrib_feature_synchronization_label
            SPLIT_TEMPLATE -> R.string.contrib_feature_split_template_label
            PRO_SUPPORT -> R.string.contrib_feature_pro_support_label
            ROADMAP_VOTING -> R.string.contrib_feature_roadmap_voting_label
            HISTORY -> R.string.contrib_feature_history_label
            BUDGET -> R.string.contrib_feature_budget_label
            OCR -> R.string.contrib_feature_ocr_label
            WEB_UI -> R.string.contrib_feature_web_ui_label
            CATEGORY_TREE -> R.string.contrib_feature_category_tree_label
            BANKING -> R.string.title_fints
            AUTOMATIC_FX_DOWNLOAD -> R.string.enable_automatic_daily_exchange_rate_download
            PORTFOLIO -> R.string.contrib_feature_portfolio_tracking
        }
}
