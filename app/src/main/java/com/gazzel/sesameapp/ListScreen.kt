package com.gazzel.sesameapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ListScreen(
    lists: List<ListResponse>,
    errorMessage: String?,
    isLoading: Boolean,
    onOpenList: (ListResponse) -> Unit,
    onAddListClick: () -> Unit,
    onSignOut: () -> Unit,
    onShareList: (ListResponse) -> Unit // Added this parameter
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddListClick,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add a new list",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Lists",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 24.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lists) { list ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onOpenList(list) }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = list.name ?: "Unnamed List",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = { onShareList(list) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Share,
                                                contentDescription = "Share List",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}