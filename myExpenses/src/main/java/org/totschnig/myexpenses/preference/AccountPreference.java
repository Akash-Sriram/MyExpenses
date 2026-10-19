package org.totschnig.myexpenses.preference;

import android.content.Context;
import android.util.AttributeSet;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.sync.GenericAccountService;

import androidx.preference.ListPreference;

public class AccountPreference extends ListPreference {

  public static final String SYNCHRONIZATION_NONE = "_SYNCHRONIZATION_NONE_";

  public AccountPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AccountPreference(Context context) {
    this(context, null);
  }

  @Override
  public int findIndexOfValue(String value) {
    int indexOfValue = super.findIndexOfValue(value);
    return Math.max(indexOfValue, 0);
  }

  public void setData(Context context) {
    String[] accounts = GenericAccountService.getAccountNames(context);
    String[] entries = new String[accounts.length + 1];
    entries[0] = context.getString(androidx.preference.R.string.not_set);
    System.arraycopy(accounts, 0, entries, 1, accounts.length);
    setEntries(entries);

    String[] entryValues = new String[accounts.length + 1];
    entryValues[0] = SYNCHRONIZATION_NONE;
    System.arraycopy(accounts, 0, entryValues, 1, accounts.length);
    setEntryValues(entryValues);
    notifyChanged();
  }
}
