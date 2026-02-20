package com.nutriscan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nutriscan.ui.activity.ActivityTrackerScreen
import com.nutriscan.ui.addmeal.AddMealScreen
import com.nutriscan.ui.analytics.AnalyticsScreen
import com.nutriscan.ui.calorietarget.CalorieTargetScreen
import com.nutriscan.ui.caloriesburned.CaloriesBurnedScreen
import com.nutriscan.ui.dashboard.DashboardScreen
import com.nutriscan.ui.social.FeedScreen

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object AddMeal : Screen("add_meal")
    object Analytics : Screen("analytics")

    object CalorieTarget : Screen("calorie_target")
    object CaloriesBurned : Screen("calories_burned")
    object ActivityTracker : Screen("activity_tracker")

    object Feed : Screen("feed")
    object CreatePost : Screen("create_post")
    object PostDetails : Screen("post_details/{postID}") {
        fun passPostID(postID: String): String = "post_details/$postID"
    }
    object UserProfile : Screen("user_profile/{userID}") {
        fun passUserID(userID: String): String = "user_profile/$userID"
    }
}

@Composable
fun NutriScanNavHost(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddMealClick = { navController.navigate(Screen.AddMeal.route) },
                onAnalyticsClick = { navController.navigate(Screen.Analytics.route) },
                onCaloriesBurnedClick = { navController.navigate(Screen.CaloriesBurned.route) },
                onFeedClick = { navController.navigate(Screen.Feed.route) }
            )
        }
        
        composable(Screen.AddMeal.route) {
            AddMealScreen(
                onMealLogged = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                onCalorieTargetClick = { navController.navigate(Screen.CalorieTarget.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CalorieTarget.route) {
            CalorieTargetScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.CaloriesBurned.route) {
            CaloriesBurnedScreen(
                onBack = { navController.popBackStack() },
                onActivityTrackerClick = { navController.navigate(Screen.ActivityTracker.route) }
            )
        }
        
        composable(Screen.ActivityTracker.route) {
            ActivityTrackerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Feed.route) {
            FeedScreen(
                onNavigateToProfile = {  },
                onNavigateToCreatePost = {  },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
