package com.nutriscan.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.remote.models.User
import com.nutriscan.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchUsersViewModel @Inject constructor(
    private val socialRepository: SocialRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadDefaultUsers()
    }

    private fun loadDefaultUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = socialRepository.getRandomUsers(limit = 20)
            result.onSuccess { users ->
                _searchResults.value = users
                _error.value = null
            }.onFailure { e ->
                _error.value = "Failed to load users: ${e.message}"
                // Keep existing results if any, or clear depending on preference
                if (_searchResults.value.isEmpty()) {
                    _searchResults.value = emptyList()
                }
            }
            _isLoading.value = false
        }
    }

    fun searchUsers(query: String) {
        // minimum 2 characters to search, otherwise show defaults
        if (query.length < 2) {
            loadDefaultUsers()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = socialRepository.searchUsers(query)
            result.onSuccess { users ->
                _searchResults.value = users
                _error.value = null
            }.onFailure { e ->
                _error.value = "Search failed: ${e.message}"
                _searchResults.value = emptyList()
            }
            _isLoading.value = false
        }
    }
}