package org.totschnig.myexpenses.testutils

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.test.mock.MockContentResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.totschnig.myexpenses.TestApp
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.deleteAccount
import org.totschnig.myexpenses.db2.findAccountType
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CASH
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.provider.TransactionProvider
import java.io.File

@RunWith(AndroidJUnit4::class)
open class BaseProviderTest {
    private lateinit var transactionProvider: TransactionProvider
    private lateinit var targetContextWrapper: Context
    private lateinit var resolver: MockContentResolver

    val persistedPermissions = mutableSetOf<Uri>()

    val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    val context: Context
        get() = targetContext

    val app: TestApp
        get() = targetContext.applicationContext as TestApp

    val homeCurrency: CurrencyUnit
        get() = app.appComponent.currencyContext().homeCurrencyUnit

    val prefHandler: PrefHandler
        get() = app.appComponent.prefHandler()

    protected val repository: Repository
        get() = Repository(
            targetContextWrapper,
            app.appComponent.currencyContext(),
            prefHandler,
            app.appComponent.preferencesDataStore(),
            app.appComponent.plannerUtils(),
            app.appComponent.preferencesHolder()
        )

    val contentResolver: ContentResolver
        get() = repository.contentResolver

    fun buildAccount(label: String, openingBalance: Long = 0L, syncAccountName: String? = null) =
        Account(
            label = label,
            currency = homeCurrency.code,
            openingBalance = openingBalance,
            syncAccountName = syncAccountName,
            type = cashAccount
        ).createIn(repository)

    fun deleteAccount(id: Long) {
        repository.deleteAccount(id)
    }

    val cashAccount: AccountType
        get() = repository.findAccountType(PREDEFINED_NAME_CASH)!!

    @Before
    @Throws(Exception::class)
    open fun setUp() {
        transactionProvider = object : TransactionProvider() {
            override fun takePersistableUriPermission(uri: Uri) {
                persistedPermissions.add(uri)
            }

            override fun releasePersistableUriPermission(uri: Uri) {
                persistedPermissions.remove(uri)
            }
        }
        resolver = MockContentResolver()
        val filenamePrefix = "test."
        targetContextWrapper = RenamingDelegatingContext(
            DelegatedMockContext(resolver),
            context,
            filenamePrefix
        )
        assertNotNull(transactionProvider)
        val providerInfo = ProviderInfo()
        providerInfo.authority = TransactionProvider.AUTHORITY
        transactionProvider.attachInfo(IsolatedContext(resolver, context), providerInfo)
        resolver.addProvider(TransactionProvider.AUTHORITY, transactionProvider)
    }

    @After
    @Throws(Exception::class)
    open fun tearDown() {
    }

    val mockContentResolver: MockContentResolver get() = resolver

    val provider: TransactionProvider get() = transactionProvider

    fun fail(message: String? = null): Nothing {
        org.junit.Assert.fail(message)
        throw AssertionError(message)
    }

    fun assertEquals(expected: Any?, actual: Any?) {
        org.junit.Assert.assertEquals(expected, actual)
    }

    fun assertEquals(message: String?, expected: Any?, actual: Any?) {
        org.junit.Assert.assertEquals(message, expected, actual)
    }

    fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }

    fun assertTrue(message: String?, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }

    fun assertFalse(condition: Boolean) {
        org.junit.Assert.assertFalse(condition)
    }

    fun assertFalse(message: String?, condition: Boolean) {
        org.junit.Assert.assertFalse(message, condition)
    }

    fun assertNotNull(actual: Any?) {
        org.junit.Assert.assertNotNull(actual)
    }

    fun assertNotNull(message: String?, actual: Any?) {
        org.junit.Assert.assertNotNull(message, actual)
    }

    fun assertNull(actual: Any?) {
        org.junit.Assert.assertNull(actual)
    }

    fun assertNull(message: String?, actual: Any?) {
        org.junit.Assert.assertNull(message, actual)
    }

    private inner class DelegatedMockContext(val resolver: ContentResolver) : ContextWrapper(context) {
        override fun createConfigurationContext(overrideConfiguration: Configuration) = this
        override fun getResources(): Resources = context.resources
        override fun getDir(name: String, mode: Int): File =
            context.getDir("mockContext2_$name", mode)

        override fun getApplicationContext() = this
        override fun getContentResolver() = resolver
    }

    private class IsolatedContext(
        private val resolver: ContentResolver,
        base: Context
    ) : ContextWrapper(base) {
        override fun getContentResolver(): ContentResolver = resolver
    }

    private class RenamingDelegatingContext(
        base: Context,
        private val fileContext: Context,
        private val prefix: String
    ) : ContextWrapper(base) {
        override fun getDatabasePath(name: String): File =
            fileContext.getDatabasePath(prefix + name)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase =
            fileContext.openOrCreateDatabase(prefix + name, mode, factory)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?
        ): SQLiteDatabase =
            fileContext.openOrCreateDatabase(prefix + name, mode, factory, errorHandler)

        override fun deleteDatabase(name: String): Boolean =
            fileContext.deleteDatabase(prefix + name)
    }
}