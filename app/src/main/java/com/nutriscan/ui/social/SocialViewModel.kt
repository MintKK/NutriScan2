package com.nutriscan.ui.social

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import com.nutriscan.data.remote.models.Comment
import com.nutriscan.data.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _feedPosts = MutableStateFlow<List<Post>>(emptyList())
    val feedPosts: StateFlow<List<Post>> = _feedPosts.asStateFlow()

    private val _followingPosts = MutableStateFlow<List<Post>>(emptyList())
    val followingPosts: StateFlow<List<Post>> = _followingPosts.asStateFlow()

    private val _allPosts = MutableStateFlow<List<Post>>(emptyList())
    val allPosts: StateFlow<List<Post>> = _allPosts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _firebaseAvailable = MutableStateFlow<Boolean?>(null)
    val firebaseAvailable: StateFlow<Boolean?> = _firebaseAvailable.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.UNKNOWN)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    val currentUser: StateFlow<User?> = socialRepository.getCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // sign in and out
    private val _isSignInSuccessful = MutableStateFlow(false)
    val isSignInSuccessful: StateFlow<Boolean> = _isSignInSuccessful.asStateFlow()

    private val _isSignUpSuccessful = MutableStateFlow(false)
    val isSignUpSuccessful: StateFlow<Boolean> = _isSignUpSuccessful.asStateFlow()

    init {
        checkAuthState()
        checkFirebaseAvailability()
    }

    fun loadFeed() {
        viewModelScope.launch {

            val currentUserID = auth.currentUser?.uid
            if (currentUserID == null) {
                _error.value = "Please sign in to view the feed"
                _isLoading.value = false

                return@launch
            }

            if (_feedPosts.value.isEmpty()) {
                _isLoading.value = true
            }

            try {
                socialRepository.getFeedPostsRealtime(currentUserID)
                    .catch {
                        e ->
                        if (e.message?.contains("PERMISSION_DENIED") == true) {
                            _error.value = "Unable to access feed. Please check your permissions."
                        } else {
                            _error.value = "Failed to load feed: ${e.message}"
                        }
                        _isLoading.value = false
                    }
                    .collect {
                        posts ->
                        _feedPosts.value = posts
                        _isLoading.value = false
                        _error.value = null
                    }
            } catch (e: Exception) {
                _error.value = "Error loading feed: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun loadFollowingFeed() {
        viewModelScope.launch {
            val currentUserID = auth.currentUser?.uid
            if (currentUserID == null) {
                _error.value = "Please sign in to view following feed"
                _isLoading.value = false

                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                socialRepository.getFeedPostsRealtime(currentUserID)
                    .catch {
                        e ->
                        _error.value = "Failed to load feed: ${e.message}"
                        _isLoading.value = false
                    }
                    .collect {
                        posts ->
                        _followingPosts.value = posts
                        _isLoading.value = false
                        _error.value = null
                    }
            } catch (e: Exception) {
                _error.value = "Error loading feed: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun loadAllFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                socialRepository.getAllPosts()
                    .catch {
                        e ->
                        _error.value = "Failed to load posts: ${e.message}"
                        _isLoading.value = false
                    }
                    .collect {
                        posts ->
                        _allPosts.value = posts
                        _isLoading.value = false
                        _error.value = null
                    }
            } catch (e: Exception) {
                _error.value = "Error loading posts: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun updatePostRealtime(postID: String): Flow<Post> {
        return socialRepository.updatePostRealtime(postID)
    }

    fun createPost(
        caption: String,
        foodName: String,
        calories: Int,
        protein: Float,
        imageUri: Uri
    ) {
        viewModelScope.launch {
            socialRepository.createPost(
                caption = caption,
                foodName = foodName,
                calories = calories,
                protein = protein,
                imageUri = imageUri
            ).onFailure {
                e ->
                _error.value = e.message
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

    fun getCommentsForPost(postId: String): Flow<List<Comment>> {
        return socialRepository.getCommentsForPost(postId)
    }

    // not using, i put in user profile vm
    fun followUser(userID: String) {
        viewModelScope.launch {
            socialRepository.followUser(userID)
                .onFailure {
                    e ->
                    _error.value = e.message
                }
        }
    }

    // not using, i put in user profile vm
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

    fun setError(message: String) {
        _error.value = message
    }

    fun checkFirebaseAvailability() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val isAvailable = try {
                    socialRepository.testFirestoreConnection()
                } catch (e: Exception) {
                    false
                }

                _firebaseAvailable.value = isAvailable

                if (!isAvailable) {
                    _error.value = "Unable to connect to server. Please check your internet connection."
                    _isLoading.value = false
                    return@launch
                }

                // check if user is authenticated
                if (auth.currentUser == null) {
                    _error.value = "Please sign in to view the feed"
                    _isLoading.value = false
                } else {
                    _error.value = null
                    loadFeed()
                }
            } catch (e: Exception) {
                _firebaseAvailable.value = false
                _error.value =
                    when {
                        e.message?.contains("Google Play Services", ignoreCase = true) == true -> {
                            "Google Play Services is required. Please run on a device with Google Play."
                        }
                        e.message?.contains("timeout", ignoreCase = true) == true -> {
                            "Connection timeout. Please check your internet and try again."
                        }
                        else -> {
                            "Connection error: ${e.message}"
                        }
                }
                _isLoading.value = false
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                auth.signInWithEmailAndPassword(email, password).await()
                _error.value = null
                _isSignInSuccessful.value = true
            } catch (e: FirebaseAuthInvalidUserException) {
                android.util.Log.e("SocialViewModel", "No account found", e)
                _error.value = "No account found with this email"
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                android.util.Log.e("SocialViewModel", "Invalid credentials", e)
                _error.value = "Invalid email or password"
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Sign in failed", e)
                _error.value = "Sign in failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUp(email: String, password: String, username: String, displayName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = result.user ?: throw Exception("Failed to create user")

                // create user profile in Firestore
                val user = User(
                    uid = firebaseUser.uid,
                    username = username,
                    displayname = displayName,
                    bio = "",
                    profileImageUrl = "",
                    email = email,
                    created = System.currentTimeMillis(),
                    numFollowers = 0,
                    numFollowing = 0,
                    numPosts = 0
                )

                val createResult = socialRepository.createUserProfile(user)
                createResult.getOrThrow()

                android.util.Log.d("SocialViewModel", "User profile created in Firestore")

                _error.value = null
                _isSignUpSuccessful.value = true
                android.util.Log.d("SocialViewModel", "Sign up successful, flag set to true")
            } catch (e: FirebaseAuthUserCollisionException) {
                android.util.Log.e("SocialViewModel", "Email already in use", e)
                _error.value = "Email already in use"
            } catch (e: FirebaseAuthWeakPasswordException) {
                android.util.Log.e("SocialViewModel", "Weak password", e)
                _error.value = "Password is too weak"
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                android.util.Log.e("SocialViewModel", "Invalid email format", e)
                _error.value = "Invalid email format"
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Sign up failed", e)
                _error.value = "Sign up failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                android.util.Log.d("SocialViewModel", "Signing out user: ${auth.currentUser?.uid}")

                _feedPosts.value = emptyList()
                _error.value = null
                _isSignInSuccessful.value = false
                _isSignUpSuccessful.value = false
                _firebaseAvailable.value = null

                delay(200)

                auth.signOut()

                android.util.Log.d("SocialViewModel", "Sign out successful")

                _error.value = "Please sign in to view the feed"
            } catch (e: Exception) {
                android.util.Log.e("SocialViewModel", "Error during sign out", e)
            }
        }
    }

    fun resetSuccessFlags() {
        _isSignInSuccessful.value = false
        _isSignUpSuccessful.value = false
    }

    enum class AuthState {
        UNKNOWN,
        AUTHENTICATED,
        UNAUTHENTICATED
    }

    private fun checkAuthState() {
        authStateListener?.let { auth.removeAuthStateListener(it) }

        authStateListener = FirebaseAuth.AuthStateListener {
            firebaseAuth ->
            val user = firebaseAuth.currentUser
            viewModelScope.launch {
                _authState.value =
                    if (user != null) {
                        AuthState.AUTHENTICATED
                    } else {
                        AuthState.UNAUTHENTICATED
                    }

                _isAuthenticated.value = user != null

                android.util.Log.d("SocialViewModel", "Auth state changed: ${_authState.value}")

                if (user != null) {
                    if (_error.value == "Please sign in to view the feed" ||
                        _error.value?.contains("sign in") == true) {
                        _error.value = null
                    }

                    loadFeed()
                } else {
                    _feedPosts.value = emptyList()
                    _error.value = "Please sign in to view the feed"
                }
            }
        }

        authStateListener?.let { auth.addAuthStateListener(it) }
    }

    // temporary function for testing
    fun signInForTesting() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                // sign in anonymously
                val result = auth.signInAnonymously().await()

                // check if we need to create a user profile
                val currentUser = result.user
                if (currentUser != null) {
                    // check if user profile exists in Firestore
                    val userProfile = socialRepository.getUserProfile(currentUser.uid).getOrNull()
                    if (userProfile == null) {
                        // create a basic profile for anonymous user
                        val anonymousUser = User(
                            uid = currentUser.uid,
                            username = "Anonymous${currentUser.uid.takeLast(4)}",
                            displayname = "Anonymous User",
                            bio = "",
                            profileImageUrl = "",
                            email = currentUser.email ?: "",
                            created = System.currentTimeMillis()
                        )
                        socialRepository.createUserProfile(anonymousUser)
                    }
                }

                _error.value = null
            } catch (e: Exception) {
                _error.value = "Test sign in failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}