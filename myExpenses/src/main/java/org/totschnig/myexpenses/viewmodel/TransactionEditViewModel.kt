package org.totschnig.myexpenses.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_PINNED
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.application
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import timber.log.Timber
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_SPLIT
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_TRANSFER
import org.totschnig.myexpenses.db2.RepositoryTemplate
import org.totschnig.myexpenses.db2.addAttachments
import org.totschnig.myexpenses.db2.createPlan
import org.totschnig.myexpenses.db2.createTemplate
import org.totschnig.myexpenses.db2.createTransaction
import org.totschnig.myexpenses.db2.deleteAttachments
import org.totschnig.myexpenses.db2.entities.Plan
import org.totschnig.myexpenses.db2.entities.Recurrence
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.db2.getCurrencyUnitForAccount
import org.totschnig.myexpenses.db2.getLastUsedOpenAccount
import org.totschnig.myexpenses.db2.linkTemplateWithTransaction
import org.totschnig.myexpenses.db2.loadActiveTagsForAccount
import org.totschnig.myexpenses.db2.loadAttachments
import org.totschnig.myexpenses.db2.loadPlan
import org.totschnig.myexpenses.db2.loadTemplate
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.requireParty
import org.totschnig.myexpenses.db2.requireRootParty
import org.totschnig.myexpenses.db2.requireBranchParty
import org.totschnig.myexpenses.db2.getRootPartyId
import org.totschnig.myexpenses.db2.loadTagsForTransaction
import org.totschnig.myexpenses.db2.savePrice
import org.totschnig.myexpenses.db2.updateNewPlanEnabled
import org.totschnig.myexpenses.db2.updatePlan
import org.totschnig.myexpenses.db2.updateTemplate
import org.totschnig.myexpenses.db2.updateTransaction
import org.totschnig.myexpenses.exception.UnknownPictureSaveException
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.domain.commitment.ContextChipData
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.model.sort.Sort
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.enumValueOrDefault
import org.totschnig.myexpenses.provider.CalendarProviderProxy
import org.totschnig.myexpenses.provider.DatabaseConstants.CATEGORY_ICON
import org.totschnig.myexpenses.provider.DatabaseConstants.CAT_AS_LABEL
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_DESCRIPTION
import org.totschnig.myexpenses.provider.KEY_IBAN
import org.totschnig.myexpenses.provider.KEY_ICON
import org.totschnig.myexpenses.provider.KEY_IS_PORTFOLIO
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_METHODID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PLANID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SEALED
import org.totschnig.myexpenses.provider.KEY_TITLE
import org.totschnig.myexpenses.provider.PORTFOLIO_NONE
import org.totschnig.myexpenses.provider.PlannerUtils
import org.totschnig.myexpenses.provider.ProviderUtils
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.ACCOUNTS_FULL_URI
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.QUERY_PARAMETER_ACCOUNT_TYPE_LIST
import org.totschnig.myexpenses.provider.fileName
import org.totschnig.myexpenses.provider.getLongIfExists
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringIfExists
import org.totschnig.myexpenses.provider.isDebugAsset
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.service.PlanExecutor
import org.totschnig.myexpenses.ui.DisplayParty
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.util.ICurrencyFormatter
import org.totschnig.myexpenses.util.ImageOptimizer
import org.totschnig.myexpenses.util.PictureDirHelper
import org.totschnig.myexpenses.util.ShortcutHelper
import org.totschnig.myexpenses.util.asExtension
import org.totschnig.myexpenses.util.io.FileCopyUtils
import org.totschnig.myexpenses.util.io.getFileExtension
import org.totschnig.myexpenses.util.io.getNameWithoutExtension
import org.totschnig.myexpenses.viewmodel.data.Account
import org.totschnig.myexpenses.viewmodel.data.InitialPlanData
import org.totschnig.myexpenses.viewmodel.data.PaymentMethod
import org.totschnig.myexpenses.viewmodel.data.PlanLoadedData
import org.totschnig.myexpenses.viewmodel.data.Tag
import org.totschnig.myexpenses.viewmodel.data.TemplateEditData
import org.totschnig.myexpenses.viewmodel.data.TransactionEditData
import org.totschnig.myexpenses.viewmodel.data.TransactionEditResult
import org.totschnig.myexpenses.viewmodel.data.TransferEditData
import org.totschnig.myexpenses.viewmodel.data.mapper.TransactionMapper
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import org.totschnig.myexpenses.viewmodel.data.Template as DataTemplate

class TransactionEditViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    TagHandlingViewModel(application, savedStateHandle) {

    @Inject
    lateinit var plannerUtils: PlannerUtils

    @Inject
    lateinit var exchangeRateHandler: ExchangeRateHandler

    @Inject
    lateinit var currencyFormatter: ICurrencyFormatter

    private var loadMethodJob: Job? = null

    private val _autoFillData: MutableStateFlow<AutoFillData?> = MutableStateFlow(null)
    val autoFillData: StateFlow<AutoFillData?> = _autoFillData

    private val _contextChip: MutableStateFlow<ContextChipData?> = MutableStateFlow(null)
    val contextChip: StateFlow<ContextChipData?> = _contextChip

    fun updateContextChip(payeeId: Long?, payeeName: String?, catId: Long?, amountPaise: Long) {
        viewModelScope.launch {
            _contextChip.value = repositoryCommitment.getContextChipInfo(payeeId, payeeName, catId, amountPaise)
        }
    }

    private val methods = MutableLiveData<List<PaymentMethod>>()
    fun getMethods(): LiveData<List<PaymentMethod>> = methods

    val accounts: Flow<List<Account>>
        get() = contentResolver.observeQuery(
            uri = ACCOUNTS_FULL_URI,
            selection = "$KEY_SEALED = 0 AND $KEY_IS_PORTFOLIO = $PORTFOLIO_NONE AND $KEY_PARENTID IS NULL"
        ).mapToList {
            Account.fromCursor(it, currencyContext)
        }

    val templates: Flow<List<DataTemplate>>
        get() = contentResolver.observeQuery(
            uri = TransactionProvider.TEMPLATES_URI.buildUpon()
                .build(), projection = arrayOf(KEY_ROWID, KEY_TITLE),
            selection = "$KEY_PLANID is null AND $KEY_PARENTID is null AND $KEY_SEALED = 0",
            sortOrder = Sort.preferredOrderByForTemplatesWithPlans(
                prefHandler,
                Sort.USAGES,
                collate
            )
        ).mapToList { DataTemplate.fromCursor(it) }

    @RequiresPermission(allOf = [Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR])
    fun plan(planId: Long): LiveData<PlanLoadedData?> = liveData(context = coroutineContext()) {
        emit(repository.loadPlan(planId)?.let { TransactionMapper.mapPlan(it) })
    }

    fun loadMethods(isIncome: Boolean, type: AccountType) {
        loadMethodJob?.cancel()
        loadMethodJob = viewModelScope.launch {
            contentResolver.observeQuery(
                TransactionProvider.METHODS_URI.buildUpon()
                    .appendPath(TransactionProvider.URI_SEGMENT_TYPE_FILTER)
                    .appendPath(if (isIncome) "1" else "-1")
                    .appendQueryParameter(QUERY_PARAMETER_ACCOUNT_TYPE_LIST, type.id.toString())
                    .build(), null, null, null, null, false
            )
                .mapToList { PaymentMethod.from(it) }
                .collect {
                    methods.postValue(it)
                }
        }
    }

    private fun maybeCreateInitialPlan(
        transaction: TransactionEditData,
        initialPlan: InitialPlanData,
    ): Pair<String, Plan?> {
        val title = initialPlan.title
            ?: transaction.party?.name
            ?: transaction.categoryPath?.takeIf { it.isNotEmpty() }
            ?: transaction.comment?.takeIf { it.isNotEmpty() }
            ?: localizedContext.getString(R.string.menu_create_template)
        //noinspection MissingPermission
        return title to if (initialPlan.recurrence != Recurrence.NONE) repository.createPlan(
            title,
            transaction.compileDescription(localizedContext, currencyFormatter, initialPlan.uuid),
            initialPlan.date,
            initialPlan.recurrence
        ) else null
    }

