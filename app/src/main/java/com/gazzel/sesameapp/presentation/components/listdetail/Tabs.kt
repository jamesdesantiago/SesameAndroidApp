package com.gazzel.sesameapp.presentation.components.listdetail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // <<< Import
import com.gazzel.sesameapp.R // <<< Import

@Composable
fun ListMapTabs(
    selectedTab: String, // Keep using "List" and "Map" as internal identifiers
    onTabSelected: (String) -> Unit
) {
    // Define constants for the internal identifiers to avoid magic strings
    companion object {
        const val LIST_TAB_ID = "List"
        const val MAP_TAB_ID = "Map"
    }

    TabRow(
        // Calculate index based on the internal identifier
        selectedTabIndex = if (selectedTab == LIST_TAB_ID) 0 else 1,
        modifier = Modifier.fillMaxWidth()
        // Optional: Add containerColor, contentColor etc. if needed
    ) {
        Tab(
            selected = selectedTab == LIST_TAB_ID,
            onClick = { onTabSelected(LIST_TAB_ID) }, // Pass internal ID back
            text = { Text(stringResource(id = R.string.tab_label_list)) } // <<< Use stringResource
            // Optional: Add selectedContentColor, unselectedContentColor
        )
        Tab(
            selected = selectedTab == MAP_TAB_ID,
            onClick = { onTabSelected(MAP_TAB_ID) }, // Pass internal ID back
            text = { Text(stringResource(id = R.string.tab_label_map)) } // <<< Use stringResource
            // Optional: Add selectedContentColor, unselectedContentColor
        )
    }
}