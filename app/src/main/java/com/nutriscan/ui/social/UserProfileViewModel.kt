package com.nutriscan.ui.social

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import com.nutriscan.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userID: String = checkNotNull(savedStateHandle["userID"])

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    private val _userPosts = MutableStateFlow<List<Post>>(emptyList())
    val userPosts: StateFlow<List<Post>> = _userPosts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _followersCount = MutableStateFlow(0)
    val followersCount: StateFlow<Int> = _followersCount.asStateFlow()

    private val _followingCount = MutableStateFlow(0)
    val followingCount: StateFlow<Int> = _followingCount.asStateFlow()

    private val _postsCount = MutableStateFlow(0)
    val postsCount: StateFlow<Int> = _postsCount.asStateFlow()

    init {
        loadUserProfile()
        loadUserPosts()
        checkIfFollowing()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = socialRepository.getUserProfile(uid = userID)
                result.onSuccess {
                    user ->
                    _userProfile.value = user
                    _followersCount.value = user?.numFollowers ?: 0
                    _followingCount.value = user?.numFollowing ?: 0
                    _postsCount.value = user?.numPosts ?: 0
                }.onFailure {
                    e ->
                    _error.value = "Failed to load profile: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadUserPosts() {
        viewModelScope.launch {
            try {
                socialRepository.getUserPosts(userID = userID)
                    .catch {
                        e ->
                        _error.value = "Failed to load posts: ${e.message}"
                    }
                    .collect {
                        posts ->
                        _userPosts.value = posts
                    }
            } catch (e: Exception) {
                _error.value = "Error loading posts: ${e.message}"
            }
        }
    }

    private fun checkIfFollowing() {
        viewModelScope.launch {
            socialRepository.isFollowing(userIDToCheck = userID)
                .catch {
                    e ->
                    _error.value = "Failed to check follow status: ${e.message}"
                }
                .collect {
                    following ->
                    _isFollowing.value = following
                }
        }
    }

    fun followUser() {
        viewModelScope.launch {
            socialRepository.followUser(userIDToFollow = userID)
                .onSuccess {
                    _isFollowing.value = true
                    _followersCount.value += 1
                }
                .onFailure { e ->
                    _error.value = "Failed to follow user: ${e.message}"
                }
        }
    }

    fun unfollowUser() {
        viewModelScope.launch {
            socialRepository.unfollowUser(userIDToUnfollow = userID)
                .onSuccess {
                    _isFollowing.value = false
                    _followersCount.value -= 1
                }
                .onFailure { e ->
                    _error.value = "Failed to unfollow user: ${e.message}"
                }
        }
    }

    fun clearError() {
        _error.value = null
    }
}