package com.nutriscan.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.Comment
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateToCreatePost: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: SocialViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val followingPosts by viewModel.followingPosts.collectAsState()
    val allPosts by viewModel.allPosts.collectAsState()
    //val posts by viewModel.feedPosts.collectAsState()
    val isLoadingAll by viewModel.isLoadingAll.collectAsState()
    val isLoadingFollowing by viewModel.isLoadingFollowing.collectAsState()
    val isLoading = if (selectedTab == 0) isLoadingAll else isLoadingFollowing
    val currentUser by viewModel.currentUser.collectAsState()
    val error by viewModel.error.collectAsState()
    val firebaseAvailable by viewModel.firebaseAvailable.collectAsState()
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(Unit) {
        // check if Firebase is available
        viewModel.checkFirebaseAvailability()
    }

    LaunchedEffect(authState, selectedTab) {
        if (authState == SocialViewModel.AuthState.AUTHENTICATED) {
            if (selectedTab == 0) {
                viewModel.loadAllFeed()
            } else {
                viewModel.loadFollowingFeed()
            }
        }
    }

    val onAddComment: (String, String) -> Unit = {
        postID, commentText ->
        if (authState == SocialViewModel.AuthState.AUTHENTICATED) {
            viewModel.addComment(postID, commentText)
        } else {
            viewModel.setError("Please sign in to comment")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    //Text("Food Feed")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.width(200.dp)
                        ) {
                            androidx.compose.material3.Tab(
                                selected = selectedTab == 0,
                                onClick = {
                                    selectedTab = 0
                                },
                                text = { Text("Feed") }
                            )
                            androidx.compose.material3.Tab(
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                },
                                text = { Text("Following") }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onNavigateToSearch()
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
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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

        // Display posts based on selected tab
        val posts = if (selectedTab == 0) allPosts else followingPosts

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
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DynamicFeed,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (selectedTab == 0) {
                        Text(
                            text = "Your Feed is Empty",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Follow some users or create your first post!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateToCreatePost) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Post")
                        }
                    } else {
                        Text(
                            text = "You're Not Following Anyone Yet",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Discover new people and get inspired!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateToSearch) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discover People")
                        }
                    }
                }
            }

            else -> {
                key(selectedTab) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = posts,
                            key = { it.postID }
                        ) {
                            post ->

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
                                onAddComment = {
                                    commentText ->
                                    onAddComment(post.postID, commentText)
                                },
                                onProfileClick = {
                                    onNavigateToProfile(it)
                                },
                                isLiked = isLiked
                            )
                        }
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
    onAddComment: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    isLiked: Boolean,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    var showComments by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }

    val commentsFlow = remember(post.postID) {
        viewModel.getCommentsForPost(post.postID)
    }
    val comments by commentsFlow.collectAsState(initial = emptyList())

    val updatedPost by viewModel.updatePostRealtime(post.postID).collectAsState(initial = post)

    val userProfile by viewModel.getUserProfile(post.userID)
        .collectAsState(initial = null)
    val profileImageUrl = userProfile?.profileImageUrl ?: ""
    
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editCaptionText by remember { mutableStateOf(updatedPost.caption) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val isOwner = currentUser?.uid == post.userID

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
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable { onProfileClick(updatedPost.userID) }
                    ) {
                        if (profileImageUrl.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(profileImageUrl),
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = updatedPost.username,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeFormat.format(Date(updatedPost.created)),
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
                        text = updatedPost.foodName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }

                if (isOwner || isAdmin) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Post Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = { 
                                        showMenu = false
                                        editCaptionText = updatedPost.caption
                                        showEditDialog = true
                                    }
                                )
                            }
                            if (isOwner || isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val painter = rememberAsyncImagePainter(
                model = updatedPost.foodImageUrl
            )
            val painterState = painter.state
            val loadedPainter = painterState is AsyncImagePainter.State.Success;
            val intrinsicSize = if (loadedPainter) {
                painterState.painter.intrinsicSize
            } else {
                Size(1f, 0.55f)
            }
            val aspectRatio = if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                intrinsicSize.width / intrinsicSize.height
            } else {
                1f
            }

            // post image
            Image(
                painter = painter,
                contentDescription = "Food",
                modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                contentScale = ContentScale.Crop
            )

            // caption
            if (updatedPost.caption.isNotEmpty()) {
                Text(
                    text = updatedPost.caption,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

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
                    NutritionInfo("Calories", "${updatedPost.numCalories}")
                    NutritionInfo("Protein", "${updatedPost.numProtein.toInt()}g")
                    NutritionInfo("Carbs", "${updatedPost.numCarbs.toInt()}g")
                    NutritionInfo("Fat", "${updatedPost.numFat.toInt()}g")
                }
            }

            // comments area
            if (updatedPost.numComments > 0 || comments.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    TextButton(
                        onClick = { showComments = !showComments },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(
                            text = if (showComments) "Hide comments" else "View all ${post.numComments} comments",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }

                    // Show comments if expanded
                    if (showComments) {
                        val visibleComments = comments.take(n = 5) // Show only first 5 comments
                        val remainingCount = comments.size - visibleComments.size

                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                        ) {
                            visibleComments.forEach {
                                comment ->
                                CommentItem(comment = comment, onProfileClick = onProfileClick)
                            }

                            if (remainingCount > 0) {
                                TextButton(
                                    onClick = { /* Navigate to full comments screen */ },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "View $remainingCount more comments",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        // Show only last 2 comments as preview
                        comments.takeLast(n = 2).forEach {
                            comment ->
                            CommentPreview(comment = comment, onProfileClick = onProfileClick)
                        }
                    }
                }
            }

            // Quick comment input
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    placeholder = { Text("Add a comment...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    shape = RoundedCornerShape(20.dp)
                )
                TextButton(
                    onClick = {
                        if (newCommentText.isNotBlank()) {
                            onAddComment(newCommentText)
                            newCommentText = ""
                        }
                    },
                    enabled = newCommentText.isNotBlank()
                ) {
                    Text(
                        "Post",
                        fontWeight = FontWeight.Bold,
                        color = if (newCommentText.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // like button
                IconButton(onClick = onLikeClick, modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${updatedPost.numLikes}")
                    }
                }

                // comment button
                IconButton(onClick = onCommentClick, modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Comment"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${updatedPost.numComments}")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to delete this post? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePost(post.postID)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Post") },
            text = {
                OutlinedTextField(
                    value = editCaptionText,
                    onValueChange = { editCaptionText = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editPost(post.postID, editCaptionText)
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CommentPreview(comment: Comment, onProfileClick: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onProfileClick(comment.userID) },
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (comment.userProfileImageUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(comment.userProfileImageUrl),
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(4.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = comment.username,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onProfileClick(comment.userID) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = comment.content,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CommentItem(comment: Comment, onProfileClick: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onProfileClick(comment.userID) },
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (comment.userProfileImageUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(comment.userProfileImageUrl),
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(6.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onProfileClick(comment.userID) }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = comment.content,
                    fontSize = 13.sp
                )
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
                    Text("Guest Account")
                }
            }
        }
    }
}