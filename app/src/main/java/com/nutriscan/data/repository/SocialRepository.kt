package com.nutriscan.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import com.nutriscan.data.remote.models.Comment
import com.nutriscan.data.remote.models.Follow
import com.nutriscan.data.remote.models.Like
import com.nutriscan.data.remote.models.Post
import com.nutriscan.data.remote.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow


@Singleton
class SocialRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
) {
    private val postsCollection = firestore.collection("posts")
    private val usersCollection = firestore.collection("users")
    private val commentsCollection = firestore.collection("comments")
    private val likesCollection = firestore.collection("likes")
    private val followsCollection = firestore.collection("follows")

    // user things
    suspend fun createUserProfile(user: User): Result<Boolean> {
        return try {
            usersCollection.document(user.uid).set(user).await()

            Result.success(value = true)
        } catch (e: Exception) {
            Result.failure(exception = e)
        }
    }

    suspend fun getUserProfile(uid: String): Result<User?> {
        return try {
            val document = usersCollection.document(uid).get().await()

            Result.success(value = document.toObject<User>())
        } catch (e: Exception) {
            Result.failure(exception = e)
        }
    }

    fun getCurrentUser(): Flow<User?> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            trySend(null)
            close()

            return@callbackFlow
        }

        val listener = usersCollection.document(currentUser.uid).addSnapshotListener {
            snapshot, error ->
            if (error != null) {
                close(error)

                return@addSnapshotListener
            }

            trySend(snapshot?.toObject<User>())
        }

        awaitClose {
            listener.remove()
        }
    }

    // posting
    suspend fun createPost(
        caption: String,
        foodName: String,
        calories: Int,
        protein: Float,
        imageUri: Uri
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticatd")

            val imageUrl = uploadPostImage(imageUri).getOrThrow()

            val userProfile = getUserProfile(currentUser.uid).getOrThrow() ?:
                throw Exception("User profile not found")

            val post = Post(
                postID =  postsCollection.document().id,
                userID = currentUser.uid,
                username = userProfile.username,
                userProfileImageUrl =  userProfile.profileImageUrl,
                caption = caption,
                foodImageUrl = imageUrl,
                foodName = foodName,
                numCalories = calories,
                numProtein = protein
            )

            // probably have to call this calculation in real time at intervals e.g. every hour
            post.trendingScore = calculateTrendingScore(likes = post.numLikes, comments = post.numComments, timestamp = System.currentTimeMillis())

            postsCollection.document(post.postID).set(post).await()

            // increment post count
            usersCollection.document(currentUser.uid)
                .update("postCount", FieldValue.increment(1))
                .await()

            Result.success(value = post.postID)
        } catch (e: Exception) {
            Result.failure(exception = e)
        }
    }

    suspend fun uploadPostImage(imageUri: Uri): Result<String> {
        return try {

            // testing will skip actual upload for local file URIs
            if (imageUri.scheme == "file") {
                return Result.success("https://via.placeholder.com/300x300?text=Test+Food")
            }

            val filename = "posts/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(filename)
            ref.putFile(imageUri).await()

            val downloadUrl = ref.downloadUrl.await()

            Result.success(value = downloadUrl.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(exception = e)
        }
    }

    fun getPosts(
        sortby: String = "trendingScore",
        limit: Long = 20
    ): Flow<PagingData<Post>> = callbackFlow {
        var lastVisible: QueryDocumentSnapshot? = null

        val query = postsCollection.orderBy(sortby, Query.Direction.DESCENDING).limit(limit)

        val listener = query.addSnapshotListener {
            snapshot, error ->
            if (error != null) {
                close(error)

                return@addSnapshotListener
            }

            if (snapshot != null) {
                val posts = snapshot.toObjects<Post>()
                if (snapshot.documents.isNotEmpty()) {
                    lastVisible = snapshot.documents.last() as QueryDocumentSnapshot
                }

                trySend(PagingData(items = posts, lastVisible = lastVisible))
            }
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun loadMorePosts(
        lastVisible: QueryDocumentSnapshot,
        sortby: String = "trendingScore",
        limit: Long = 20
    ): Result<List<Post>> {
        return try {
            val snapshot = postsCollection
                .orderBy(sortby, Query.Direction.DESCENDING)
                .startAfter(lastVisible)
                .limit(limit)
                .get()
                .await()

            Result.success(value = snapshot.toObjects())
        } catch (e: Exception) {
            Result.failure(exception = e)
        }
    }

    fun getFeedPosts(userId: String): Flow<List<Post>> = flow<List<Post>> {
        // Get following list
        val followsSnapshot = followsCollection
            .whereEqualTo("followerID", userId)
            .get()
            .await()

        val followingIds = followsSnapshot.documents.mapNotNull { it.getString("followingID") }
        val allUserIDs = followingIds + userId

        if (allUserIDs.isEmpty()) {
            emit(emptyList<Post>())
            return@flow
        }

        // Get posts
        val postsSnapshot = postsCollection
            .whereIn("userID", allUserIDs)
            .orderBy("created", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .await()

        emit(postsSnapshot.toObjects<Post>())
    }.flowOn(Dispatchers.IO)

    fun getFeedPostsRealtime(userId: String): Flow<List<Post>> = callbackFlow {
        var followingListener: ListenerRegistration? = null
        var postsListener: ListenerRegistration? = null

        // Listen to follows changes
        followingListener = followsCollection
            .whereEqualTo("followerID", userId)
            .addSnapshotListener { followsSnapshot, error ->
                if (error != null) {
                    close(error)

                    return@addSnapshotListener
                }

                val followingIDs = followsSnapshot?.documents?.mapNotNull { it.getString("followingID") } ?: emptyList()
                val allUserIDs = followingIDs + userId

                if (allUserIDs.isEmpty()) {
                    trySend(emptyList<Post>())

                    return@addSnapshotListener
                }

                // Remove previous posts listener if exists
                postsListener?.remove()

                // Create new posts listener with updated following list
                postsListener = postsCollection
                    .whereIn("userID", allUserIDs)
                    .orderBy("created", Query.Direction.DESCENDING)
                    .limit(50)
                    .addSnapshotListener { postsSnapshot, postsError ->
                        if (postsError != null) {
                            return@addSnapshotListener
                        }
                        trySend(postsSnapshot?.toObjects<Post>() ?: emptyList<Post>())
                    }
            }

        awaitClose {
            followingListener?.remove()
            postsListener?.remove()
        }
    }

    fun getUserPosts(userID: String): Flow<List<Post>> = flow {
        try {
            val snapshot = postsCollection
                .whereEqualTo("userID", userID)
                .orderBy("created", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            emit(snapshot.toObjects<Post>())
        } catch (e: Exception) {
            // Return empty list on error, error will be handled by ViewModel
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun getUserPostsRealtime(userID: String): Flow<List<Post>> = callbackFlow {
        val listener = postsCollection
            .whereEqualTo("userID", userID)
            .orderBy("created", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener {
                snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.toObjects<Post>() ?: emptyList())
            }

        awaitClose { listener.remove() }
    }

    // like
    suspend fun likePost(postID: String): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
            val like = Like(
                likeID = "${postID}_${currentUser.uid}",
                postID = postID,
                userID = currentUser.uid
            )

            likesCollection.document(like.likeID).set(like).await()

            // increment post likes count
            postsCollection.document(postID)
                .update("likesCount", FieldValue.increment(1))
                .await()

            Result.success(value = true)
        } catch (e: Exception) {
            Result.failure(exception = e)
        }
    }

    suspend fun unlikePost(postId: String): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            likesCollection.document("${postId}_${currentUser.uid}").delete().await()

            // Decrement post likes count
            postsCollection.document(postId)
                .update("likesCount", FieldValue.increment(-1))
                .await()

            Result.success(value = true)
        } catch (e: Exception) {
            Result.failure(exception = e)
        }
    }

    fun isPostLikedByUser(postId: String): Flow<Boolean> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            trySend(false)
            close()

            return@callbackFlow
        }

        val listener = likesCollection.document("${postId}_${currentUser.uid}")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)

                    return@addSnapshotListener
                }

                trySend(snapshot?.exists() == true)
            }

        awaitClose { listener.remove() }
    }

    // comments
    suspend fun addComment(postID: String, content: String): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")
            val userProfile = getUserProfile(currentUser.uid).getOrThrow()
                ?: throw Exception("User profile not found")

            val comment = Comment(
                commentID = commentsCollection.document().id,
                postID = postID,
                userID = currentUser.uid,
                username = userProfile.username,
                userProfileImageUrl = userProfile.profileImageUrl,
                content = content
            )

            commentsCollection.document(comment.commentID).set(comment).await()

            // Increment post comments count
            postsCollection.document(postID)
                .update("commentsCount", FieldValue.increment(1))
                .await()

            Result.success(comment.commentID)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCommentsForPost(postID: String): Flow<List<Comment>> = callbackFlow {
        val listener = commentsCollection
            .whereEqualTo("postID", postID)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)

                    return@addSnapshotListener
                }

                trySend(snapshot?.toObjects<Comment>() ?: emptyList<Comment>())
            }

        awaitClose { listener.remove() }
    }

    // follow
    suspend fun followUser(userIDToFollow: String): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            val follow = Follow(
                followID = "${currentUser.uid}_${userIDToFollow}",
                followerID = currentUser.uid,
                followingID = userIDToFollow
            )

            followsCollection.document(follow.followID).set(follow).await()

            // Update counts
            usersCollection.document(currentUser.uid)
                .update("followingCount", FieldValue.increment(1))
                .await()

            usersCollection.document(userIDToFollow)
                .update("followersCount", FieldValue.increment(1))
                .await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unfollowUser(userIDToUnfollow: String): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            followsCollection.document("${currentUser.uid}_${userIDToUnfollow}").delete().await()

            // Update counts
            usersCollection.document(currentUser.uid)
                .update("followingCount", FieldValue.increment(-1))
                .await()

            usersCollection.document(userIDToUnfollow)
                .update("followersCount", FieldValue.increment(-1))
                .await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isFollowing(userIDToCheck: String): Flow<Boolean> = callbackFlow {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            trySend(false)
            close()

            return@callbackFlow
        }

        val listener = followsCollection.document("${currentUser.uid}_${userIDToCheck}")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)

                    return@addSnapshotListener
                }

                trySend(snapshot?.exists() == true)
            }

        awaitClose { listener.remove() }
    }

    // trending score
    private fun calculateTrendingScore(likes: Int, comments: Int, timestamp: Long): Double {
        // algorithm calculation can be changed
        // testing with this for now
        val now = System.currentTimeMillis()
        val hoursSincePost = (now - timestamp) / (1000 * 60 * 60).toDouble()
        val engagement = (likes + comments  * 2).toDouble()

        return if (hoursSincePost < 24) {
            engagement / (hoursSincePost + 2).pow(x = 1.5)
        } else {
            engagement / (hoursSincePost + 2).pow(x = 1.8)
        }
    }

    // pagination
    data class PagingData<T>(
        val items: List<T>,
        val lastVisible: QueryDocumentSnapshot?
    )

    suspend fun testFirestoreConnection(): Boolean {
        return try {
            withTimeout(10000L) {
                // Try a simple operation that doesn't require authentication
                // Using collection group query might work better
                try {
                    postsCollection.limit(1).get().await()
                    true
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        // This might actually be okay - it means Firestore is reachable
                        // but requires authentication
                        true
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw Exception("Firestore connection timeout")
        } catch (e: Exception) {
            throw Exception("Firestore connection failed: ${e.message}")
        }
    }
}