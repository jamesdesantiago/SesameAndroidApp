package com.gazzel.sesameapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme

class FriendsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SesameAppTheme {
                FriendsScreen()
            }
        }
    }
}

@Composable
fun FriendsScreen() {
    // Placeholder data for following and followers
    val followingList = listOf(
        Friend("MH", "@mm.hidalgomas", true),
        Friend("LH", "@luisahaya10", true),
        Friend("RH", "@mr.hidalgomas", true),
        Friend("JM", "@jamesdesantiago", true),
        Friend("MT", "@marteresita", true),
        Friend("MJ", "@miniqui", true),
        Friend("YT", "@yolandatrivino", true),
        Friend("LM", "@luciapardo", true)
    )

    val followersList = listOf(
        Friend("AB", "@alicebrown", false),
        Friend("CD", "@charliedoe", false),
        Friend("EF", "@emilyfoster", false)
    )

    // State for the selected tab
    var selectedTab by remember { mutableStateOf("Following") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Title
            Text(
                text = "Sesame",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Title
            Text(
                text = "Friends",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Tabs (Following and Followers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Following Tab
                Button(
                    onClick = { selectedTab = "Following" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == "Following") Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedTab == "Following") Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "${followingList.size} Following",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Followers Tab
                Button(
                    onClick = { selectedTab = "Followers" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == "Followers") Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedTab == "Followers") Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "${followersList.size} Followers",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Search Bar (Placeholder)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // List of Following/Followers
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val listToShow = if (selectedTab == "Following") followingList else followersList
                items(listToShow) { friend ->
                    FriendItem(friend)
                }
            }
        }
    }
}

@Composable
fun FriendItem(friend: Friend) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Initials Circle
            Surface(
                modifier = Modifier
                    .size(40.dp),
                shape = CircleShape,
                color = when (friend.initials) {
                    "MH" -> Color(0xFFFFC1CC) // Pink
                    "LH" -> Color(0xFFADD8E6) // Light Blue
                    "RH" -> Color(0xFF87CEEB) // Sky Blue
                    "JM" -> Color(0xFF87CEFA) // Light Sky Blue
                    "MT" -> Color(0xFFD3D3D3) // Light Gray
                    "MJ" -> Color(0xFFFFD700) // Gold
                    "YT" -> Color(0xFFFFA500) // Orange
                    "LM" -> Color(0xFF90EE90) // Light Green
                    else -> Color.Gray
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.initials,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Username
            Text(
                text = friend.username,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Follow/Following Button (Placeholder)
        Button(
            onClick = { /* No action yet */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (friend.isFollowing) Color.Transparent else Color.Black,
                contentColor = if (friend.isFollowing) Color.Black else Color.White
            ),
            border = if (friend.isFollowing) ButtonDefaults.outlinedButtonBorder else null,
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = if (friend.isFollowing) "following" else "follow",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}