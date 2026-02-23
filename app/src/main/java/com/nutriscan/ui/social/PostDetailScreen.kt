package com.nutriscan.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postID: String,
    onNavigateToProfile: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val post by viewModel.post.collectAsState()
    val author by viewModel.author.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()

    LaunchedEffect(viewModel.eventFlow) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PostDetailEvent.Deleted -> {
                    post?.let { onNavigateToProfile(it.userID) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && post == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (post != null) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        // Note: To simplify, we track isLiked in the SocialViewModel which PostCard uses directly in FeedScreen
                        // PostCard itself is strongly coupled with SocialViewModel.
                        PostCard(
                            post = post!!,
                            onLikeClick = { viewModel.toggleLike() },
                            onCommentClick = { /* Scroll to comments */ },
                            onAddComment = { commentContent ->
                                // Optional logic here if needed, PostCard usually handles this via its own SocialViewModel
                                // but we must provide the lambda
                            },
                            onProfileClick = { onNavigateToProfile(it) },
                            onDeleteClick = { viewModel.deletePost() },
                            isLiked = isLiked
                        )
                    }
                }
            }
        }
    }
}
