package org.totschnig.myexpenses.db2

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import androidx.annotation.VisibleForTesting
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import org.totschnig.myexpenses.model2.Party
import org.totschnig.myexpenses.provider.KEY_IBAN
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.METHOD_CLEANUP_UNUSED_PAYEES
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.PAYEES_URI
import org.totschnig.myexpenses.provider.mapToMap
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

fun Repository.createParty(party: Party) = contentResolver.createParty(party)
fun Repository.createParty(party: String) = Party.create(name = party)
    ?.let { contentResolver.createParty(it) }

fun Repository.saveParty(party: Party) {
    contentResolver.update(
        ContentUris.withAppendedId(PAYEES_URI, party.id),
        party.asContentValues,
        null,
        null
    )
}

/**
 * Looks for a party by name and iban
 * @return id or null if not found
 */
fun Repository.findParty(party: Party) = contentResolver.findParty(party.name, party.iban)

fun Repository.findParty(name: String) = contentResolver.findParty(name)
fun Repository.requireParty(name: String) = contentResolver.requireParty(name)

@VisibleForTesting
fun Repository.deleteParty(id: Long) {
    contentResolver.delete(ContentUris.withAppendedId(PAYEES_URI, id), null, null)
}

// legacy methods with ContentResolver receiver
fun ContentResolver.requireParty(name: String): Long? {
    return findParty(name) ?: Party.create(name = name)
        ?.let { createParty(it) }?.id
}

fun ContentResolver.createParty(party: Party) =
    insert(
        PAYEES_URI,
        party.asContentValues
    )?.let {
        party.copy(id = ContentUris.parseId(it))
    }.also {
        if (it == null) {
            CrashHandler.report(
                Exception("failed to create party")
            )
        }
    }

fun ContentResolver.findParty(party: String, iban: String? = null) = query(
    PAYEES_URI,
    arrayOf(KEY_ROWID),
    KEY_PAYEE_NAME + " = ? AND " + KEY_IBAN + if (iban == null) " IS NULL" else " = ?",
    if (iban == null) arrayOf(party.trim()) else arrayOf(party, iban),
    null
)?.use { if (it.moveToFirst()) it.getLong(0) else null }

fun ContentResolver.requireRootParty(name: String): Long? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null

    // 1. Look for root party (parent_id IS NULL) with this name
    val rootId = query(
        PAYEES_URI,
        arrayOf(KEY_ROWID),
        "$KEY_PAYEE_NAME = ? AND $KEY_PARENTID IS NULL",
        arrayOf(trimmed),
        null
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    if (rootId != null) return rootId

    // 2. If no root party, check if any party exists with this name (legacy)
    val anyId = findParty(trimmed)
    if (anyId != null) return anyId

    // 3. Create a new root party
    return Party.create(name = trimmed)?.let { createParty(it) }?.id
}

fun ContentResolver.requireBranchParty(rootId: Long, name: String, iban: String?): Long {
    val cleanIban = iban?.trim()?.takeIf { it.isNotEmpty() } ?: return rootId
    val trimmedName = name.trim()

    // 1. Check if a branch under rootId already exists with this IBAN/UPI ID
    val branchId = query(
        PAYEES_URI,
        arrayOf(KEY_ROWID),
        "$KEY_PARENTID = ? AND $KEY_IBAN = ?",
        arrayOf(rootId.toString(), cleanIban),
        null
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    if (branchId != null) return branchId

    // 2. Check if the root party itself already has this exact IBAN
    val rootHasIban = query(
        PAYEES_URI,
        arrayOf(KEY_ROWID, KEY_IBAN),
        "$KEY_ROWID = ?",
        arrayOf(rootId.toString()),
        null
    )?.use { if (it.moveToFirst()) it.getStringOrNull(1) == cleanIban else false }

    if (rootHasIban == true) return rootId

    // 3. Create a new child branch payee linked to rootId
    val branchParty = Party.create(
        name = trimmedName,
        iban = cleanIban,
        parentId = rootId
    )
    return branchParty?.let { createParty(it) }?.id ?: rootId
}

fun ContentResolver.getRootPartyId(partyId: Long): Long? = query(
    ContentUris.withAppendedId(PAYEES_URI, partyId),
    arrayOf(KEY_PARENTID),
    null, null, null
)?.use {
    if (it.moveToFirst()) {
        it.getLongOrNull(0) ?: partyId
    } else partyId
}

fun Repository.requireRootParty(name: String): Long? =
    contentResolver.requireRootParty(name)

fun Repository.requireBranchParty(rootId: Long, name: String, iban: String?): Long =
    contentResolver.requireBranchParty(rootId, name, iban)

fun Repository.getRootPartyId(partyId: Long): Long? =
    contentResolver.getRootPartyId(partyId)

fun Repository.unsetParentId(partyId: Long) {
    setParentId(partyId, null)
}

fun Repository.getPartyName(partyId: Long) = contentResolver.query(
    ContentUris.withAppendedId(PAYEES_URI, partyId),
    arrayOf(KEY_PAYEE_NAME), null, null, null
)?.use {
    it.moveToFirst()
    it.getString(0)
}

@VisibleForTesting
fun Repository.getParty(partyId: Long) = contentResolver.query(
    ContentUris.withAppendedId(PAYEES_URI, partyId),
    arrayOf(KEY_PAYEE_NAME, KEY_PARENTID), null, null, null
)?.use {
    if (it.moveToFirst()) {
        Party(partyId, it.getString(0), parentId = it.getLongOrNull(1))
    } else
        null
}

@VisibleForTesting
fun Repository.setParentId(partyId: Long, parentId: Long?) {
    contentResolver.update(
        ContentUris.withAppendedId(PAYEES_URI, partyId),
        ContentValues(1).apply { put(KEY_PARENTID, parentId) },
        null, null
    )
}

fun Repository.cleanupUnusedParties() {
    contentResolver.call(
        TransactionProvider.DUAL_URI,
        METHOD_CLEANUP_UNUSED_PAYEES,
        null,
        null
    )
}


fun Repository.observePayeeMap(): Flow<Map<Long, String>> {
    return contentResolver.observeQuery(
        PAYEES_URI,
        arrayOf(KEY_ROWID, KEY_PAYEE_NAME)
    ).mapToMap { cursor ->
        cursor.getLong(0) to cursor.getString(1)
    }
}
