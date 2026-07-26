package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.api.TaskSuggestion
import com.example.data.api.GeminiClient
import com.example.data.model.ProjectEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSuggestionsDialog(
    viewModel: TaskViewModel,
    projects: List<ProjectEntity>,
    categoryFilter: String,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.aiSuggestionsState.collectAsState()
    
    // Track which suggestions have been added to prevent duplicate additions
    val addedSuggestions = remember { mutableStateListOf<String>() }

    // Auto-generate suggestions on opening if idle
    LaunchedEffect(Unit) {
        if (uiState is AiSuggestionsUiState.Idle) {
            viewModel.generateAiSuggestions(categoryFilter)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("ai_suggestions_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Sparkles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "AI Task Suggestions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Powered by Gemini 3.5 Flash",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("ai_suggest_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close suggestions")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = uiState) {
                        is AiSuggestionsUiState.Idle,
                        is AiSuggestionsUiState.Loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = "Analyzing your active tasks and planning next steps...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is AiSuggestionsUiState.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error icon",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                
                                val isApiKeyError = state.message.contains("API Key", ignoreCase = true)
                                
                                Text(
                                    text = if (isApiKeyError) "Missing Gemini API Key ⚠️" else "Unable to fetch suggestions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                
                                Text(
                                    text = if (isApiKeyError) {
                                        "Please configure your Gemini API Key in the Secrets panel in the Google AI Studio interface to enable AI-powered features."
                                    } else {
                                        state.message
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Button(
                                    onClick = { viewModel.generateAiSuggestions(categoryFilter) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry")
                                }
                            }
                        }

                        is AiSuggestionsUiState.Success -> {
                            if (state.suggestions.isEmpty()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No suggestions received.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        Text(
                                            text = if (categoryFilter != "All") {
                                                "✨ Personalized suggestions tailored for '$categoryFilter':"
                                            } else {
                                                "✨ Smart recommendations based on your current task load:"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }

                                    items(state.suggestions) { suggestion ->
                                        val isAdded = addedSuggestions.contains(suggestion.title)
                                        
                                        SuggestedTaskCard(
                                            suggestion = suggestion,
                                            isAdded = isAdded,
                                            onAddClick = {
                                                viewModel.addTask(
                                                    title = suggestion.title,
                                                    description = suggestion.description,
                                                    priority = suggestion.priority,
                                                    category = suggestion.category,
                                                    projectId = "personal" // Default to personal space
                                                )
                                                addedSuggestions.add(suggestion.title)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState is AiSuggestionsUiState.Success) {
                        Button(
                            onClick = {
                                addedSuggestions.clear()
                                viewModel.generateAiSuggestions(categoryFilter)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("regenerate_suggestions_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Regenerate")
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestedTaskCard(
    suggestion: TaskSuggestion,
    isAdded: Boolean,
    onAddClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suggested_task_card_${suggestion.title.replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category & Priority Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category badge
                    val catColor = when (suggestion.category) {
                        "Work" -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
                        "Personal" -> Color(0xFFF3E5F5) to Color(0xFF4A148C)
                        "Health" -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
                        "Shopping" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                        else -> Color(0xFFECEFF1) to Color(0xFF263238)
                    }
                    
                    Surface(
                        color = catColor.first,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = suggestion.category,
                            color = catColor.second,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Priority Badge
                    val prioColor = when (suggestion.priority) {
                        "High" -> Color(0xFFFFE9E9) to Color(0xFFC62828)
                        "Medium" -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
                        else -> Color(0xFFF1F8E9) to Color(0xFF33691E)
                    }

                    Surface(
                        color = prioColor.first,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = suggestion.priority,
                            color = prioColor.second,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Add button / added status indicator
                IconButton(
                    onClick = { if (!isAdded) onAddClick() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isAdded) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isAdded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("add_suggested_task_${suggestion.title.replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (isAdded) "Task Added" else "Add suggested task",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Title & Description
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
