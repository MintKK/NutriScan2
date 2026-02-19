package com.nutriscan.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.nutriscan.data.remote.models.Comment
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import com.nutriscan.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _feedPosts = MutableStateFlow<List<Post>>(emptyList())
    val feedPosts: StateFlow<List<Post>> = _feedPosts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val currentUser: StateFlow<User?> = socialRepository.getCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        //loadFeed() dont load immediately anymore since adb cannot test
    }

    fun loadFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch
                socialRepository.getFeedPosts(currentUserId)
                    .catch {
                        e ->
                        _error.value = e.message
                    }
                    .collect {
                        posts ->
                        _feedPosts.value = posts
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun likePost(postID: String) {
        viewModelScope.launch {
            socialRepository.likePost(postID)
                .onFailure {
                    e ->
                    _error.value = e.message
                }
        }
    }

    fun unlikePost(postID: String) {
        viewModelScope.launch {
            socialRepository.unlikePost(postID)
                .onFailure {
                    e ->
                    _error.value = e.message
                }
        }
    }

    fun isPostLikedByUser(postID: String): Flow<Boolean> {
        return socialRepository.isPostLikedByUser(postID);
    }

    fun addComment(postID: String, content: String) {
        viewModelScope.launch {
            socialRepository.addComment(postID, content)
                .onFailure {
                    e ->
                    _error.value = e.message
                }
        }
    }

    fun followUser(userID: String) {
        viewModelScope.launch {
            socialRepository.followUser(userID)
                .onFailure {
                    e ->
                    _error.value = e.message
                }
        }
    }

    fun unfollowUser(userID: String) {
        viewModelScope.launch {
            socialRepository.unfollowUser(userID)
                .onFailure {
                    e ->
                    _error.value = e.message
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun checkFirebaseAvailability() {
        viewModelScope.launch {
            try {
                // Try to access Firebase Auth as a test
                val currentUser = auth.currentUser
                _error.value = null

                try {
                    // Test Firestore with a simple operation
                    socialRepository.testFirestoreConnection()

                    loadFeed()
                } catch (e: Exception) {
                    _error.value = "Firestore unavailable: ${e.message}"
                }
            } catch (e: Exception) {
                _error.value =
                    when {
                        e.message?.contains("Google Play Services", ignoreCase = true) == true -> {
                            "Google Play Services is required. Please run on a device with Google Play."
                        }

                        else -> {
                            "Firebase error: ${e.message}"
                        }
                    }
            }
        }
    }
}