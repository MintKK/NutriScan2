package com.nutriscan.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateToCreatePost: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val posts by viewModel.feedPosts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        // Check if Firebase is available
        viewModel.checkFirebaseAvailability()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Forum") },
                actions = {
                    IconButton(onClick = {
                        /* Open search */
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = {
                        onNavigateToProfile(currentUser?.uid ?: "")
                    }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreatePost,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Post")
            }
        }
    ) {
        padding ->

        when {
            error != null -> {
                ErrorScreen(
                    error = error!!,
                    onRetry = {
                        viewModel.clearError()
                        viewModel.checkFirebaseAvailability()
                      },
                    modifier = Modifier.padding(padding)
                )
            }

            isLoading && posts.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
                                if (isLiked) {
                                    viewModel.unlikePost(post.postID)
                                } else {
                                    viewModel.likePost(post.postID)
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
            // User info row
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile image
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

                // Food name chip
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

            // Post image
            Image(
                painter = rememberAsyncImagePainter(post.foodImageUrl),
                contentDescription = "Food",
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentScale = ContentScale.Crop
            )

            // Caption
            if (post.caption.isNotEmpty()) {
                Text(
                    text = post.caption,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Nutrition info
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

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
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
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Firebase Error",
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
            if (error.contains("Google Play Services", ignoreCase = true)) {
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
        }
    }
}