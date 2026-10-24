package org.totschnig.myexpenses.provider.filter

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_TRANSFER_PEER

class UniversalSearchCriterionTest {

    @Test
    fun testTextQueryGeneratesMultiFieldLikeSelection() {
        val criterion = UniversalSearchCriterion(query = "Accenture")
        val selection = criterion.getSelectionForParents()

        assertThat(selection).contains(KEY_COMMENT)
        assertThat(selection).contains(KEY_PAYEE_NAME)
        assertThat(selection).contains("Accenture")
        assertThat(criterion.getSelectionArgs(false)).isEmpty()
    }

    @Test
    fun testNumericQueryGeneratesAmountMatching() {
        val criterion = UniversalSearchCriterion(query = "489")
        val selection = criterion.getSelectionForParents()

        assertThat(selection).contains(KEY_AMOUNT)
        // 489 as whole number matches both 489 minor units and 48900 (paise)
        assertThat(selection).contains("abs(amount) = 489")
        assertThat(selection).contains("abs(amount) = 48900")
    }

    @Test
    fun testIncomeFilterType() {
        val criterion = UniversalSearchCriterion(typeFilter = "INCOME")
        val selection = criterion.getSelectionForParents()

        assertThat(selection).contains("$KEY_AMOUNT > 0")
    }

    @Test
    fun testExpenseFilterType() {
        val criterion = UniversalSearchCriterion(typeFilter = "EXPENSE")
        val selection = criterion.getSelectionForParents()

        assertThat(selection).contains("$KEY_AMOUNT < 0")
    }

    @Test
    fun testTransferFilterType() {
        val criterion = UniversalSearchCriterion(typeFilter = "TRANSFER")
        val selection = criterion.getSelectionForParents()

        assertThat(selection).contains("$KEY_TRANSFER_PEER IS NOT NULL")
    }

    @Test
    fun testCombinedQueryAndType() {
        val criterion = UniversalSearchCriterion(query = "Swiggy", typeFilter = "EXPENSE")
        val selection = criterion.getSelectionForParents()

        assertThat(selection).contains("Swiggy")
        assertThat(selection).contains("$KEY_AMOUNT < 0")
    }
}
