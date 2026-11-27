---
name: compose-architecture-and-audit
description: Audit, optimize, and refactor Android Jetpack Compose codebases. Enforces state survival across configuration changes with custom Savers, recomposition stability, IME keyboard insets, dirty-state discard safeguards, and cloud-first CI/CD deployment.
metadata:
  version: "1.0"
---

# Jetpack Compose Architecture & Audit Skill

This skill provides an authoritative operational runbook and technical standard for auditing, refactoring, and implementing production-grade Jetpack Compose interfaces in Android applications, with a specialized focus on transactional, financial, and form-heavy interactions.

---

## 1. Core Principles

1. **Zero Data Loss on Configuration Change**: Any user-entered draft (amounts, dates, text, tags, attachments, selection states) must survive screen rotation, window resizing, theme changes, and OS process recreation.
2. **Predictive & Guarded Navigation**: Users must never lose uncommitted work via accidental taps outside a dialog or system back gestures.
3. **Double-Entry Financial Integrity**: Input validations must proactively block nonsensical states (e.g. transfers where source == destination, zero amounts, or mismatched category types).
4. **Cloud-First Verification Pipeline**: Respect host machine resource limits; never run heavy builds locally when cloud CI/CD is available.

---

## 2. State Survival & Custom Savers

### The Golden Rule
> **NEVER use plain `remember { mutableStateOf(...) }` for user-entered input.**
> Use `rememberSaveable` with a custom `Saver` whenever the data type is not natively supported by Android's `Bundle`.

### Custom Saver Reference Implementations

#### A. `BigDecimal` Saver (Currency & Amounts)
```kotlin
val BigDecimalSaver = run {
    val key = "big_decimal_str"
    Saver<BigDecimal, String>(
        save = { it.toPlainString() },
        restore = { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }
    )
}

// Usage in Composable:
var amount by rememberSaveable(stateSaver = BigDecimalSaver) {
    mutableStateOf(initialAmount)
}
```

#### B. `LocalDate` & `LocalTime` Savers (Date & Time Pickers)
```kotlin
val LocalDateSaver = Saver<LocalDate, String>(
    save = { it.toString() }, // ISO-8601 YYYY-MM-DD
    restore = { try { LocalDate.parse(it) } catch (_: Exception) { LocalDate.now() } }
)

val LocalTimeSaver = Saver<LocalTime, String>(
    save = { it.toString() }, // ISO-8601 HH:mm:ss
    restore = { try { LocalTime.parse(it) } catch (_: Exception) { LocalTime.now() } }
)

// Usage:
var date by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(initialDate) }
var time by rememberSaveable(stateSaver = LocalTimeSaver) { mutableStateOf(initialTime) }
```

#### C. Parcelable Collections & Domain Models
```kotlin
// For List<Tag> or List<Uri> (Uri is Parcelable):
var selectedTags by rememberSaveable(
    saver = listSaver(
        save = { it.map { tag -> tag.id } },
        restore = { ids -> allAvailableTags.filter { it.id in ids } }
    )
) {
    mutableStateOf(initialTags)
}

var attachments by rememberSaveable(
    saver = listSaver(
        save = { it.map { uri -> uri.toString() } },
        restore = { list -> list.map { Uri.parse(it) } }
    )
) {
    mutableStateOf(initialAttachments)
}
```

---

## 3. Dirty State Tracking & Unsaved Changes Guard

Form editors and transaction sheets must intercept dismiss requests when edits have been made.

```kotlin
// 1. Compute isDirty dynamically
val isDirty = remember(
    amountText, payeeText, comment, referenceNumber,
    selectedCategoryId, selectedTags, attachments
) {
    amountText.isNotBlank() ||
    payeeText.isNotBlank() ||
    comment.isNotBlank() ||
    referenceNumber.isNotBlank() ||
    attachments.isNotEmpty() ||
    selectedCategoryId != initialData?.categoryId
}

var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

// 2. Safe Close Action
val attemptClose = {
    if (isDirty) {
        showDiscardDialog = true
    } else {
        onClose()
    }
}

// 3. Intercept Android Back Gesture
BackHandler(enabled = isDirty) {
    showDiscardDialog = true
}

// 4. Discard Confirmation Dialog
if (showDiscardDialog) {
    AlertDialog(
        onDismissRequest = { showDiscardDialog = false },
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    showDiscardDialog = false
                    onClose()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.menu_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { showDiscardDialog = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
```

---

## 4. Financial & Form UX Guardrails

### A. Transfer Account Validation (No Self-Transfers)
```kotlin
val isTransfer = selectedType == TransactionTypeOption.TRANSFER
val isSelfTransfer = isTransfer && (selectedAccountId == transferAccountId)

// In Save button:
val canSave = amount > BigDecimal.ZERO && 
              selectedAccount != null && 
              (!isTransfer || !isSelfTransfer)

Button(
    onClick = ::handleSave,
    enabled = canSave
) { ... }

// Show clear warning if source and target match
if (isSelfTransfer) {
    Text(
        text = "Source and destination accounts must be different",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp)
    )
}
```

### B. Category Consistency Across Type Toggles
When toggling between Expense and Income:
```kotlin
fun updateTransactionType(newType: TransactionTypeOption) {
    if (selectedType == newType) return
    selectedType = newType
    
    // Validate if current category matches the new type
    if (selectedCategoryId != null) {
        val currentCat = allCategories.flatMap { it.flatten() }.find { it.id == selectedCategoryId }
        val targetFlag = if (newType == TransactionTypeOption.INCOME) FLAG_INCOME else FLAG_EXPENSE
        if (currentCat != null && currentCat.typeFlags != targetFlag && currentCat.typeFlags != FLAG_NEUTRAL) {
            // Mismatch: clear category to prevent corrupted analytics
            selectedCategoryId = null
            selectedCategoryPath = null
            selectedCategoryIcon = null
        }
    }
}
```

---

## 5. Soft Keyboard & IME Inset Handling

To prevent the on-screen keyboard from covering inputs or causing flickering dialog resizes:
1. Wrap root column content in `Modifier.imePadding()` or provide `WindowInsets.ime.asPaddingValues()`.
2. Do not combine nested scrollable containers with unconstrained dialog bounds.
3. Avoid programmatic `requestFocus()` during window attach (`LaunchedEffect(Unit)`) as it disrupts IME composition buffers.

---

## 6. Verification & Cloud Deployment Runbook

1. **Code Modification**:
   - Apply edits cleanly without leaving orphaned imports or unused variables.
2. **Git Commit & Remote Push**:
   - Commit changes: `git commit -m "fix/feat: ..."`
   - Push to GitHub remote: `git push origin <branch>`
3. **Cloud Build Execution**:
   - Watch cloud GitHub Actions workflow:
     ```bash
     gh run watch <run-id> --exit-status
     ```
4. **Artifact Download & Streamed Install**:
   - Download debug artifact:
     ```bash
     gh run download <run-id> -n MyExpenses-debug -D /tmp/debug-apk
     ```
   - Stream install to connected device:
     ```bash
     adb -s <device-id> install -r /tmp/debug-apk/*.apk
     ```
   - Verify package status non-intrusively via `dumpsys` / `pm list packages`.
