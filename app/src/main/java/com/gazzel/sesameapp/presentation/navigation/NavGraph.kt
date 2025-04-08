// app/src/main/java/com/gazzel/sesameapp/presentation/navigation/NavGraph.kt
package com.gazzel.sesameapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
// Import ALL screen composables
import com.gazzel.sesameapp.presentation.screens.auth.LoginScreen
import com.gazzel.sesameapp.presentation.screens.auth.UsernameSetupScreen
import com.gazzel.sesameapp.presentation.screens.friends.FriendsScreen
import com.gazzel.sesameapp.presentation.screens.home.HomeScreen
import com.gazzel.sesameapp.presentation.screens.listdetail.ListDetailScreen
import com.gazzel.sesameapp.presentation.screens.lists.CreateListScreen // <<< ADD
import com.gazzel.sesameapp.presentation.screens.lists.ListsScreen // <<< ADD
import com.gazzel.sesameapp.presentation.screens.profile.ProfileScreen
import com.gazzel.sesameapp.presentation.screens.search.SearchPlacesScreen // <<< ADD
// Import other screens if they become composables
import com.gazzel.sesameapp.presentation.screens.profile.EditProfileScreen
import com.gazzel.sesameapp.presentation.screens.notifications.NotificationsScreen
import com.gazzel.sesameapp.presentation.screens.privacy.PrivacySettingsScreen
import com.gazzel.sesameapp.presentation.screens.help.HelpScreen


// --- Screen sealed class definition (ensure all routes are defined) ---
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object UsernameSetup : Screen("username_setup")
    object Home : Screen("home")
    object Lists : Screen("lists") // Route for User Lists screen
    object CreateList : Screen("create_list") // Route for Create List screen
    object Friends : Screen("friends")
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object Notifications : Screen("notifications")
    object PrivacySettings : Screen("privacy_settings")
    object Help : Screen("help")

    // List Detail with argument
    object ListDetail : Screen("list_details/{listId}") {
        const val ARG_LIST_ID = "listId"
        val routeWithArg = "list_details/{$ARG_LIST_ID}"
        fun createRoute(listId: String) = "list_details/$listId"
    }

    // Search Places with argument
    object SearchPlaces : Screen("search_places/{listId}") {
        const val ARG_LIST_ID = "listId"
        val routeWithArg = "search_places/{$ARG_LIST_ID}"
        fun createRoute(listId: String) = "search_places/$listId"
    }
}
// --- End Screen sealed class ---

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route // Or check auth status and start elsewhere
    ) {
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.UsernameSetup.route) { UsernameSetupScreen(navController) }
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Profile.route) { ProfileScreen(navController) }
        composable(Screen.EditProfile.route) { EditProfileScreen(navController) }
        composable(Screen.Notifications.route) { NotificationsScreen(navController) }
        composable(Screen.PrivacySettings.route) { PrivacySettingsScreen(navController) }
        composable(Screen.Help.route) { HelpScreen(navController) }
        composable(Screen.Friends.route) { FriendsScreen(navController) }

        // --- ADD Composable destinations for refactored screens ---

        // User Lists Screen
        composable(Screen.Lists.route) { ListsScreen(navController = navController) }

        // Create List Screen
        composable(Screen.CreateList.route) { CreateListScreen(navController = navController) }

        // List Detail Screen (with argument)
        composable(
            route = Screen.ListDetail.routeWithArg, // Uses routeWithArg
            arguments = listOf(navArgument(Screen.ListDetail.ARG_LIST_ID) {
                type = NavType.StringType
            })
        ) { // NavController passed implicitly if needed, ViewModel gets ID via SavedStateHandle
            ListDetailScreen(navController = navController)
        }


        // Search Places Screen (with argument)
        composable(
            route = Screen.SearchPlaces.routeWithArg,
            arguments = listOf(navArgument(Screen.SearchPlaces.ARG_LIST_ID) {
                type = NavType.StringType
            })
        ) { // ViewModel gets listId from SavedStateHandle automatically
            SearchPlacesScreen(navController = navController)
        }

        // --- End ADDED destinations ---

    } // End NavHost
}