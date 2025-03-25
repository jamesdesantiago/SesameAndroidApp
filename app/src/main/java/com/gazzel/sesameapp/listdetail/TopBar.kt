package com.gazzel.sesameapp.listdetail

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailTopBar(
    listName: String,
    onBackClick: () -> Unit,
    onAddCollaboratorClick: () -> Unit,
    onShareListClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = listName,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onAddCollaboratorClick) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add collaborator"
                )
            }
            IconButton(onClick = onShareListClick) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share list"
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options"
                )
            }
        }
    )
}