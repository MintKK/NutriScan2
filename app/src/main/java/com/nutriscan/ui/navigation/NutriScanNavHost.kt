package com.nutriscan.ui.navigation

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nutriscan.NutritionTargets
import com.nutriscan.NutritionTargetsPrefs
import com.nutriscan.QuestionnaireResultsScreen
import com.nutriscan.QuestionnaireScreen
import com.nutriscan.WelcomeScreen
import com.nutriscan.ui.activity.ActivityTrackerScreen
import com.nutriscan.ui.addmeal.AddMealScreen
import com.nutriscan.ui.analytics.AnalyticsScreen
import com.nutriscan.ui.auth.SignInScreen
import com.nutriscan.ui.auth.SignUpScreen
import com.nutriscan.ui.caloriesburned.CaloriesBurnedScreen
import com.nutriscan.ui.calorietarget.CalorieTargetScreen
import com.nutriscan.ui.dashboard.DashboardScreen
import com.nutriscan.ui.export.ExportReportScreen
import com.nutriscan.ui.diary.FoodDiaryScreen
import com.nutriscan.ui.social.FeedScreen
import com.nutriscan.ui.social.UserProfileScreen
import com.nutriscan.ui.social.CreatePostScreen

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Questionnaire : Screen("questionnaire")
    object QuestionnaireResults : Screen("questionnaire_results")

    object Dashboard : Screen("dashboard")
    object AddMeal : Screen("add_meal?query={query}") {
        fun passQuery(query: String?): String = "add_meal?query=$query"
    }
    object Analytics : Screen("analytics")

    object CalorieTarget : Screen("calorie_target")
    object CaloriesBurned : Screen("calories_burned")
    object ActivityTracker : Screen("activity_tracker")
    object FoodDiary : Screen("food_diary")
    object WeeklyReport : Screen("weekly_report")

    object Feed : Screen("feed")
    object CreatePost : Screen("create_post")
    object PostDetails : Screen("post_details/{postID}") {
        fun passPostID(postID: String): String = "post_details/$postID"
    }
    object UserProfile : Screen("user_profile/{userID}") {
        fun passUserID(userID: String): String = "user_profile/$userID"
    }

    // Auth
    object SignIn : Screen("sign_in")
    object SignUp : Screen("sign_up")
}

@Composable
fun NutriScanNavHost(
    navController: NavHostController,
    prefs: SharedPreferences,
    showQuestionnaire: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = if (showQuestionnaire) Screen.Welcome.route else Screen.Dashboard.route
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(Screen.Questionnaire.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onSkip = {
                    prefs.edit().putBoolean("questionnaire_skipped", true).apply()
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Questionnaire.route) {
            QuestionnaireScreen(
                onFinished = { targets ->
                    // Save to SharedPreferences (backward compat)
                    NutritionTargetsPrefs.save(prefs, targets)
                    // DataStore save happens inside ViewModel.saveTargetsToDataStore()

                    navController.navigate(Screen.QuestionnaireResults.route) {
                        popUpTo(Screen.Questionnaire.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.QuestionnaireResults.route) {
            val targets = NutritionTargetsPrefs.load(prefs) ?: NutritionTargets(0, 0, 0, 0)
            QuestionnaireResultsScreen(
                targets = targets,
                onContinue = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.QuestionnaireResults.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddMealClick = { query -> 
                    navController.navigate(Screen.AddMeal.passQuery(query)) 
                },
                onAnalyticsClick = { navController.navigate(Screen.Analytics.route) },
                onCaloriesBurnedClick = { navController.navigate(Screen.CaloriesBurned.route) },
                onFeedClick = { navController.navigate(Screen.Feed.route) },
                onRetakeQuestionnaire = { navController.navigate(Screen.Questionnaire.route) },
                onFoodDiaryClick = { navController.navigate(Screen.FoodDiary.route) }
            )
        }
        
        composable(
            route = Screen.AddMeal.route,
            arguments = listOf(
                androidx.navigation.navArgument("query") { 
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query")
            AddMealScreen(
                initialSearchQuery = query,
                onMealLogged = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                onCalorieTargetClick = { navController.navigate(Screen.CalorieTarget.route) },
                onBack = { navController.popBackStack() },
                onExportClick = { navController.navigate(Screen.WeeklyReport.route) }
            )
        }

        composable(Screen.WeeklyReport.route) {
            ExportReportScreen(
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

        composable(Screen.FoodDiary.route) {
            FoodDiaryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Feed.route) {
            FeedScreen(
                onNavigateToProfile = {
                    userID ->
                    navController.navigate(Screen.UserProfile.passUserID(userID))
                },
                onNavigateToCreatePost = {
                    navController.navigate(Screen.CreatePost.route)
                },
                onNavigateToSignIn = {
                    navController.navigate(Screen.SignIn.route)
                },
                onSignOut = {
                    navController.popBackStack(Screen.Dashboard.route, false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.UserProfile.route) { backStackEntry ->
            val userID = backStackEntry.arguments?.getString("userID") ?: ""
            UserProfileScreen(
                userID = userID,
                onBack = { navController.popBackStack() },
                onNavigateToFeed = { navController.popBackStack(Screen.Feed.route, false) },
                onSignOut = { navController.popBackStack(Screen.Feed.route, false) }
            )
        }

        composable(Screen.CreatePost.route) {
            CreatePostScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignInSuccess = {
                    navController.popBackStack()
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Feed.route) {
                            inclusive = true
                        }
                    }
                },
                onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.popBackStack()
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Feed.route) {
                            inclusive = true
                        }
                    }
                },
                onNavigateToSignIn = {
                    navController.popBackStack()
                    navController.navigate(Screen.SignIn.route)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
