package com.nutriscan.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.rememberAsyncImagePainter
import com.nutriscan.data.remote.models.Post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateToCreatePost: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val posts by viewModel.feedPosts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val error by viewModel.error.collectAsState()
    val firebaseAvailable by viewModel.firebaseAvailable.collectAsState()
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(Unit) {
        // Check if Firebase is available
        viewModel.checkFirebaseAvailability()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Feed") },
                actions = {
                    IconButton(onClick = {
                        /* Open search */
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    if (authState == SocialViewModel.AuthState.AUTHENTICATED) {
                        IconButton(
                            onClick = {
                                if (currentUser != null) {
                                    onNavigateToProfile(currentUser!!.uid)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Profile")
                        }
                    } else {
                        IconButton(
                            onClick = onNavigateToSignIn
                        ) {
                            Icon(Icons.Default.Login, contentDescription = "Sign In")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (authState == SocialViewModel.AuthState.AUTHENTICATED) {
                FloatingActionButton(
                    onClick = onNavigateToCreatePost,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Post")
                }
            }
        }
    ) {
        padding ->

        when {
            // error message exist
            error != null -> {
                ErrorScreen(
                    error = error!!,
                    onRetry = {
                        viewModel.clearError()
                        viewModel.checkFirebaseAvailability()
                    },
                    onSignIn = onNavigateToSignIn,
                    isAuthenticated = (authState == SocialViewModel.AuthState.AUTHENTICATED),
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }

            // will be loading
            isLoading && posts.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // not loading
            !isLoading && posts.isEmpty() && authState == SocialViewModel.AuthState.AUTHENTICATED -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No posts yet. Follow some users or create your first post!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = posts,
                        key = { it.postID }
                    ) { post ->

                        val isLiked by viewModel.isPostLikedByUser(post.postID)
                            .collectAsState(initial = false)

                        PostCard(
                            post = post,
                            onLikeClick = {
                                if (authState == SocialViewModel.AuthState.AUTHENTICATED) {
                                    if (isLiked) {
                                        viewModel.unlikePost(post.postID)
                                    } else {
                                        viewModel.likePost(post.postID)
                                    }
                                } else {
                                    // Show sign in prompt
                                    viewModel.setError("Please sign in to like posts")
                                }
                            },
                            onCommentClick = {
                                /* Navigate to comments */
                            },
                            onProfileClick = {
                                onNavigateToProfile(post.userID)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    var isLiked by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // user info row
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // user profile image
                Surface(
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable() { onProfileClick() },
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (post.userProfileImageUrl.isNotEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(post.userProfileImageUrl),
                            contentDescription = "Profile",
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = post.username,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeFormat.format(Date(post.created)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // food name chip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = post.foodName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // post image
            Image(
                painter = rememberAsyncImagePainter(post.foodImageUrl),
                contentDescription = "Food",
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentScale = ContentScale.Crop
            )

            // caption
            if (post.caption.isNotEmpty()) {
                Text(
                    text = post.caption,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // nutrition info
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NutritionInfo("Calories", "${post.numCalories}")
                    NutritionInfo("Protein", "${post.numProtein.toInt()}g")
                    //NutritionInfo("Carbs", "${post.carbs.toInt()}g")
                    //NutritionInfo("Fat", "${post.fat.toInt()}g")
                }
            }

            // action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // like button
                IconButton(onClick = onLikeClick) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${post.numLikes}")
                    }
                }

                // comment button
                IconButton(onClick = onCommentClick) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Comment,
                            contentDescription = "Comment"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${post.numComments}")
                    }
                }

                // share button
                IconButton(onClick = { /* Share */ }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share"
                    )
                }
            }
        }
    }
}

@Composable
fun NutritionInfo(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    isAuthenticated: Boolean,
    viewModel: SocialViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unable to Load Feed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (!isAuthenticated) {
                Button(onClick = onSignIn) {
                    Text("Sign In")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onRetry) {
                    Text("Retry Connection")
                }
            } else if (error.contains("timeout", ignoreCase = true) ||
                error.contains("connection", ignoreCase = true)) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            } else if (error.contains("Google Play Services", ignoreCase = true)) {
                Text(
                    text = "This app requires Google Play Services.\n" +
                            "Please run on a device/emulator with Google Play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            } else {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }

            if (!isAuthenticated && error.contains("sign in", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        // test sign-in for development
                        viewModel.signInForTesting()
                    }
                ) {
                    Text("Test Sign In (Dev Only)")
                }
            }
        }
    }
}