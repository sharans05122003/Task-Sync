package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProjectEntity
import com.example.data.model.TaskEntity
import com.example.data.sync.InAppNotification
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskApp(viewModel: TaskViewModel) {
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 840

    // Collect ViewState
    val tasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val projects by viewModel.activeProjects.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val logs by viewModel.syncLogs.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val selectedPriority by viewModel.selectedPriorityFilter.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatusFilter.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectIdFilter.collectAsStateWithLifecycle()
    val selectedSortBy by viewModel.selectedSortBy.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddProjectDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showAiSuggestionsDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var currentTab by remember { mutableIntStateOf(0) } // 0 = Projects, 1 = Tasks, 2 = Sync Console

    val unreadNotificationsCount = notifications.count { !it.isRead }

    // Live heads-up popup banner state
    var lastNotifId by remember { mutableStateOf("") }
    var activeToast by remember { mutableStateOf<InAppNotification?>(null) }

    LaunchedEffect(notifications) {
        val latest = notifications.firstOrNull()
        if (latest != null && latest.id != lastNotifId && latest.id != "notif-init") {
            lastNotifId = latest.id
            activeToast = latest
        }
    }

    // Auto-dismiss heads-up alert banner
    LaunchedEffect(activeToast) {
        if (activeToast != null) {
            delay(4000)
            activeToast = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Floating Heads-Up Live Alert Popup Banner
        AnimatedVisibility(
            visible = activeToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(99f)
                .align(Alignment.TopCenter)
        ) {
            activeToast?.let { notif ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showNotificationsDialog = true
                            activeToast = null
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Notification",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = notif.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = notif.message,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(
                            onClick = { activeToast = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Toast",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                MediumTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "App Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "CollabSync",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    },
                    actions = {
                        // Notifications Bell with Badge
                        IconButton(
                            onClick = {
                                viewModel.markNotificationsAsRead()
                                showNotificationsDialog = true
                            },
                            modifier = Modifier.testTag("notifications_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationsCount > 0) {
                                        Badge {
                                            Text(unreadNotificationsCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (unreadNotificationsCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = "Show collaborative notifications"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Connection Status Banner
                        ConnectionStatusBadge(
                            isOnline = isOnline,
                            onClick = { viewModel.toggleOnline() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                if (currentTab == 0 && !isWideScreen) {
                    FloatingActionButton(
                        onClick = { showAddProjectDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_project_fab")
                    ) {
                        Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "Create Project")
                    }
                } else if ((currentTab == 1 || isWideScreen) && currentTab != 2) {
                    FloatingActionButton(
                        onClick = { showAddTaskDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_task_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task")
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            if (isWideScreen) {
                // Wide Screen Layout (Projects / Tasks Panel split)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Left Panel: Projects & Navigation (35%)
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Projects",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddProjectDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Project")
                            }
                        }

                        ProjectListSection(
                            projects = projects,
                            tasks = tasks,
                            selectedProjectId = selectedProjectId,
                            onProjectSelect = { viewModel.setProjectIdFilter(it) },
                            onDeleteProject = { viewModel.deleteProject(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Middle Panel: Tasks Dashboard (40%)
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Tasks Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        TaskControlsSection(
                            searchQuery = searchQuery,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            selectedCategory = selectedCategory,
                            onCategorySelect = { viewModel.setCategoryFilter(it) },
                            selectedPriority = selectedPriority,
                            onPrioritySelect = { viewModel.setPriorityFilter(it) },
                            selectedStatus = selectedStatus,
                            onStatusSelect = { viewModel.setStatusFilter(it) },
                            selectedSortBy = selectedSortBy,
                            onSortBySelect = { viewModel.setSortBy(it) },
                            onAiSuggestClick = { showAiSuggestionsDialog = true }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TaskList(
                            tasks = tasks,
                            projects = projects,
                            onCompleteToggle = { viewModel.toggleTaskCompletion(it) },
                            onEditClick = { taskToEdit = it },
                            onDeleteClick = { viewModel.deleteTask(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Right Panel: Sync & Realtime Collaborative Simulator (25%)
                    Column(
                        modifier = Modifier
                            .weight(0.25f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        SyncConsolePanel(
                            isOnline = isOnline,
                            isSyncing = isSyncing,
                            lastSyncTime = lastSyncTime,
                            logs = logs,
                            onSyncNow = { viewModel.syncNow() },
                            onSimulateServerChange = { viewModel.simulateServerSideChange() },
                            onClearLogs = { viewModel.clearLogs() },
                            onToggleNetwork = { viewModel.toggleOnline() }
                        )
                    }
                }
            } else {
                // Compact Screen Layout (Mobile tabbed)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Projects Tab")
                                    Text("Projects (${projects.size + 1})")
                                }
                            },
                            modifier = Modifier.testTag("projects_tab")
                        )
                        Tab(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Tasks Tab")
                                    Text("Tasks (${tasks.size})")
                                }
                            },
                            modifier = Modifier.testTag("tasks_tab")
                        )
                        Tab(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Terminal, contentDescription = "Sync Tab")
                                    Text("Simulator")
                                }
                            },
                            modifier = Modifier.testTag("sync_tab")
                        )
                    }

                    when (currentTab) {
                        0 -> {
                            // Mobile Projects View
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                ProjectListSection(
                                    projects = projects,
                                    tasks = tasks,
                                    selectedProjectId = selectedProjectId,
                                    onProjectSelect = {
                                        viewModel.setProjectIdFilter(it)
                                        currentTab = 1 // Auto focus tasks
                                    },
                                    onDeleteProject = { viewModel.deleteProject(it) }
                                )
                            }
                        }
                        1 -> {
                            // Mobile Tasks View
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                // Dynamic active project header label
                                val focusedProjectName = when (selectedProjectId) {
                                    "all" -> "All Projects"
                                    "personal" -> "Personal Space"
                                    else -> projects.find { it.id == selectedProjectId }?.name ?: "Collaborative Project"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (selectedProjectId == "personal") Icons.Default.Person else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = focusedProjectName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (selectedProjectId != "all") {
                                        TextButton(onClick = { viewModel.setProjectIdFilter("all") }) {
                                            Text("Clear Project Filter", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TaskControlsSection(
                                    searchQuery = searchQuery,
                                    onSearchChange = { viewModel.setSearchQuery(it) },
                                    selectedCategory = selectedCategory,
                                    onCategorySelect = { viewModel.setCategoryFilter(it) },
                                    selectedPriority = selectedPriority,
                                    onPrioritySelect = { viewModel.setPriorityFilter(it) },
                                    selectedStatus = selectedStatus,
                                    onStatusSelect = { viewModel.setStatusFilter(it) },
                                    selectedSortBy = selectedSortBy,
                                    onSortBySelect = { viewModel.setSortBy(it) },
                                    onAiSuggestClick = { showAiSuggestionsDialog = true }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                TaskList(
                                    tasks = tasks,
                                    projects = projects,
                                    onCompleteToggle = { viewModel.toggleTaskCompletion(it) },
                                    onEditClick = { taskToEdit = it },
                                    onDeleteClick = { viewModel.deleteTask(it) }
                                )
                            }
                        }
                        2 -> {
                            // Mobile Sync & Collaborator Simulation Control View
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                SyncConsolePanel(
                                    isOnline = isOnline,
                                    isSyncing = isSyncing,
                                    lastSyncTime = lastSyncTime,
                                    logs = logs,
                                    onSyncNow = { viewModel.syncNow() },
                                    onSimulateServerChange = { viewModel.simulateServerSideChange() },
                                    onClearLogs = { viewModel.clearLogs() },
                                    onToggleNetwork = { viewModel.toggleOnline() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Project Dialog
        if (showAddProjectDialog) {
            ProjectFormDialog(
                onDismiss = { showAddProjectDialog = false },
                onSubmit = { name, desc, shared, members ->
                    viewModel.addProject(name, desc, shared, members)
                    showAddProjectDialog = false
                }
            )
        }

        // Add Task Dialog
        if (showAddTaskDialog) {
            TaskFormDialog(
                title = "New Task",
                projects = projects,
                onDismiss = { showAddTaskDialog = false },
                onSubmit = { t, d, p, c, projId, assignee, due ->
                    viewModel.addTask(t, d, p, c, projId, assignee, due)
                    showAddTaskDialog = false
                }
            )
        }

        // AI Task Suggestions Dialog
        if (showAiSuggestionsDialog) {
            AiSuggestionsDialog(
                viewModel = viewModel,
                projects = projects,
                categoryFilter = selectedCategory,
                onDismiss = {
                    showAiSuggestionsDialog = false
                    viewModel.clearAiSuggestions()
                }
            )
        }

        // Edit Task Dialog
        taskToEdit?.let { task ->
            TaskFormDialog(
                title = "Edit Task",
                initialTitle = task.title,
                initialDesc = task.description,
                initialPriority = task.priority,
                initialCategory = task.category,
                initialProjectId = task.projectId,
                initialAssignee = task.assignedTo,
                initialDueDate = task.dueDate,
                projects = projects,
                onDismiss = { taskToEdit = null },
                onSubmit = { t, d, p, c, projId, assignee, due ->
                    viewModel.updateTask(task.copy(
                        title = t,
                        description = d,
                        priority = p,
                        category = c,
                        projectId = projId,
                        assignedTo = assignee,
                        dueDate = due
                    ))
                    taskToEdit = null
                }
            )
        }

        // Notifications Dialog log
        if (showNotificationsDialog) {
            NotificationsLogDialog(
                notifications = notifications,
                onDismiss = { showNotificationsDialog = false },
                onClear = { viewModel.clearNotifications() }
            )
        }
    }
}

@Composable
fun ConnectionStatusBadge(isOnline: Boolean, onClick: () -> Unit) {
    val contentColor = if (isOnline) Color(0xFFB2F0AD) else Color(0xFFEF5350)
    val statusText = if (isOnline) "Synced" else "Offline"
    val icon = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = CircleShape,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("connection_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Icon(
                imageVector = icon,
                contentDescription = statusText,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ------------------ PROJECTS VIEW COMPONENTS ------------------
@Composable
fun ProjectListSection(
    projects: List<ProjectEntity>,
    tasks: List<TaskEntity>,
    selectedProjectId: String,
    onProjectSelect: (String) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Predefined Personal Space Project Row (Always available)
        item {
            val personalTasks = tasks.filter { it.projectId == "personal" }
            val completedCount = personalTasks.count { it.isCompleted }
            ProjectRowItem(
                id = "personal",
                name = "Personal Space",
                description = "Your private, offline-capable workspace.",
                isShared = false,
                members = "",
                completedTasks = completedCount,
                totalTasks = personalTasks.size,
                isSelected = selectedProjectId == "personal",
                onSelect = { onProjectSelect("personal") },
                onDelete = null
            )
        }

        // Header for shared projects
        if (projects.isNotEmpty()) {
            item {
                Text(
                    text = "Shared Workspaces",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(projects, key = { it.id }) { proj ->
                val projTasks = tasks.filter { it.projectId == proj.id }
                val completedCount = projTasks.count { it.isCompleted }
                ProjectRowItem(
                    id = proj.id,
                    name = proj.name,
                    description = proj.description,
                    isShared = proj.isShared,
                    members = proj.members,
                    completedTasks = completedCount,
                    totalTasks = projTasks.size,
                    isSelected = selectedProjectId == proj.id,
                    onSelect = { onProjectSelect(proj.id) },
                    onDelete = { onDeleteProject(proj) }
                )
            }
        }

        // Show a reset filter card if a specific project is selected
        if (selectedProjectId != "all") {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedCard(
                    onClick = { onProjectSelect("all") },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show All Project Tasks", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectRowItem(
    id: String,
    name: String,
    description: String,
    isShared: Boolean,
    members: String,
    completedTasks: Int,
    totalTasks: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val strokeColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, strokeColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("project_card_$id")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Project",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Collaborative tracking stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shared Status with green dot or members
                if (isShared) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB2F0AD))
                        )
                        Text(
                            text = if (members.isNotEmpty()) "Members: $members" else "Shared",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB2F0AD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Private", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                        Text(
                            text = "Private",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Progress Counter
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$completedTasks/$totalTasks Done",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ------------------ TASK LIST COMPONENTS ------------------
@Composable
fun TaskControlsSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    selectedPriority: String,
    onPrioritySelect: (String) -> Unit,
    selectedStatus: String,
    onStatusSelect: (String) -> Unit,
    selectedSortBy: String,
    onSortBySelect: (String) -> Unit,
    onAiSuggestClick: () -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search & Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search tasks, descriptions or members...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_input")
            )

            // Filter Trigger Button
            IconButton(
                onClick = { showFilterSheet = true },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .size(48.dp)
                    .testTag("filter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = if (selectedPriority != "All" || selectedStatus != "All" || selectedSortBy != "None") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // AI Task Suggestions Button
            IconButton(
                onClick = onAiSuggestClick,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .size(48.dp)
                    .testTag("ai_suggest_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Task Suggestions",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Horizontal Category Quick Scroll
        val categories = listOf("All", "Work", "Personal", "Health", "Shopping", "Other")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(cat) },
                    label = { Text(cat) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("category_chip_$cat")
                )
            }
        }
    }

    if (showFilterSheet) {
        Dialog(onDismissRequest = { showFilterSheet = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Filter & Sort Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Priority Filter Option
                    Text(
                        text = "Priority Level Filter",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All", "High", "Medium", "Low").forEach { level ->
                            val active = level == selectedPriority
                            Button(
                                onClick = { onPrioritySelect(level) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("priority_filter_$level"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(level, fontSize = 12.sp)
                            }
                        }
                    }

                    // Status Filter Option
                    Text(
                        text = "Task Status Filter",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("All", "Pending", "Completed").forEach { state ->
                            val active = state == selectedStatus
                            Button(
                                onClick = { onStatusSelect(state) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_filter_$state"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(state, fontSize = 12.sp)
                            }
                        }
                    }

                    // Sort Option
                    Text(
                        text = "Sort Tasks By",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "None" to "Default (None)",
                            "PriorityHighToLow" to "Priority: High to Low",
                            "PriorityLowToHigh" to "Priority: Low to High",
                            "DateNewest" to "Date: Newest First",
                            "DateOldest" to "Date: Oldest First"
                        ).forEach { (value, label) ->
                            val active = value == selectedSortBy
                            Surface(
                                onClick = { onSortBySelect(value) },
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("sort_by_$value")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (active) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showFilterSheet = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("apply_filters_button")
                    ) {
                        Text("Apply Filters & Sort")
                    }
                }
            }
        }
    }
}

@Composable
fun TaskList(
    tasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    onCompleteToggle: (TaskEntity) -> Unit,
    onEditClick: (TaskEntity) -> Unit,
    onDeleteClick: (TaskEntity) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "No Tasks Found",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Create a task or trigger collaborative cloud events in the Simulator to sync team entries.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tasks, key = { it.id }) { task ->
                val prjName = if (task.projectId == "personal") {
                    "Personal"
                } else {
                    projects.find { it.id == task.projectId }?.name ?: "Collaborative"
                }

                TaskRowItem(
                    task = task,
                    projectName = prjName,
                    onCompleteToggle = { onCompleteToggle(task) },
                    onEditClick = { onEditClick(task) },
                    onDeleteClick = { onDeleteClick(task) }
                )
            }
        }
    }
}

@Composable
fun TaskRowItem(
    task: TaskEntity,
    projectName: String,
    onCompleteToggle: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cardColor = if (task.isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val priorityColor = when (task.priority.lowercase()) {
        "high" -> Color(0xFFEF5350)
        "medium" -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }

    val categoryIcon = when (task.category.lowercase()) {
        "work" -> Icons.Default.Work
        "personal" -> Icons.Default.Person
        "health" -> Icons.Default.Favorite
        "shopping" -> Icons.Default.ShoppingCart
        else -> Icons.AutoMirrored.Filled.List
    }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (task.isCompleted) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .testTag("task_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority Line
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onCompleteToggle() },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("task_checkbox_${task.id}")
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditClick() }
            ) {
                // Category Tag & Project Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = task.category,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = task.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    // Project Label Tag
                    Surface(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = projectName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )

                if (task.description.isNotEmpty()) {
                    Text(
                        text = task.description,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentSize()
                ) {
                    // Assignee Badge Chip
                    val assignedLabel = if (task.assignedTo.isNotEmpty()) "Assigned: ${task.assignedTo}" else "Unassigned"
                    val assignedColor = if (task.assignedTo.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    Surface(
                        color = assignedColor.copy(alpha = 0.7f),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AssignmentInd,
                                contentDescription = null,
                                tint = if (task.assignedTo.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = assignedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (task.assignedTo.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Due Date Badge
                    if (task.dueDate != null && task.dueDate > 0L) {
                        val now = System.currentTimeMillis()
                        val timeLeft = task.dueDate - now
                        val isOverdue = timeLeft < 0 && !task.isCompleted
                        val isDueSoon = timeLeft in 0L..(24 * 3600 * 1000L) && !task.isCompleted

                        val badgeBg = when {
                            task.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            isOverdue -> Color(0xFFFEEBEE)
                            isDueSoon -> Color(0xFFFFF8E1)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        }

                        val badgeFg = when {
                            task.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isOverdue -> Color(0xFFC62828)
                            isDueSoon -> Color(0xFFF57F17)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        val icon = when {
                            task.isCompleted -> Icons.Default.CheckCircle
                            isOverdue -> Icons.Default.Warning
                            isDueSoon -> Icons.Default.Schedule
                            else -> Icons.Default.DateRange
                        }

                        val text = when {
                            task.isCompleted -> "Done: " + SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(task.dueDate))
                            isOverdue -> "Overdue: " + SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(task.dueDate))
                            isDueSoon -> "Due Soon: " + SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(task.dueDate))
                            else -> "Due: " + SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(task.dueDate))
                        }

                        Surface(
                            color = badgeBg,
                            shape = CircleShape,
                            modifier = Modifier.testTag("task_due_badge_${task.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = badgeFg,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg
                                )
                            }
                        }
                    }
                }
            }

            // Sync indicators
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                SyncStatusIcon(status = task.syncStatus)
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_task_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Task",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SyncStatusIcon(status: String) {
    val isSynced = status == "SYNCED"
    val tint = if (isSynced) Color(0xFFB2F0AD) else Color(0xFFFF9800)
    val description = if (isSynced) "Synced to Cloud" else "Saved Offline Only"
    val icon = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue

    Surface(
        color = tint.copy(alpha = 0.1f),
        shape = CircleShape,
        modifier = Modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ------------------ SYNC CONSOLE COMPONENTS ------------------
@Composable
fun SyncConsolePanel(
    isOnline: Boolean,
    isSyncing: Boolean,
    lastSyncTime: String?,
    logs: List<String>,
    onSyncNow: () -> Unit,
    onSimulateServerChange: () -> Unit,
    onClearLogs: () -> Unit,
    onToggleNetwork: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Realtime Collaborative Sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Sync Status Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFFB2F0AD) else Color(0xFFEF5350))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOnline) "Cloud Connection Synced" else "Offline Simulation Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (lastSyncTime != null) "Last synced: $lastSyncTime" else "No sync executed yet",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onSyncNow,
                        enabled = isOnline && !isSyncing,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(40.dp)
                            .testTag("manual_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Now",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (isSyncing) rotationAngle else 0f)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onToggleNetwork,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isOnline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_network_button")
                ) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isOnline) "Simulate Go Offline" else "Simulate Go Online")
                }

                Button(
                    onClick = onSimulateServerChange,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_server_change_button")
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inject Team Collaborator Event")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sync Console Logs",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Text("Clear Console", fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { logLine ->
                        val textColor = when {
                            logLine.contains("ERROR") -> Color(0xFFEF5350)
                            logLine.contains("SUCCESS") -> Color(0xFFB2F0AD)
                            logLine.contains("PUSH") || logLine.contains("PULL") -> Color(0xFF64B5F6)
                            logLine.contains("CONFLICT") -> Color(0xFFFFB74D)
                            logLine.contains("CLOUD EVENT") -> Color(0xFFCE93D8)
                            else -> Color(0xFFDCDCDC)
                        }
                        Text(
                            text = logLine,
                            color = textColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------ FORMS AND DIALOGS ------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFormDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean, String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var descText by remember { mutableStateOf("") }
    var isShared by remember { mutableStateOf(true) }

    val membersList = listOf("Jane Smith", "Alex Rivera", "Sarah Connor", "Bob Vance")
    var selectedMembers by remember { mutableStateOf(setOf("John Doe")) } // Current user John Doe is always a member

    var nameError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "New Shared Workspace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        if (it.isNotEmpty()) nameError = false
                    },
                    label = { Text("Workspace Name *") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("Workspace name is required") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Workspace Description") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Shared Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Enable Collaboration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Allows assigning tasks to team members.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                }

                if (isShared) {
                    Text(
                        text = "Add Team Members",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Multi select members
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        membersList.forEach { member ->
                            val included = selectedMembers.contains(member)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMembers = if (included) {
                                            selectedMembers - member
                                        } else {
                                            selectedMembers + member
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = included,
                                    onCheckedChange = {
                                        selectedMembers = if (included) {
                                            selectedMembers - member
                                        } else {
                                            selectedMembers + member
                                        }
                                    }
                                )
                                Text(member, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (nameText.trim().isEmpty()) {
                                nameError = true
                            } else {
                                val membersCSV = selectedMembers.joinToString(", ")
                                onSubmit(nameText.trim(), descText.trim(), isShared, membersCSV)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Create Workspace")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(
    title: String,
    initialTitle: String = "",
    initialDesc: String = "",
    initialPriority: String = "Medium",
    initialCategory: String = "Work",
    initialProjectId: String = "personal",
    initialAssignee: String = "",
    initialDueDate: Long? = null,
    projects: List<ProjectEntity>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, String, Long?) -> Unit
) {
    var titleText by remember { mutableStateOf(initialTitle) }
    var descText by remember { mutableStateOf(initialDesc) }
    var priority by remember { mutableStateOf(initialPriority) }
    var category by remember { mutableStateOf(initialCategory) }
    var projectId by remember { mutableStateOf(initialProjectId) }
    var assignee by remember { mutableStateOf(initialAssignee) }
    var dueDate by remember { mutableStateOf(initialDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = titleText,
                    onValueChange = {
                        titleText = it
                        if (it.isNotEmpty()) titleError = false
                    },
                    label = { Text("Task Title *") },
                    isError = titleError,
                    supportingText = { if (titleError) Text("Title is required") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_title_input")
                )

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_desc_input")
                )

                // Project Selector Row
                Text(
                    text = "Assign to Workspace",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = projectId == "personal",
                            onClick = {
                                projectId = "personal"
                                assignee = "" // private tasks cannot be assigned
                            },
                            label = { Text("Personal Space") },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                    items(projects) { proj ->
                        FilterChip(
                            selected = projectId == proj.id,
                            onClick = { projectId = proj.id },
                            label = { Text(proj.name) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // Team Member Assignee selection (Only available if selected project is not 'personal')
                if (projectId != "personal") {
                    val activeProject = projects.find { it.id == projectId }
                    val projectMembersList = activeProject?.members?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: listOf("John Doe", "Jane Smith", "Alex Rivera", "Sarah Connor", "Bob Vance")

                    Text(
                        text = "Assign to Collaborator",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = assignee.isEmpty(),
                                onClick = { assignee = "" },
                                label = { Text("Unassigned") },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                        items(projectMembersList) { member ->
                            FilterChip(
                                selected = assignee == member,
                                onClick = { assignee = member },
                                label = { Text(member) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }

                // Category Selection Scroll List
                Text(
                    text = "Category",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                val categories = listOf("Work", "Personal", "Health", "Shopping", "Other")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = cat == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("form_cat_$cat")
                        )
                    }
                }

                // Priority Selection
                Text(
                    text = "Priority",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("High", "Medium", "Low").forEach { level ->
                        val isSelected = level == priority
                        val levelColor = when (level) {
                            "High" -> Color(0xFFEF5350)
                            "Medium" -> Color(0xFFFFA726)
                            else -> Color(0xFF66BB6A)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) levelColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) levelColor else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { priority = level }
                                .testTag("form_priority_$level")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = level,
                                    color = if (isSelected) levelColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Due Date Selector
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Due Date",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (dueDate != null) {
                                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dueDate!!))
                            } else {
                                "No due date"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dueDate != null) {
                            IconButton(
                                onClick = { dueDate = null },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Due Date", modifier = Modifier.size(18.dp))
                            }
                        }
                        Button(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("form_due_date_button")
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select Date", fontSize = 12.sp)
                        }
                    }
                }

                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = dueDate ?: System.currentTimeMillis()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dueDate = datePickerState.selectedDateMillis
                                    showDatePicker = false
                                },
                                modifier = Modifier.testTag("datepicker_confirm")
                            ) {
                                Text("OK")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancel")
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("form_cancel_button")
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (titleText.trim().isEmpty()) {
                                titleError = true
                            } else {
                                onSubmit(titleText.trim(), descText.trim(), priority, category, projectId, assignee, dueDate)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("form_submit_button")
                    ) {
                        Text("Save Task")
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsLogDialog(
    notifications: List<InAppNotification>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .fillMaxHeight(0.7f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Team Collaborators Activity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = onClear) {
                            Text("Clear")
                        }
                    }
                }

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                            Text("No collaborative updates yet.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notifications) { notif ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Circle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(8.dp)
                                            .padding(top = 4.dp)
                                    )
                                    Column {
                                        Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(notif.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val timeStr = SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(notif.timestamp))
                                        Text(timeStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Panel")
                }
            }
        }
    }
}