    @SuppressLint("MissingPermission")
    suspend fun save(
        transaction: TransactionEditData,
        userSetExchangeRate: BigDecimal?,
    ): Result<TransactionEditResult> = withContext(coroutineContext()) {
        runCatching {
            val resolvedParty = transaction.party?.let { party ->
                val rootId = party.id?.let { id ->
                    repository.getRootPartyId(id) ?: id
                } ?: repository.requireRootParty(party.name)

                if (rootId != null) {
                    val finalPartyId = if (!party.iban.isNullOrBlank()) {
                        repository.requireBranchParty(rootId, party.name, party.iban)
                    } else {
                        rootId
                    }
                    val finalName = getPayeeName(finalPartyId) ?: party.name
                    DisplayParty(finalPartyId, finalName, party.shortName, party.iban)
                } else {
                    party
                }
            }
            val transaction = transaction.copy(party = resolvedParty)
            val result = if (transaction.isTemplate) {
                val initialPlanId = transaction.initialPlan?.let {
                    maybeCreateInitialPlan(
                        transaction,
                        it
                    ).second?.id
                }
                val template = TransactionMapper.mapTemplate(transaction).let {
                    if (initialPlanId != null) it.copy(data = it.data.copy(planId = initialPlanId)) else it
                }
                val id = if (transaction.id == 0L) {
                    val id = repository.createTemplate(template).id
                    repository.updateNewPlanEnabled(licenceHandler)
                    if (initialPlanId != null) {
                        PlanExecutor.enqueueSelf(application, prefHandler, forceImmediate = true)
                    }
                    id
                } else {
                    repository.updateTemplate(template)
                    if (transaction.planId != null) {
                        repository.updatePlan(
                            transaction.planId,
                            template.title,
                            transaction.compileDescription(localizedContext, currencyFormatter)
                        )
                    }
                    transaction.id
                }
                TransactionEditResult(
                    id = id,
                    amount = template.data.amount,
                    initialPlanId = initialPlanId
                )
            } else {
                val repositoryTransaction = TransactionMapper.mapTransaction(transaction)
                val saved = if (transaction.id == 0L) {
                    repository.createTransaction(repositoryTransaction)
                } else {
                    repository.updateTransaction(repositoryTransaction)
                    repositoryTransaction
                }
                val initialPlanId = transaction.initialPlan
                    ?.let { maybeCreateInitialPlan(transaction, it) }
                    ?.also { (title, plan) ->
                        val template = repository.createTemplate(
                            RepositoryTemplate.fromTransaction(
                                repositoryTransaction,
                                title,
                                transaction.initialPlan.uuid
                            ).let {
                                if (plan?.id != null) it.copy(data = it.data.copy(planId = plan.id)) else it
                            }
                        )
                        if (plan != null) {
                            repository.linkTemplateWithTransaction(
                                template.id,
                                saved.id,
                                CalendarProviderProxy.calculateId(plan.dtStart)
                            )
                        }
                        repository.updateNewPlanEnabled(licenceHandler)
                    }?.second?.id
                if (transaction.planInstanceId != null && transaction.originTemplate != null) {
                    repository.linkTemplateWithTransaction(
                        transaction.originTemplate.templateId,
                        saved.id,
                        transaction.planInstanceId
                    )
                }
                TransactionEditResult(
                    id = saved.id,
                    amount = repositoryTransaction.data.amount,
                    transferPeer = saved.transferPeer?.id,
                    transferAmount = repositoryTransaction.transferPeer?.amount,
                    initialPlanId = initialPlanId
                )
            }
            //Attachments
            if (!transaction.isTemplate) {
                (originalUris - attachmentUris.value.toSet()).takeIf { it.isNotEmpty() }?.let {
                    repository.deleteAttachments(transaction.id, it)
                }
                val newAttachmentUris =
                    (attachmentUris.value - originalUris.toSet()).map(::prepareUriForSave)
                repository.addAttachments(result.id, newAttachmentUris)
                result.transferPeer?.let {
                    repository.addAttachments(it, newAttachmentUris)
                }
                try {
                    repositoryCommitment.resyncAllCommitments()
                } catch (e: Exception) {
                    Timber.e(e, "Error resyncing commitments after transaction save")
                }
            }

            if (transaction.templateEditData != null && transaction.id != 0L) {
                if (
                    ShortcutManagerCompat.getShortcuts(getApplication(), FLAG_MATCH_PINNED)
                        .any {
                            it.id == ShortcutHelper.idTemplate(transaction.id)
                        }
                ) {
                    ShortcutManagerCompat.updateShortcuts(
                        getApplication(),
                        listOf(
                            ShortcutHelper.buildTemplateShortcut(
                                getApplication(),
                                TemplateInfo.fromTemplate(
                                    transaction.id,
                                    transaction.templateEditData.title,
                                    transaction.templateEditData.defaultAction
                                )
                            )
                        )
                    )
                }
            }
            val date = transaction.date.toLocalDate()
            if (date <= LocalDate.now()) {
                userSetExchangeRate
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?.let {
                        repository.savePrice(
                            currencyContext.homeCurrencyUnit,
                            transaction.amount.currencyUnit,
                            date,
                            ExchangeRateSource.User,
                            it
                        )
                    }
            }
            result
        }
    }

