package com.gazzel.sesameapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gazzel.sesameapp.presentation.screens.auth.LoginScreen
import com.gazzel.sesameapp.presentation.screens.auth.UsernameSetupScreen
import com.gazzel.sesameapp.presentation.screens.friends.FriendsScreen
import com.gazzel.sesameapp.presentation.screens.home.HomeScreen
import com.gazzel.sesameapp.presentation.screens.lists.ListsScreen
import com.gazzel.sesameapp.presentation.screens.profile.ProfileScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object UsernameSetup : Screen("username_setup")
    object Home : Screen("home")
    object Lists : Screen("lists")
    object Friends : Screen("friends")
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object Notifications : Screen("notifications")
    object PrivacySettings : Screen("privacy_settings")
    object Help : Screen("help")
    // Keep CreateList if it becomes a Composable screen later
    // object CreateList : Screen("create_list")

    // --- DEFINE ListDetail HERE ---
    object ListDetail : Screen("list_details/{listId}") { // Define argument in route
        // Argument name for consistency
        const val ARG_LIST_ID = "listId"
        // Route definition using the constant
        val routeWithArg = "list_details/{$ARG_LIST_ID}"
        // Helper function to create the route with a specific ID
        fun createRoute(listId: String) = "list_details/$listId"
    }
    // --- END ListDetail Definition ---
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.UsernameSetup.route) { UsernameSetupScreen(navController) }
        composable(Screen.Home.route) { HomeScreen(navController) } // Assuming HomeScreen is a Composable
        composable(Screen.Lists.route) { ListsScreen(navController) }
        composable(Screen.Friends.route) { FriendsScreen(navController) } // Assuming FriendsScreen is a Composable
        composable(Screen.Profile.route) { ProfileScreen(navController) }

        // Add composables for other screens if they are Composables
        // composable(Screen.EditProfile.route) { /* EditProfileScreen(navController) */ }
        // composable(Screen.Notifications.route) { /* NotificationsScreen(navController) */ }
        // composable(Screen.PrivacySettings.route) { /* PrivacySettingsScreen(navController) */ }
        // composable(Screen.Help.route) { /* HelpScreen(navController) */ }
        // composable(Screen.CreateList.route) { /* CreateListScreen(navController) */ }

        // --- ADD ListDetail Composable Route HERE ---
        composable(
            // Use the route definition with the argument placeholder
            route = Screen.ListDetail.routeWithArg,
            // Define the argument expected in the route
            arguments = listOf(navArgument(Screen.ListDetail.ARG_LIST_ID) {
                type = NavType.StringType // Specify the argument type
                // nullable = false // Default is false, can be set explicitly
                // defaultValue = "" // Can set a default if needed
            })
        ) { backStackEntry ->
            // Retrieve the argument value from the backStackEntry
            val listId = backStackEntry.arguments?.getString(Screen.ListDetail.ARG_LIST_ID) ?: ""
            // Check if listId is valid before navigating or showing the screen
            if (listId.isNotEmpty()) {
                // Call your ListDetailScreen composable, passing the required arguments
                // Replace ListDetailScreen with your actual composable name if different
                // ListDetailScreen(navController = navController, listId = listId)
                // Placeholder Text until ListDetailScreen composable exists:
                androidx.compose.material3.Text("Showing List Detail for ID: $listId")
            } else {
                // Handle error: listId was missing or invalid
                androidx.compose.material3.Text("Error: Missing List ID")
                // Optionally navigate back or show an error message
            }
        }
        // --- END ListDetail Composable Route ---
    } // End NavHost
}