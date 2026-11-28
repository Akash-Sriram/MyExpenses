package org.totschnig.myexpenses.viewmodel.data

import android.database.Cursor
import androidx.core.database.getStringOrNull
import org.totschnig.myexpenses.provider.KEY_ACCOUNT_TYPE_LIST
import org.totschnig.myexpenses.provider.KEY_ICON
import org.totschnig.myexpenses.provider.KEY_IS_NUMBERED
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.provider.getBoolean
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getString

data class PaymentMethod(
    val id: Long,
    val label: String,
    val isNumbered: Boolean,
    val type: Int = 0,
    val accountTypes: List<Long> = emptyList(),
    val icon: String? = null
) {
    override fun toString(): String {
        return label
    }

    companion object {
        fun from(cursor: Cursor): PaymentMethod {
            val typeCol = cursor.getColumnIndex(KEY_TYPE)
            val iconCol = cursor.getColumnIndex(KEY_ICON)
            val accountTypesCol = cursor.getColumnIndex(KEY_ACCOUNT_TYPE_LIST)
            val accountTypes = if (accountTypesCol >= 0 && !cursor.isNull(accountTypesCol)) {
                cursor.getString(accountTypesCol).split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
            } else {
                emptyList()
            }
            return PaymentMethod(
                id = cursor.getLong(KEY_ROWID),
                label = cursor.getString(KEY_LABEL),
                isNumbered = cursor.getBoolean(KEY_IS_NUMBERED),
                type = if (typeCol >= 0) cursor.getInt(typeCol) else 0,
                accountTypes = accountTypes,
                icon = if (iconCol >= 0) cursor.getStringOrNull(iconCol) else null
            )
        }
    }
}

