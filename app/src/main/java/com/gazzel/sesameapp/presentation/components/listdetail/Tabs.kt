package com.gazzel.sesameapp.presentation.components.listdetail

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun ListMapTabs(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    TabRow(
        selectedTabIndex = if (selectedTab == "List") 0 else 1,
        modifier = Modifier.fillMaxWidth()
    ) {
        Tab(
            selected = selectedTab == "List",
            onClick = { onTabSelected("List") },
            text = { Text("List") }
        )
        Tab(
            selected = selectedTab == "Map",
            onClick = { onTabSelected("Map") },
            text = { Text("Map") }
        )
    }
} 