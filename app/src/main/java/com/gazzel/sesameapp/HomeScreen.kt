package com.gazzel.sesameapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    lists: List<ListResponse>,
    errorMessage: String?,
    isLoading: Boolean,
    onDeleteList: (Int) -> Unit,
    onUpdateList: (Int, String) -> Unit,
    onTogglePrivacy: (Int, Boolean) -> Unit,
    onOpenList: (ListResponse) -> Unit,
    onAddListClick: () -> Unit,
    onSignOut: () -> Unit
) {
    var editingListId by remember { mutableStateOf<Int?>(null) }
    var editingListName by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddListClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add a new list")
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Lists",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Show the existing lists.
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(lists) { list ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Use safe calls for name and isPrivate
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenList(list) }
                                ) {
                                    Text(
                                        text = list.name ?: "Unnamed List",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = if (list.isPrivate ?: true) "Private" else "Public",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                IconButton(onClick = {
                                    editingListId = list.id
                                    editingListName = list.name ?: ""
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit List"
                                    )
                                }
                                // Lock toggle button uses safe default.
                                IconButton(onClick = {
                                    onTogglePrivacy(list.id, !(list.isPrivate ?: true))
                                }) {
                                    if (list.isPrivate ?: true) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = "Make Public"
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.LockOpen,
                                            contentDescription = "Make Private"
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteList(list.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete List"
                                    )
                                }
                            }

                            if (editingListId == list.id) {
                                OutlinedTextField(
                                    value = editingListName,
                                    onValueChange = { editingListName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Rename List") }
                                )
                                Row {
                                    Button(onClick = {
                                        onUpdateList(list.id, editingListName)
                                        editingListId = null
                                        editingListName = ""
                                    }) {
                                        Text("Save")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(onClick = {
                                        editingListId = null
                                        editingListName = ""
                                    }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out")
                }
            }
        }
    }
}
