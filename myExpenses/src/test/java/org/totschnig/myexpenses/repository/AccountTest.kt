package org.totschnig.myexpenses.repository

import android.database.sqlite.SQLiteConstraintException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.db2.deleteAccount
import org.totschnig.myexpenses.db2.findMatchingAccountForSms
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.insertTransfer
import org.totschnig.myexpenses.db2.setAccountProperty
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.provider.KEY_SEALED

@RunWith(RobolectricTestRunner::class)
class AccountTest: BaseTestWithRepository() {

    @Test(expected = SQLiteConstraintException::class)
    fun deleteTransactionInSealedAccount() {
        val currencyUnit = CurrencyUnit.DebugInstance
        val account = insertAccount(label= "Account 1", currency = currencyUnit.code, openingBalance = 100L)
        val transaction = repository.insertTransaction(accountId = account, amount = 100L)
        closeAccount(account)
        repository.deleteTransaction(transaction.id)
    }

    @Test
    fun deleteAccountWithTransferLinkedToSealedAccount() {
        val currencyUnit = CurrencyUnit.DebugInstance
        val account1 = insertAccount(label= "Account 1", currency = currencyUnit.code, openingBalance = 100L)
        val account2 = insertAccount(label= "Account 2", currency = currencyUnit.code, openingBalance = 100L)
        repository.insertTransfer(account1, account2, 100L)
        closeAccount(account2)
        repository.deleteAccount(account1)
    }

    @Test
    fun testFindMatchingAccountForSms() {
        val cash = insertAccount(label = "Cash Wallet", currency = "INR")
        val icici = insertAccount(label = "Icici Bank Accounts", currency = "INR", description = "Savings A/C XX407")
        val hdfc = insertAccount(label = "HDFC Salary", currency = "INR", description = "A/C 1234")

        // 1. Matches both bank hint and account digits in description
        val matchBoth = repository.findMatchingAccountForSms(
            bankHint = "ICICI",
            accountDigits = "407",
            currency = "INR"
        )
        org.junit.Assert.assertNotNull(matchBoth)
        org.junit.Assert.assertEquals(icici, matchBoth!!.id)
        org.junit.Assert.assertEquals("Icici Bank Accounts", matchBoth.label)

        // 2. Matches bank hint substring without account digits
        val matchBank = repository.findMatchingAccountForSms(
            bankHint = "ICICI",
            accountDigits = null,
            currency = "INR"
        )
        org.junit.Assert.assertNotNull(matchBank)
        org.junit.Assert.assertEquals(icici, matchBank!!.id)

        // 3. Matches account digits without bank hint
        val matchDigits = repository.findMatchingAccountForSms(
            bankHint = null,
            accountDigits = "1234",
            currency = "INR"
        )
        org.junit.Assert.assertNotNull(matchDigits)
        org.junit.Assert.assertEquals(hdfc, matchDigits!!.id)
    }

    private fun closeAccount(accountId: Long) {
        repository.setAccountProperty(accountId, KEY_SEALED, true)
    }
}