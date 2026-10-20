package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.io.FileUtils

class ImportSourceViewModel(application: Application) : BaseViewModel(application) {
    private val _appData = MutableStateFlow<List<DocumentFile>>(emptyList())
    val appData: StateFlow<List<DocumentFile>> = _appData.asStateFlow()

    fun loadAppData(typeChecker: (String, String) -> Boolean) {
        viewModelScope.launch(coroutineContext()) {
            AppDirHelper.getAppDir(getApplication()).onSuccess { dir ->
                _appData.value =
                    dir.listFiles()
                        .filter { it.length() > 0 && !it.isDirectory }
                        .sortedByDescending { it.lastModified() }
                        .filter {
                            val name = it.name
                            val type = it.type
                            name != null && type != null && typeChecker(type, FileUtils.getExtension(name))
                        }
            }
        }
    }
}