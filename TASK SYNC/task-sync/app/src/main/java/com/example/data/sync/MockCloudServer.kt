package com.example.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudTask(
    val id: String,
    val title: String,
    val description: String,
    val priority: String,
    val category: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val projectId: String = "personal",
    val assignedTo: String = "",
    val dueDate: Long? = null
)

data class CloudProject(
    val id: String,
    val name: String,
    val description: String,
    val isShared: Boolean = false,
    val members: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class InAppNotification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

object MockCloudServer {
    // In-memory "Cloud" database populated with projects
    private val _projects = MutableStateFlow<Map<String, CloudProject>>(
        mapOf(
            "project-1" to CloudProject(
                id = "project-1",
                name = "Mobile App Launch",
                description = "Preparing for the release of our modern Kotlin application.",
                isShared = true,
                members = "John Doe, Jane Smith, Alex Rivera",
                createdAt = System.currentTimeMillis() - 86400000
            ),
            "project-2" to CloudProject(
                id = "project-2",
                name = "Marketing Campaign",
                description = "Planning social media coverage and launch event.",
                isShared = true,
                members = "John Doe, Sarah Connor, Bob Vance",
                createdAt = System.currentTimeMillis() - 43200000
            )
        )
    )
    val projects: StateFlow<Map<String, CloudProject>> = _projects.asStateFlow()

    // In-memory "Cloud" database populated with starter tasks
    private val _tasks = MutableStateFlow<Map<String, CloudTask>>(
        mapOf(
            "cloud-task-1" to CloudTask(
                id = "cloud-task-1",
                title = "Launch Android Development Studio",
                description = "Complete initial checkout and configuration of the workspace.",
                priority = "High",
                category = "Work",
                isCompleted = true,
                createdAt = System.currentTimeMillis() - 3600000 * 2,
                updatedAt = System.currentTimeMillis() - 3600000 * 2,
                projectId = "project-1",
                assignedTo = "Alex Rivera"
            ),
            "cloud-task-2" to CloudTask(
                id = "cloud-task-2",
                title = "Design Core Offline Engine",
                description = "Define Room structures and design synchronization algorithms.",
                priority = "High",
                category = "Work",
                isCompleted = false,
                createdAt = System.currentTimeMillis() - 3600000 * 1,
                updatedAt = System.currentTimeMillis() - 3600000 * 1,
                projectId = "project-1",
                assignedTo = "John Doe"
            ),
            "cloud-task-3" to CloudTask(
                id = "cloud-task-3",
                title = "Exercise & Stretching Routine",
                description = "Take a 15-minute screen break to jog and stretch.",
                priority = "Medium",
                category = "Health",
                isCompleted = false,
                createdAt = System.currentTimeMillis() - 1800000,
                updatedAt = System.currentTimeMillis() - 1800000,
                projectId = "personal",
                assignedTo = ""
            )
        )
    )
    val tasks: StateFlow<Map<String, CloudTask>> = _tasks.asStateFlow()

    // Live In-App Notifications
    private val _notifications = MutableStateFlow<List<InAppNotification>>(
        listOf(
            InAppNotification(
                id = "notif-init",
                title = "Welcome to Collaborate!",
                message = "You can now collaborate in projects and assign tasks to team members.",
                timestamp = System.currentTimeMillis()
            )
        )
    )
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(
        listOf("Cloud: Server initialized with 2 shared projects and 3 default cloud tasks.")
    )
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        _syncLogs.value = listOf("[$timestamp] $message") + _syncLogs.value.take(49)
    }

    fun clearLogs() {
        _syncLogs.value = listOf("[System] Logs cleared.")
    }

    fun markNotificationsAsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    fun addNotification(title: String, message: String) {
        val newNotif = InAppNotification(
            id = "notif-local-${System.currentTimeMillis()}-${(1000..9999).random()}",
            title = title,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        _notifications.value = listOf(newNotif) + _notifications.value.take(19)
    }

    // HTTP Simulation Endpoints for Projects
    fun getProjects(): List<CloudProject> {
        log("GET /api/v1/projects - Fetching all shared projects from cloud")
        return _projects.value.values.toList()
    }

    fun upsertProject(project: CloudProject) {
        val isUpdate = _projects.value.containsKey(project.id)
        if (isUpdate) {
            log("PUT /api/v1/projects/${project.id} - Updating project '${project.name}'")
        } else {
            log("POST /api/v1/projects - Creating new shared project '${project.name}'")
        }
        _projects.value = _projects.value + (project.id to project)
    }

    fun deleteProject(id: String) {
        val proj = _projects.value[id]
        val name = proj?.name ?: id
        log("DELETE /api/v1/projects/$id - Removing project '$name'")
        _projects.value = _projects.value - id
        // Also remove tasks belonging to that project on server
        _tasks.value = _tasks.value.filter { it.value.projectId != id }
    }

    // HTTP Simulation Endpoints for Tasks
    fun getTasks(): List<CloudTask> {
        log("GET /api/v1/tasks - Fetching all tasks from cloud")
        return _tasks.value.values.toList()
    }

    fun upsertTask(task: CloudTask) {
        val isUpdate = _tasks.value.containsKey(task.id)
        if (isUpdate) {
            log("PUT /api/v1/tasks/${task.id} - Updating task '${task.title}'")
        } else {
            log("POST /api/v1/tasks - Inserting new task '${task.title}'")
        }
        _tasks.value = _tasks.value + (task.id to task)
    }

    fun deleteTask(id: String) {
        val task = _tasks.value[id]
        val title = task?.title ?: id
        log("DELETE /api/v1/tasks/$id - Removing task '$title'")
        _tasks.value = _tasks.value - id
    }

    private var simulationIndex = 0

    // Simulate database changes on the server side (to test background pulling)
    fun simulateServerSideChange() {
        val membersList = listOf("Jane Smith", "Alex Rivera", "Sarah Connor", "Bob Vance")
        val projectIds = listOf("project-1", "project-2")
        val projectNames = mapOf("project-1" to "Mobile App Launch", "project-2" to "Marketing Campaign")

        val randomMember = membersList.random()
        val randomProject = projectIds.random()
        val randomProjectName = projectNames[randomProject] ?: "Shared Space"

        val eventType = simulationIndex % 4
        simulationIndex++

        val notificationId = "notif-${System.currentTimeMillis()}"
        var notifTitle = ""
        var notifMsg = ""

        when (eventType) {
            0 -> {
                val taskId = "server-task-${System.currentTimeMillis() % 1000}"
                val titles = listOf("Refine UI typography", "Review feedback on Play Store", "Prepare pitch deck slides", "Translate app to Spanish")
                val selectedTitle = titles.random()
                val newTask = CloudTask(
                    id = taskId,
                    title = selectedTitle,
                    description = "Added by $randomMember. Let's get this completed as a team.",
                    priority = listOf("High", "Medium", "Low").random(),
                    category = "Work",
                    isCompleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    projectId = randomProject,
                    assignedTo = "John Doe"
                )
                _tasks.value = _tasks.value + (taskId to newTask)

                notifTitle = "New Task Assigned"
                notifMsg = "$randomMember assigned you '$selectedTitle' in '$randomProjectName'."
                log("CLOUD EVENT: $randomMember created and assigned task '$selectedTitle' to John Doe in '$randomProjectName'")
            }
            1 -> {
                val workTasks = _tasks.value.values.filter { it.projectId == randomProject && !it.isCompleted }
                if (workTasks.isNotEmpty()) {
                    val target = workTasks.random()
                    val updated = target.copy(isCompleted = true, updatedAt = System.currentTimeMillis(), assignedTo = randomMember)
                    _tasks.value = _tasks.value + (target.id to updated)

                    notifTitle = "Task Completed"
                    notifMsg = "$randomMember completed '${target.title}' in '$randomProjectName'."
                    log("CLOUD EVENT: $randomMember marked task '${target.title}' as completed in '$randomProjectName'")
                } else {
                    val taskId = "server-task-${System.currentTimeMillis() % 1000}"
                    val newTask = CloudTask(
                        id = taskId,
                        title = "Verify Release Builds",
                        description = "Automated verify task.",
                        priority = "Medium",
                        category = "Work",
                        isCompleted = true,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        projectId = randomProject,
                        assignedTo = randomMember
                    )
                    _tasks.value = _tasks.value + (taskId to newTask)
                    notifTitle = "Task Completed"
                    notifMsg = "$randomMember completed 'Verify Release Builds' in '$randomProjectName'."
                    log("CLOUD EVENT: $randomMember completed task 'Verify Release Builds' in '$randomProjectName'")
                }
            }
            2 -> {
                val projectTasks = _tasks.value.values.filter { it.projectId == randomProject }
                if (projectTasks.isNotEmpty()) {
                    val target = projectTasks.random()
                    val assignee = membersList.random()
                    val updated = target.copy(assignedTo = assignee, updatedAt = System.currentTimeMillis())
                    _tasks.value = _tasks.value + (target.id to updated)

                    notifTitle = "Task Reassigned"
                    notifMsg = "$randomMember reassigned '${target.title}' to $assignee."
                    log("CLOUD EVENT: $randomMember reassigned task '${target.title}' to $assignee in '$randomProjectName'")
                } else {
                    notifTitle = "Project Update"
                    notifMsg = "$randomMember modified project settings for '$randomProjectName'."
                    log("CLOUD EVENT: $randomMember modified project settings for '$randomProjectName'")
                }
            }
            3 -> {
                notifTitle = "New Comment"
                val comments = listOf(
                    "Keep up the great work, team!",
                    "Let's finalize the proposal today if possible.",
                    "Is anybody working on the layout issues?",
                    "Looks good to me, ready for testing."
                )
                notifMsg = "$randomMember commented: \"${comments.random()}\""
                log("CLOUD EVENT: $randomMember commented on '$randomProjectName'")
            }
        }

        if (notifTitle.isNotEmpty()) {
            val newNotif = InAppNotification(
                id = notificationId,
                title = notifTitle,
                message = notifMsg,
                timestamp = System.currentTimeMillis()
            )
            _notifications.value = listOf(newNotif) + _notifications.value.take(19)
        }
    }
}
