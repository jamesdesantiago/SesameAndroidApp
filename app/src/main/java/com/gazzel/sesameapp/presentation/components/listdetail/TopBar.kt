package com.gazzel.sesameapp.presentation.components.listdetail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import com.gazzel.sesameapp.R // <<< Import your R class

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
            // List name is dynamic, so it remains a variable
            Text(
                text = listName,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    // Use stringResource for contentDescription
                    contentDescription = stringResource(R.string.cd_back_button)
                )
            }
        },
        actions = {
            IconButton(onClick = onAddCollaboratorClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    // Use stringResource for contentDescription
                    contentDescription = stringResource(R.string.cd_add_collaborator)
                )
            }
            IconButton(onClick = onShareListClick) {
                Icon(
                    imageVector = Icons.Default.Share,
                    // Use stringResource for contentDescription
                    contentDescription = stringResource(R.string.cd_share_list)
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    // Use stringResource for contentDescription
                    contentDescription = stringResource(R.string.cd_more_options)
                )
            }
        }
    )
}