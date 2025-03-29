package com.gazzel.sesameapp.presentation.screens.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.domain.model.FAQ

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
                title = { Text("Help & Support") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (uiState) {
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
                val faqs = (uiState as HelpUiState.Success).faqs
                
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
                                    text = "Quick Actions",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = { showContactSupportDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Contact Support")
                                }
                                Button(
                                    onClick = { showFeedbackDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Feedback, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send Feedback")
                                }
                            }
                        }
                    }

                    // FAQs
                    item {
                        Text(
                            text = "Frequently Asked Questions",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(faqs) { faq ->
                        var expanded by remember { mutableStateOf(false) }
                        
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = faq.question,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (expanded) "Show less" else "Show more"
                                    )
                                }

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
                    Text(
                        text = (uiState as HelpUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
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
            title = { Text("Contact Support") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message") },
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
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactSupportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Feedback dialog
    if (showFeedbackDialog) {
        var feedback by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Send Feedback") },
            text = {
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text("Your feedback") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendFeedback(feedback)
                        showFeedbackDialog = false
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
} 