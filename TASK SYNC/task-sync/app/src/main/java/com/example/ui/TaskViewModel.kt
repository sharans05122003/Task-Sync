package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ProjectEntity
import com.example.data.model.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.data.sync.InAppNotification
import com.example.data.sync.MockCloudServer
import com.example.data.sync.SyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.api.GeminiClient
import com.example.data.api.TaskSuggestion

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = TaskRepository(database.taskDao(), database.projectDao())

    // AI task suggestions state
    private val _aiSuggestionsState = MutableStateFlow<AiSuggestionsUiState>(AiSuggestionsUiState.Idle)
    val aiSuggestionsState: StateFlow<AiSuggestionsUiState> = _aiSuggestionsState.asStateFlow()

    fun generateAiSuggestions(categoryFilter: String) {
        viewModelScope.launch {
            _aiSuggestionsState.value = AiSuggestionsUiState.Loading
            try {
                val currentTasks = repository.activeTasksFlow.first()
                val suggestions = GeminiClient.getTaskSuggestions(currentTasks, categoryFilter)
                _aiSuggestionsState.value = AiSuggestionsUiState.Success(suggestions)
            } catch (e: Exception) {
                _aiSuggestionsState.value = AiSuggestionsUiState.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun clearAiSuggestions() {
        _aiSuggestionsState.value = AiSuggestionsUiState.Idle
    }

    // Observe synchronization status
    val isOnline: StateFlow<Boolean> = SyncManager.isOnline
    val isSyncing: StateFlow<Boolean> = SyncManager.isSyncing
    val lastSyncTime: StateFlow<String?> = SyncManager.lastSyncTime
    val syncLogs: StateFlow<List<String>> = MockCloudServer.syncLogs
    val notifications: StateFlow<List<InAppNotification>> = MockCloudServer.notifications

    // UI filters
    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    private val _selectedPriorityFilter = MutableStateFlow("All")
    val selectedPriorityFilter: StateFlow<String> = _selectedPriorityFilter.asStateFlow()

    private val _selectedStatusFilter = MutableStateFlow("All") // "All", "Pending", "Completed"
    val selectedStatusFilter: StateFlow<String> = _selectedStatusFilter.asStateFlow()

    private val _selectedProjectIdFilter = MutableStateFlow("all")
    val selectedProjectIdFilter: StateFlow<String> = _selectedProjectIdFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSortBy = MutableStateFlow("None") // "None", "PriorityHighToLow", "PriorityLowToHigh", "DateNewest", "DateOldest"
    val selectedSortBy: StateFlow<String> = _selectedSortBy.asStateFlow()

    // Observe projects
    val activeProjects: StateFlow<List<ProjectEntity>> = repository.activeProjectsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    data class FilterState(
        val category: String,
        val priority: String,
        val status: String,
        val projectId: String,
        val query: String,
        val sortBy: String
    )

    private val filtersFlow = combine(
        _selectedCategoryFilter,
        _selectedPriorityFilter,
        _selectedStatusFilter,
        _selectedProjectIdFilter,
        _searchQuery,
        _selectedSortBy
    ) { array ->
        FilterState(
            category = array[0],
            priority = array[1],
            status = array[2],
            projectId = array[3],
            query = array[4],
            sortBy = array[5]
        )
    }

    // Combined, reactive task list
    val filteredTasks: StateFlow<List<TaskEntity>> = combine(
        repository.activeTasksFlow,
        filtersFlow
    ) { tasks, filters ->
        val filtered = tasks.filter { task ->
            val matchCategory = filters.category == "All" || task.category.equals(filters.category, ignoreCase = true)
            val matchPriority = filters.priority == "All" || task.priority.equals(filters.priority, ignoreCase = true)
            val matchStatus = when (filters.status) {
                "Completed" -> task.isCompleted
                "Pending" -> !task.isCompleted
                else -> true
            }
            val matchProject = filters.projectId == "all" || task.projectId == filters.projectId
            val matchQuery = filters.query.isEmpty() || 
                    task.title.contains(filters.query, ignoreCase = true) || 
                    task.description.contains(filters.query, ignoreCase = true) ||
                    task.assignedTo.contains(filters.query, ignoreCase = true)

            matchCategory && matchPriority && matchStatus && matchProject && matchQuery
        }

        when (filters.sortBy) {
            "PriorityHighToLow" -> filtered.sortedByDescending { getPriorityWeight(it.priority) }
            "PriorityLowToHigh" -> filtered.sortedBy { getPriorityWeight(it.priority) }
            "DateNewest" -> filtered.sortedByDescending { it.createdAt }
            "DateOldest" -> filtered.sortedBy { it.createdAt }
            else -> filtered
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun getPriorityWeight(priority: String): Int {
        return when (priority.lowercase()) {
            "high" -> 3
            "medium" -> 2
            "low" -> 1
            else -> 0
        }
    }

    init {
        // Initial sync on launch
        viewModelScope.launch {
            repository.triggerSync()
        }

        // Setup background synchronizer observers
        viewModelScope.launch {
            // Auto-sync when reconnecting online
            launch {
                isOnline.collect { online ->
                    if (online) {
                        repository.triggerSync()
                    }
                }
            }

            // Periodic pull/push sync every 15 seconds while online to show real-time changes
            launch {
                while (true) {
                    delay(15000)
                    if (isOnline.value && !isSyncing.value) {
                        repository.triggerSync()
                    }
                }
            }
        }

        // Observe tasks flow to check due dates reactively
        viewModelScope.launch {
            repository.activeTasksFlow.collect { tasks ->
                checkDueDates(tasks)
            }
        }
    }

    private val triggeredDueNotifications = mutableSetOf<String>()

    private fun checkDueDates(tasks: List<TaskEntity>) {
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            if (!task.isCompleted && task.dueDate != null && task.dueDate > 0) {
                val timeLeft = task.dueDate - now
                val keyOverdue = "${task.id}_overdue"
                val keyApproaching = "${task.id}_approaching"

                if (timeLeft < 0) {
                    // Overdue!
                    if (!triggeredDueNotifications.contains(keyOverdue)) {
                        triggeredDueNotifications.add(keyOverdue)
                        MockCloudServer.addNotification(
                            title = "Task Overdue ⚠️",
                            message = "The task '${task.title}' was due on ${formatDate(task.dueDate)}."
                        )
                    }
                } else if (timeLeft <= 24 * 3600 * 1000) {
                    // Due soon (within 24 hours)
                    if (!triggeredDueNotifications.contains(keyApproaching)) {
                        triggeredDueNotifications.add(keyApproaching)
                        MockCloudServer.addNotification(
                            title = "Task Due Soon ⏰",
                            message = "The task '${task.title}' is due within 24 hours on ${formatDate(task.dueDate)}."
                        )
                    }
                }
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    // Filters setter
    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun setPriorityFilter(priority: String) {
        _selectedPriorityFilter.value = priority
    }

    fun setStatusFilter(status: String) {
        _selectedStatusFilter.value = status
    }

    fun setProjectIdFilter(projectId: String) {
        _selectedProjectIdFilter.value = projectId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(sortBy: String) {
        _selectedSortBy.value = sortBy
    }

    // Actions
    fun toggleOnline() {
        SyncManager.toggleOnline()
    }

    fun syncNow() {
        viewModelScope.launch {
            repository.triggerSync()
        }
    }

    fun addTask(
        title: String,
        description: String,
        priority: String,
        category: String,
        projectId: String = "personal",
        assignedTo: String = "",
        dueDate: Long? = null
    ) {
        viewModelScope.launch {
            repository.insertTask(title, description, priority, category, projectId, assignedTo, dueDate)
            if (isOnline.value) {
                repository.triggerSync()
            }
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
            if (isOnline.value) {
                repository.triggerSync()
            }
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(task)
            if (isOnline.value) {
                repository.triggerSync()
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
            if (isOnline.value) {
                repository.triggerSync()
            }
        }
    }

    // Project Actions
    fun addProject(name: String, description: String, isShared: Boolean, members: String) {
        viewModelScope.launch {
            repository.insertProject(name, description, isShared, members)
            if (isOnline.value) {
                repository.triggerSync()
            }
        }
    }

    fun updateProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.updateProject(project)
            if (isOnline.value) {
                repository.triggerSync()
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (isOnline.value) {
                repository.triggerSync()
            }
        }
    }

    fun simulateServerSideChange() {
        MockCloudServer.simulateServerSideChange()
    }

    fun clearLogs() {
        MockCloudServer.clearLogs()
    }

    fun markNotificationsAsRead() {
        MockCloudServer.markNotificationsAsRead()
    }

    fun clearNotifications() {
        MockCloudServer.clearNotifications()
    }
}

sealed interface AiSuggestionsUiState {
    object Idle : AiSuggestionsUiState
    object Loading : AiSuggestionsUiState
    data class Success(val suggestions: List<TaskSuggestion>) : AiSuggestionsUiState
    data class Error(val message: String) : AiSuggestionsUiState
}
