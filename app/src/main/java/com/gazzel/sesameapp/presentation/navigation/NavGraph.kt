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
import com.gazzel.sesameapp.presentation.screens.listdetail.ListDetailScreen
import com.gazzel.sesameapp.presentation.screens.lists.CreateListScreen
import com.gazzel.sesameapp.presentation.screens.lists.ListsScreen
import com.gazzel.sesameapp.presentation.screens.profile.ProfileScreen
import com.gazzel.sesameapp.presentation.screens.search.SearchPlacesScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object UsernameSetup : Screen("username_setup")
    object Home : Screen("home")
    object Lists : Screen("lists")
    object CreateList : Screen("create_list")
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
    object SearchPlaces : Screen("search_places/{listId}") { // Add route object with arg
        const val ARG_LIST_ID = "listId"
        val routeWithArg = "search_places/{$ARG_LIST_ID}"
        fun createRoute(listId: String) = "search_places/$listId"
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
        composable(Screen.CreateList.route) { CreateListScreen(navController) }
        composable(Screen.Friends.route) { FriendsScreen(navController) } // Assuming FriendsScreen is a Composable
        composable(Screen.Profile.route) { ProfileScreen(navController) }
        composable(Screen.CreateList.route) { CreateListScreen(navController) }

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
            // ViewModel will get listId from SavedStateHandle now
            ListDetailScreen(navController = navController) // Call the new screen
        }
        composable(
            route = Screen.SearchPlaces.routeWithArg, // Use route with arg
            arguments = listOf(navArgument(Screen.SearchPlaces.ARG_LIST_ID) { // Define arg
                type = NavType.StringType
            })
        ) { // ViewModel gets listId from SavedStateHandle
            SearchPlacesScreen(navController = navController) // Call the new screen
        }
        // --- END ListDetail Composable Route ---
    } // End NavHost
}