    private val shouldCopyExternalUris
        get() = prefHandler.getBoolean(PrefKey.COPY_ATTACHMENT, true)

    private fun prepareUriForSave(uri: Uri): Uri {
        val pictureUriBase: String = PictureDirHelper.getPictureUriBase(false, getApplication())
        return if (uri.toString().startsWith(pictureUriBase) || uri.isDebugAsset) uri
        else {

            val pictureUriTemp = PictureDirHelper.getPictureUriBase(true, getApplication())
            val isInTempFolder = uri.toString().startsWith(pictureUriTemp)
            val isExternal = !isInTempFolder

            if (isExternal && !shouldCopyExternalUris) uri else {

                val result = if (contentResolver.getType(uri)?.startsWith("image") == true &&
                    prefHandler.getBoolean(PrefKey.OPTIMIZE_PICTURE, true)
                ) {
                    val format = prefHandler.enumValueOrDefault(
                        PrefKey.OPTIMIZE_PICTURE_FORMAT,
                        Bitmap.CompressFormat.WEBP_LOSSY
                    )

                    val homeUri = PictureDirHelper.getOutputMediaUri(
                        false,
                        getApplication(),
                        extension = format.asExtension
                    )
                    try {

                        val maxSize =
                            prefHandler.getInt(PrefKey.OPTIMIZE_PICTURE_MAX_SIZE, 1000)
                        val quality = prefHandler.getInt(PrefKey.OPTIMIZE_PICTURE_QUALITY, 80)
                            .coerceAtLeast(0).coerceAtMost(100)
                        ImageOptimizer.optimize(
                            contentResolver,
                            uri,
                            homeUri,
                            format,
                            maxSize,
                            maxSize,
                            quality
                        )
                    } catch (e: IOException) {
                        throw UnknownPictureSaveException(uri, homeUri, e)
                    }
                    homeUri
                } else {
                    val fileName = uri.fileName(getApplication())
                    val homeUri = PictureDirHelper.getOutputMediaUri(
                        false,
                        getApplication(),
                        fileName = getNameWithoutExtension(fileName),
                        extension = getFileExtension(fileName)
                    )
                    FileCopyUtils.copy(contentResolver, uri, homeUri)
                    homeUri
                }
                if (isInTempFolder) {
                    contentResolver.delete(uri, null, null)
                }
                result
            }
        }
    }

    fun loadActiveTags(id: Long) = viewModelScope.launch(coroutineContext()) {
        if (!userHasUpdatedTags) {
            updateTags(repository.loadActiveTagsForAccount(id), false)
        }
    }

    suspend fun newTemplate(
        operationType: Int,
        parentId: Long?,
        defaultAction: Template.Action,
    ): TransactionEditData? = withContext(coroutineContext()) {
        repository.getLastUsedOpenAccount()?.let {
            TransactionEditData(
                amount = Money(it.second, 0L),
                accountId = it.first,
                transferEditData = if (operationType == TYPE_TRANSFER) TransferEditData() else null,
                categoryId = if (operationType == TYPE_SPLIT) SPLIT_CATID else null,
                templateEditData = TemplateEditData(
                    defaultAction = defaultAction
                ),
                parentId = parentId,
                uuid = generateUuid()
            ).applyDefaultTransferCategory()
        }
    }

    private suspend fun ensureLoadData(
        accountId: Long,
        currencyUnit: CurrencyUnit?,
    ): Pair<Long, CurrencyUnit>? {
        return if (accountId > 0 && currencyUnit != null)
            accountId to currencyUnit
        else withContext(coroutineContext()) {
            (if (accountId > 0) {
                repository.getCurrencyUnitForAccount(accountId)?.let {
                    accountId to it
                }
            } else null) ?: repository.getLastUsedOpenAccount()
        }
    }

    suspend fun newTransaction(
        accountId: Long,
        currencyUnit: CurrencyUnit?,
        parentId: Long?,
    ): TransactionEditData? =
        ensureLoadData(accountId, currencyUnit)?.let { (accountId, currencyUnit) ->
            TransactionEditData(
                amount = Money(currencyUnit, 0L),
                accountId = accountId,
                parentId = parentId,
                uuid = generateUuid(),
            )
        }

