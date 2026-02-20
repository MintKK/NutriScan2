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
class CreatePostViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

//    private val userID: String = checkNotNull(savedStateHandle["userID"])
//
//    private val _userProfile = MutableStateFlow<User?>(null)
//    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()
//
//    private val _userPosts = MutableStateFlow<List<Post>>(emptyList())
//    val userPosts: StateFlow<List<Post>> = _userPosts.asStateFlow()
//
//    private val _isLoading = MutableStateFlow(false)
//    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
//
//    private val _isFollowing = MutableStateFlow(false)
//    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()
//
//    private val _error = MutableStateFlow<String?>(null)
//    val error: StateFlow<String?> = _error.asStateFlow()
//
//    private val _followersCount = MutableStateFlow(0)
//    val followersCount: StateFlow<Int> = _followersCount.asStateFlow()
//
//    private val _followingCount = MutableStateFlow(0)
//    val followingCount: StateFlow<Int> = _followingCount.asStateFlow()
//
//    private val _postsCount = MutableStateFlow(0)
//    val postsCount: StateFlow<Int> = _postsCount.asStateFlow()

    init {
//        loadUserProfile()
//        loadUserPosts()
//        checkIfFollowing()
    }


}