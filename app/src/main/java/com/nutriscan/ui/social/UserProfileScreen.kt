package com.nutriscan.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nutriscan.ui.common.ImagePickerResult
import com.nutriscan.ui.common.GalleryImagePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userID: String,
    onBack: () -> Unit,
    onNavigateToFeed: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val userProfile by viewModel.userProfile.collectAsState()       // User data class
    val userPosts by viewModel.userPosts.collectAsState()           // list of User's Posts
    val isLoading by viewModel.isLoading.collectAsState()           // loading progress
    val isFollowing by viewModel.isFollowing.collectAsState()       // boolean for whether following this User
    val error by viewModel.error.collectAsState()                   // error message
    val followersCount by viewModel.followersCount.collectAsState() // num of followers for this User
    val followingCount by viewModel.followingCount.collectAsState() // num of following for this User
    val postsCount by viewModel.postsCount.collectAsState()         // num of posts for this User

    // get current user ID from SocialViewModel
    // to tell if this user profile is their own profile
    val socialViewModel: SocialViewModel = hiltViewModel()
    val currentUserID by socialViewModel.currentUser.collectAsState()

    // determine if this is the current user's profile
    val isCurrentUser = currentUserID?.uid == userID

    // call sign out and navigate
    fun handleSignOut() {
        onBack()

        viewModel.viewModelScope.launch {
            delay(100)

            socialViewModel.signOut()
            onSignOut()
        }
    }

    var showImagePicker by remember { mutableStateOf(false) }
    var isUploadingProfilePic by remember { mutableStateOf(false) }

    fun handleImagePickerResult(result: ImagePickerResult) {
        showImagePicker = false
        when (result) {
            is ImagePickerResult.Success -> {
                viewModel.updateProfilePicture(result.uri)
            }
            is ImagePickerResult.Error -> {
                // Show error (you might want to add error state to ViewModel)
            }
            is ImagePickerResult.Cancelled -> {
                // User cancelled
            }
        }
    }

    // Observe upload state
    val isUploading by viewModel.isUploadingProfilePic.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (userProfile != null) {
                            userProfile!!.displayname
                        } else {
                            "Profile"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) {
        padding ->
        if (isLoading && userProfile == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null && userProfile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error loading profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {

            // successful loading of user profile
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item {
                    ProfileHeader(
                        userProfile = userProfile,
                        isFollowing = isFollowing,
                        followersCount = followersCount,
                        followingCount = followingCount,
                        postsCount = postsCount,
                        onFollowClick = {
                            if (isFollowing) {
                                viewModel.unfollowUser()
                            } else {
                                viewModel.followUser()
                            }
                        },
                        isCurrentUser = isCurrentUser,
                        onSignOut = { handleSignOut() },
                        onEditProfilePicture = if (isCurrentUser) {
                            { showImagePicker = true }
                        } else null,
                        isUploadingProfilePic = isUploading
                    )
                }

                // list of user's posts
                items(
                    items = userPosts,
                    key = { it.postID }
                ) {
                    post ->
                    ProfilePostItem(post = post)
                }

                // if user didnt post then show this
                if (userPosts.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No posts yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImagePicker) {
        GalleryImagePicker {
            result ->
            handleImagePickerResult(result)
        }
    }
}

@Composable
fun ProfileHeader(
    userProfile: User?,
    isFollowing: Boolean,
    followersCount: Int,
    followingCount: Int,
    postsCount: Int,
    onFollowClick: () -> Unit,
    isCurrentUser: Boolean,
    onSignOut: () -> Unit,
    onEditProfilePicture: (() -> Unit)? = null,
    isUploadingProfilePic: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // user profile picture
//            Surface(
//                modifier = Modifier.size(80.dp).clip(CircleShape),
//                color = MaterialTheme.colorScheme.primaryContainer
//            ) {
//                if (userProfile?.profileImageUrl?.isNotEmpty() == true) {
//                    Image(
//                        painter = rememberAsyncImagePainter(userProfile.profileImageUrl),
//                        contentDescription = "Profile",
//                        contentScale = ContentScale.Crop
//                    )
//                } else {
//                    Icon(
//                        Icons.Default.Person,
//                        contentDescription = null,
//                        modifier = Modifier.padding(16.dp)
//                    )
//                }
//            }

            Box(
                modifier = Modifier.size(80.dp)
            ) {
                Surface(
                    modifier = Modifier.size(80.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (userProfile?.profileImageUrl?.isNotEmpty() == true) {
                        Image(
                            painter = rememberAsyncImagePainter(userProfile.profileImageUrl),
                            contentDescription = "Profile",
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Edit button overlay for current user
                if (onEditProfilePicture != null && !isUploadingProfilePic) {
                    IconButton(
                        onClick = onEditProfilePicture,
                        modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit profile picture",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }

                if (isUploadingProfilePic) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // user stats
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStat(count = postsCount, label = "Posts")
                ProfileStat(count = followersCount, label = "Followers")
                ProfileStat(count = followingCount, label = "Following")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // user display name
        Text(
            text = userProfile?.displayname ?: "Unknown User",
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.titleLarge.fontSize
        )

        // user app username
        Text(
            text = "@${userProfile?.username ?: "username"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // user bio
        if (userProfile?.bio?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = userProfile.bio,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // show sign out button if profile is current user
        if (isCurrentUser) {
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sign Out")
            }
        }
        // else show the following button
        else {
            Button(
                onClick = onFollowClick,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isFollowing) {
                    ButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isFollowing) "Following" else "Follow")
            }
        }

        Divider(
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

@Composable
fun ProfileStat(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProfilePostItem(post: Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Image(
                painter = rememberAsyncImagePainter(post.foodImageUrl),
                contentDescription = "Food post",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.foodName,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.numLikes}")

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        Icons.Default.Comment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.numComments}")
                }
            }
        }
    }
}