    suspend fun newTransfer(
        accountId: Long,
        currencyUnit: CurrencyUnit?,
        transferAccountId: Long,
        parentId: Long?,
    ): TransactionEditData? =
        ensureLoadData(accountId, currencyUnit)?.let { (accountId, currencyUnit) ->
            TransactionEditData(
                amount = Money(currencyUnit, 0L),
                accountId = accountId,
                parentId = parentId,
                uuid = generateUuid(),
                transferEditData = TransferEditData(transferAccountId = transferAccountId)
            ).applyDefaultTransferCategory()
        }

    suspend fun newSplit(accountId: Long, currencyUnit: CurrencyUnit?): TransactionEditData? =
        ensureLoadData(accountId, currencyUnit)?.let { (accountId, currencyUnit) ->
            TransactionEditData(
                amount = Money(currencyUnit, 0L),
                accountId = accountId,
                categoryId = SPLIT_CATID,
                uuid = generateUuid(),
            )
        }

    /**
     * as a side effect sets attachments
     */
    suspend fun read(
        rowId: Long,
        task: InstantiationTask,
        clone: Boolean,
        extras: Bundle?,
    ): TransactionEditData? = withContext(context = coroutineContext()) {

        if (task == InstantiationTask.TRANSACTION) {
            val uriList = repository.loadAttachments(rowId)
            //If we clone a transaction the attachments need to be considered new for the clone in order to get saved
            if (clone) {
                addAttachmentUris(*uriList.toTypedArray())
            } else {
                originalUris = ArrayList(uriList)
            }
        }

        when (task) {
            InstantiationTask.TEMPLATE -> repository.loadTemplate(rowId, withTags = true)?.let {
                TransactionMapper.map(it, currencyContext)
            }

            InstantiationTask.TRANSACTION_FROM_TEMPLATE -> {
                val template = repository.loadTemplate(rowId, withTags = true, require = false)
                template?.instantiate(currencyContext, exchangeRateHandler)?.let {
                    TransactionMapper.map(it, currencyContext).copy(
                        originTemplate = TransactionMapper.mapTemplateEditData(template)
                    )
                }
            }

            InstantiationTask.TRANSACTION -> repository.loadTransaction(rowId, withTags = true)
                .let {
                    val withCurrentDate =
                        if (clone && prefHandler.getBoolean(PrefKey.CLONE_WITH_CURRENT_DATE, true))
                            ZonedDateTime.now().toEpochSecond() else null
                    TransactionMapper.map(
                        if (clone) it.copy(
                            data = it.data.copy(
                                id = 0L,
                                uuid = generateUuid(),
                                date = withCurrentDate ?: it.data.date,
                                valueDate = withCurrentDate ?: it.data.valueDate
                            )
                        ) else it, currencyContext
                    )
                }

            InstantiationTask.FROM_INTENT_EXTRAS ->
                ProviderUtils.buildFromExtras(repository, extras!!)


            InstantiationTask.TEMPLATE_FROM_TRANSACTION -> RepositoryTemplate.fromTransaction(
                repository.loadTransaction(rowId, withTransfer = true, withTags = true)
            ).let {
                TransactionMapper.map(it, currencyContext)
            }
        }
    }

    companion object {
        private const val KEY_ATTACHMENT_URIS = "attachmentUris"
        private const val KEY_ORIGINAL_URIS = "originalUris"
    }

