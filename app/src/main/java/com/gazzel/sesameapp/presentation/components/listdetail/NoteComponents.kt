package com.gazzel.sesameapp.presentation.components.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.* // Wildcard import
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.* // Wildcard import
import androidx.compose.runtime.* // Wildcard import
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.R // <<< Import R class

// --- Note Viewer Composable ---

@Composable
fun NoteViewer(
    note: String,
    onClose: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .wrapContentSize(Alignment.Center),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.note_viewer_title), // <<< Use stringResource
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.cd_edit_note) // <<< Use stringResource
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.cd_close_viewer) // <<< Use stringResource
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (note.isNotBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 300.dp)
                            .padding(vertical = 8.dp)
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.note_viewer_empty_placeholder), // <<< Use stringResource
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}


// --- Note Editor Composable ---

@Composable
fun NoteEditor(
    note: String,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .wrapContentSize(Alignment.Center),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.note_editor_title), // <<< Use stringResource
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .focusRequester(focusRequester),
                    // Pass Text composable to label
                    label = { Text(stringResource(id = R.string.label_enter_note)) }, // <<< Use stringResource
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(id = R.string.dialog_button_cancel)) // <<< Use stringResource
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onSave,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = stringResource(id = R.string.cd_save_note), // <<< Use stringResource
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(id = R.string.button_save_note)) // <<< Use stringResource
                    }
                }
            }
        }
    }
}