package com.gazzel.sesameapp.presentation.components.listdetail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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