    fun startAutoFill(id: Long, overridePreferences: Boolean, autoFillAccountFromExtra: Boolean) {
        val dataToLoad = buildList {
            add(KEY_ROWID)
            val autoFillAccountFromPreference =
                prefHandler.getString(PrefKey.AUTO_FILL_ACCOUNT, "aggregate")
            val mayLoadAccount =
                overridePreferences && autoFillAccountFromExtra || autoFillAccountFromPreference == "always" ||
                        autoFillAccountFromPreference == "aggregate" && autoFillAccountFromExtra
            if (overridePreferences || prefHandler.getBoolean(PrefKey.AUTO_FILL_AMOUNT, true)) {
                add(KEY_CURRENCY)
                add(KEY_AMOUNT)
            }
            if (overridePreferences || prefHandler.getBoolean(
                    PrefKey.AUTO_FILL_CATEGORY,
                    true
                )
            ) {
                add(KEY_CATID)
                add(CAT_AS_LABEL)
                add(CATEGORY_ICON)
            }
            if (overridePreferences || prefHandler.getBoolean(
                    PrefKey.AUTO_FILL_COMMENT,
                    true
                )
            ) {
                add(KEY_COMMENT)
            }
            if (overridePreferences || prefHandler.getBoolean(PrefKey.AUTO_FILL_METHOD, true)) {
                add(KEY_METHODID)
            }
            if (overridePreferences || prefHandler.getBoolean(PrefKey.AUTO_FILL_DEBT, true)) {
                add(KEY_DEBT_ID)
            }
            if (mayLoadAccount) {
                add(KEY_ACCOUNTID)
            }
        }
        viewModelScope.launch(coroutineContext()) {
            contentResolver.query(
                ContentUris.withAppendedId(TransactionProvider.AUTOFILL_URI, id),
                dataToLoad.toTypedArray(), null, null, null
            )?.use {
                if (it.moveToFirst()) {
                    val txnId = it.getLongIfExists(KEY_ROWID)
                    val tags = txnId?.let { tid -> repository.loadTagsForTransaction(tid) }
                    _autoFillData.tryEmit(AutoFillData.fromCursor(it, currencyContext, tags))
                }
            }
        }
    }

    fun autoFillDone() {
        _autoFillData.tryEmit(null)
    }

    private var originalUris: ArrayList<Uri>
        get() = savedStateHandle[KEY_ORIGINAL_URIS] ?: ArrayList()
        set(value) {
            savedStateHandle[KEY_ORIGINAL_URIS] = value
            addAttachmentUris(*value.toTypedArray())
        }

    val attachmentUris: StateFlow<ArrayList<Uri>> =
        savedStateHandle.getStateFlow(KEY_ATTACHMENT_URIS, ArrayList())

    private val _upiReceiptEvent = MutableSharedFlow<Pair<Uri, org.totschnig.myexpenses.util.upi.UpiReceiptData>>(replay = 1, extraBufferCapacity = 1)
    val upiReceiptEvent: SharedFlow<Pair<Uri, org.totschnig.myexpenses.util.upi.UpiReceiptData>> = _upiReceiptEvent

    fun checkAndScanUpiReceipt(uri: Uri) {
        viewModelScope.launch {
            val result = org.totschnig.myexpenses.util.upi.UpiOcrScanner.scanReceipt(getApplication(), uri)
            result.onSuccess { data ->
                if (data.isValid) {
                    _upiReceiptEvent.emit(Pair(uri, data))
                }
            }
        }
    }

