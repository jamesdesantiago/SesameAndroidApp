package com.gazzel.sesameapp

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme

@Composable
fun NewHomeScreen() {
    val context = LocalContext.current

    SesameAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Title
                Text(
                    text = "Sesame",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Row of Buttons (New List, Friends, Lists)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // New List Button
                    Button(
                        onClick = {
                            // Navigate to AddListActivity for creating a new list
                            context.startActivity(
                                Intent(context, AddListActivity::class.java).apply {
                                    putExtra("listId", -1) // Indicate a new list
                                    putExtra("listName", "New List") // Placeholder name
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New List",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "New List",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Friends Button
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, FriendsActivity::class.java))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Friends",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Friends",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Lists Button (Existing)
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, ListActivity::class.java))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Lists",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lists",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar (Placeholder)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Find a place",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Map Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Takes remaining space
                        .background(
                            color = Color.LightGray,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Text(
                        text = "Map Placeholder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}