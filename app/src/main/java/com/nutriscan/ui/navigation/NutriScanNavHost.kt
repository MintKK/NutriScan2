package com.nutriscan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nutriscan.ui.addmeal.AddMealScreen
import com.nutriscan.ui.analytics.AnalyticsScreen
import com.nutriscan.ui.dashboard.DashboardScreen

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object AddMeal : Screen("add_meal")
    object Analytics : Screen("analytics")
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
                onAnalyticsClick = { navController.navigate(Screen.Analytics.route) }
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
