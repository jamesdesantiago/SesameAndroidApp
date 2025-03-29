package com.gazzel.sesameapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gazzel.sesameapp.presentation.screens.home.HomeScreen
import com.gazzel.sesameapp.presentation.screens.lists.ListsScreen
import com.gazzel.sesameapp.presentation.screens.friends.FriendsScreen
import com.gazzel.sesameapp.presentation.screens.profile.ProfileScreen
import com.gazzel.sesameapp.presentation.screens.auth.LoginScreen
import com.gazzel.sesameapp.presentation.screens.auth.UsernameSetupScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object UsernameSetup : Screen("username_setup")
    object Home : Screen("home")
    object Lists : Screen("lists")
    object Friends : Screen("friends")
    object Profile : Screen("profile")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(navController)
        }
        composable(Screen.UsernameSetup.route) {
            UsernameSetupScreen(navController)
        }
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Lists.route) {
            ListsScreen(navController)
        }
        composable(Screen.Friends.route) {
            FriendsScreen(navController)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(navController)
        }
    }
} 