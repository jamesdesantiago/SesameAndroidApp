package com.gazzel.sesameapp.presentation.screens.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.* // Use wildcard
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Use wildcard
import androidx.compose.material3.* // Use wildcard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // Import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // Import R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    navController: NavController,
    viewModel: HelpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showContactSupportDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_screen_title)) }, // <<< Use String Res
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button)) // <<< Use String Res
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) { // Use variable
            is HelpUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is HelpUiState.Success -> {
                val faqs = state.faqs

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quick actions
                    item {
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.help_quick_actions_title), // <<< Use String Res
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = { showContactSupportDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null) // Icon is decorative
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.help_button_contact_support)) // <<< Use String Res
                                }
                                Button(
                                    onClick = { showFeedbackDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.MailOutline, contentDescription = null) // Icon is decorative
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.help_button_send_feedback)) // <<< Use String Res
                                }
                            }
                        }
                    }

                    // FAQs Title
                    item {
                        Text(
                            text = stringResource(R.string.help_faq_title), // <<< Use String Res
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp) // Add padding if needed
                        )
                    }

                    // FAQs List
                    items(faqs, key = { it.question }) { faq -> // Use question as key if unique
                        var expanded by remember { mutableStateOf(false) }
                        val arrowIconDesc = if (expanded) stringResource(R.string.cd_show_less) else stringResource(R.string.cd_show_more) // <<< Use String Res

                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded } // Make whole column clickable
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = faq.question,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp) // Allow text wrapping
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = arrowIconDesc
                                    )
                                }

                                // AnimatedVisibility can make this smoother
                                if (expanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = faq.answer,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is HelpUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { // Added Column for Button
                        Text(
                            text = state.message, // Keep specific error
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                        // Optionally add retry for FAQ loading
                        Button(onClick = { viewModel.loadFAQs() }) {
                            Text(stringResource(R.string.button_retry))
                        }
                    }
                }
            }
        }
    }

    // Contact support dialog
    if (showContactSupportDialog) {
        var subject by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showContactSupportDialog = false },
            title = { Text(stringResource(R.string.dialog_contact_support_title)) }, // <<< Use String Res
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(stringResource(R.string.dialog_contact_support_label_subject)) }, // <<< Use String Res
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(stringResource(R.string.dialog_contact_support_label_message)) }, // <<< Use String Res
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendSupportEmail(subject, message)
                        showContactSupportDialog = false
                    },
                    enabled = subject.isNotBlank() && message.isNotBlank() // Basic validation
                ) {
                    Text(stringResource(R.string.dialog_button_send)) // <<< Use String Res
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactSupportDialog = false }) {
                    Text(stringResource(R.string.dialog_button_cancel)) // <<< Use String Res
                }
            }
        )
    }

    // Feedback dialog
    if (showFeedbackDialog) {
        var feedback by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text(stringResource(R.string.dialog_send_feedback_title)) }, // <<< Use String Res
            text = {
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text(stringResource(R.string.dialog_send_feedback_label_feedback)) }, // <<< Use String Res
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendFeedback(feedback)
                        showFeedbackDialog = false
                    },
                    enabled = feedback.isNotBlank() // Basic validation
                ) {
                    Text(stringResource(R.string.dialog_button_send)) // <<< Use String Res
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text(stringResource(R.string.dialog_button_cancel)) // <<< Use String Res
                }
            }
        )
    }
}