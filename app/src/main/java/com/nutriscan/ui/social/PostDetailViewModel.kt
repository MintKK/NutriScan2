package com.nutriscan.ui.social

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import com.nutriscan.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val repository: SocialRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val postID: String = checkNotNull(savedStateHandle["postID"])

    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()
    
    // We already have currentUser in SocialViewModel or FeedViewModel.
    // For Post detail we just need the Post itself.
    // We'll also need the author (User) if it's not embedded fully or if we want their live profile picture.
    private val _author = MutableStateFlow<User?>(null)
    val author: StateFlow<User?> = _author.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    val isLiked: StateFlow<Boolean> = repository.isPostLikedByUser(postID)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        loadPost()
    }

    private fun loadPost() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            repository.getPostById(postID).fold(
                onSuccess = { fetchedPost ->
                    _post.value = fetchedPost
                    if (fetchedPost != null) {
                        loadAuthor(fetchedPost.userID)
                    } else {
                        _error.value = "Post not found"
                        _isLoading.value = false
                    }
                },
                onFailure = { e ->
                    _error.value = e.message ?: "Failed to load post"
                    _isLoading.value = false
                }
            )
        }
    }

    private suspend fun loadAuthor(userId: String) {
        repository.getUserProfile(userId).fold(
            onSuccess = { user ->
                _author.value = user
                _isLoading.value = false
            },
            onFailure = { e ->
                // It's okay if author fails, we still show the post
                _isLoading.value = false
            }
        )
    }

    // Debounce flag to prevent spamming the like button
    private var isLiking = false

    // Toggle like
    fun toggleLike() {
        if (isLiking) return
        val currentPost = _post.value ?: return
        isLiking = true
        viewModelScope.launch {
            try {
                repository.toggleLike(currentPost.postID).onSuccess {
                    // To keep it simple, reload or manually patch
                    loadPost()
                }
            } finally {
                isLiking = false
            }
        }
    }
}
