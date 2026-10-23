package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.viewmodel.data.Tag

open class TagHandlingViewModel(application: Application, savedStateHandle: SavedStateHandle)
    : TagBaseViewModel(application, savedStateHandle) {

    val tagsLiveData : MutableLiveData<List<Tag>> = savedStateHandle.getLiveData(KEY_MAPPED_TAGS)

    var userHasUpdatedTags: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_USER_HAS_UPDATED_TAG) == true
        set(value) {
            savedStateHandle[KEY_USER_HAS_UPDATED_TAG] = value
        }

    fun updateTags(tagList: List<Tag>, fromUser: Boolean) {
        if (fromUser) {
            userHasUpdatedTags = true
        }
        tagsLiveData.postValue(tagList)
        refreshSuggestedTags(tagList)
    }

    fun removeTag(tag: Tag) {
        userHasUpdatedTags = true
        removeTags(tag.id)
    }

    fun removeTags(vararg tagIds: Long) {
        val updated = tagsLiveData.value?.filter { !tagIds.contains(it.id) }.orEmpty()
        savedStateHandle[KEY_MAPPED_TAGS] = updated
        refreshSuggestedTags(updated)
    }

    val suggestedTagsLiveData: MutableLiveData<List<Tag>> = MutableLiveData(emptyList())
    private var cachedSuggestions: List<Tag> = emptyList()

    fun fetchSuggestedTags(payeeId: Long?, catId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cachedSuggestions = repositoryCommitment.getSuggestedTagsForContext(payeeId, catId)
                refreshSuggestedTags(tagsLiveData.value)
            } catch (e: Exception) {
                cachedSuggestions = emptyList()
                suggestedTagsLiveData.postValue(emptyList())
            }
        }
    }

    private fun refreshSuggestedTags(currentTags: List<Tag>?) {
        val currentSelectedIds = currentTags?.map { it.id }?.toSet() ?: emptySet()
        val filtered = cachedSuggestions.filter { it.id !in currentSelectedIds }
        suggestedTagsLiveData.postValue(filtered)
    }

    fun addTag(tag: Tag) {
        userHasUpdatedTags = true
        val current = tagsLiveData.value.orEmpty()
        if (current.none { it.id == tag.id }) {
            val updated = current + tag
            tagsLiveData.postValue(updated)
            refreshSuggestedTags(updated)
        }
    }

    companion object {
        private const val KEY_MAPPED_TAGS = "mappedTags"
        private const val KEY_USER_HAS_UPDATED_TAG = "userHasUpdatedTags"
    }
}