    suspend fun resolvePayeeId(name: String, upiId: String? = null): Long? = withContext(Dispatchers.IO) {
        val cleanName = org.totschnig.myexpenses.util.upi.UpiReceiptParser.cleanPayee(name)
        if (cleanName.isBlank() && upiId.isNullOrBlank()) return@withContext null

        try {
            // 1. Direct case-insensitive match on cleanName
            if (cleanName.isNotBlank()) {
                contentResolver.query(
                    TransactionProvider.PAYEES_URI,
                    arrayOf(KEY_ROWID),
                    "$KEY_PAYEE_NAME = ? COLLATE NOCASE",
                    arrayOf(cleanName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) return@withContext cursor.getLong(0)
                }

                // 2. Space-normalized match (e.g. "Hunger Box" vs "Hungerbox")
                val noSpaces = cleanName.replace(" ", "")
                contentResolver.query(
                    TransactionProvider.PAYEES_URI,
                    arrayOf(KEY_ROWID),
                    "REPLACE($KEY_PAYEE_NAME, ' ', '') = ? COLLATE NOCASE",
                    arrayOf(noSpaces),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) return@withContext cursor.getLong(0)
                }
            }

            // 3. Match on UPI ID / IBAN if provided
            val cleanUpi = upiId?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanUpi != null) {
                contentResolver.query(
                    TransactionProvider.PAYEES_URI,
                    arrayOf(KEY_ROWID),
                    "$KEY_IBAN = ? COLLATE NOCASE",
                    arrayOf(cleanUpi),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) return@withContext cursor.getLong(0)
                }
            }

            // 4. Fallback: find or create root party with cleanName if available
            if (cleanName.isNotBlank()) {
                repository.requireRootParty(cleanName)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error resolving payee id for %s", name)
            if (cleanName.isNotBlank()) repository.requireRootParty(cleanName) else null
        }
    }

    suspend fun getPayeeName(payeeId: Long): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.query(
                ContentUris.withAppendedId(TransactionProvider.PAYEES_URI, payeeId),
                arrayOf(KEY_PAYEE_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(KEY_PAYEE_NAME)
                    if (idx != -1 && !cursor.isNull(idx)) cursor.getString(idx) else cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findMethodIdForLabel(label: String): Long? = withContext(Dispatchers.IO) {
        try {
            contentResolver.query(
                TransactionProvider.METHODS_URI,
                arrayOf(KEY_ROWID),
                "$KEY_LABEL = ? COLLATE NOCASE",
                arrayOf(label),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findAccountIdForHint(hint: String): Long? = withContext(Dispatchers.IO) {
        try {
            // 1. Direct match on label or description
            contentResolver.query(
                TransactionProvider.ACCOUNTS_URI,
                arrayOf(KEY_ROWID),
                "$KEY_LABEL LIKE ? OR $KEY_DESCRIPTION LIKE ?",
                arrayOf("%$hint%", "%$hint%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return@withContext cursor.getLong(0)
            }

            // 2. Routing sponsors & Card aliases in India
            val normalizedHint = when {
                hint.contains("Samsung", ignoreCase = true) || hint.contains("Axis", ignoreCase = true) -> "ICICI"
                hint.contains("Coral", ignoreCase = true) || hint.contains("Visa", ignoreCase = true) -> "Coral"
                hint.contains("Amazon", ignoreCase = true) -> "Amazon"
                else -> hint
            }
            contentResolver.query(
                TransactionProvider.ACCOUNTS_URI,
                arrayOf(KEY_ROWID),
                "$KEY_LABEL LIKE ? OR $KEY_DESCRIPTION LIKE ?",
                arrayOf("%$normalizedHint%", "%$normalizedHint%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return@withContext cursor.getLong(0)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findDefaultBaseAccountId(): Long? = withContext(Dispatchers.IO) {
        try {
            contentResolver.query(
                TransactionProvider.ACCOUNTS_URI,
                arrayOf(KEY_ROWID),
                "$KEY_LABEL LIKE ? OR $KEY_DESCRIPTION LIKE ?",
                arrayOf("%ICICI%", "%Salary%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return@withContext cursor.getLong(0)
            }
            contentResolver.query(
                TransactionProvider.ACCOUNTS_URI,
                arrayOf(KEY_ROWID),
                null,
                null,
                "$KEY_ROWID ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun addAttachmentUris(vararg uris: Uri) {
        savedStateHandle[KEY_ATTACHMENT_URIS] = ArrayList(mutableSetOf<Uri>().apply {
            addAll(attachmentUris.value)
            addAll(uris)
        })
    }

    fun removeAttachmentUri(uri: Uri) {
        savedStateHandle[KEY_ATTACHMENT_URIS] = ArrayList<Uri>().apply {
            addAll(attachmentUris.value.filterNot { it == uri })
        }
    }

    data class AutoFillData(
        val catId: Long?,
        val label: String?,
        val icon: String?,
        val comment: String?,
        val amount: Money?,
        val methodId: Long?,
        val accountId: Long?,
        val debtId: Long?,
        val tags: List<Tag>? = null,
    ) {
        companion object {
            fun fromCursor(cursor: Cursor, currencyContext: CurrencyContext, tags: List<Tag>? = null) = AutoFillData(
                catId = cursor.getLongIfExists(KEY_CATID),
                label = cursor.getStringIfExists(KEY_LABEL),
                icon = cursor.getStringIfExists(KEY_ICON),
                comment = cursor.getStringIfExists(KEY_COMMENT)?.let {
                    org.totschnig.myexpenses.util.upi.UpiReceiptParser.cleanComment(it).takeIf { c -> c.isNotEmpty() }
                },
                amount = cursor.getLongIfExists(KEY_AMOUNT)?.let {
                    Money(currencyContext[cursor.getString(KEY_CURRENCY)], it)
                },
                methodId = cursor.getLongIfExists(KEY_METHODID),
                accountId = cursor.getLongIfExists(KEY_ACCOUNTID),
                debtId = cursor.getLongIfExists(KEY_DEBT_ID),
                tags = tags
            )
        }
    }

    enum class InstantiationTask { TRANSACTION, TEMPLATE, TRANSACTION_FROM_TEMPLATE, FROM_INTENT_EXTRAS, TEMPLATE_FROM_TRANSACTION }
}


