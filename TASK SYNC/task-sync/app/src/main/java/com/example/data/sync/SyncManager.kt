package com.example.data.sync

import com.example.data.local.ProjectDao
import com.example.data.local.TaskDao
import com.example.data.model.ProjectEntity
import com.example.data.model.TaskEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SyncManager {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()

    fun toggleOnline() {
        val nextState = !_isOnline.value
        _isOnline.value = nextState
        MockCloudServer.log("SYSTEM: Network toggled to " + if (nextState) "ONLINE" else "OFFLINE")
    }

    suspend fun sync(taskDao: TaskDao, projectDao: ProjectDao): Boolean {
        if (!_isOnline.value) {
            MockCloudServer.log("SYNC ABORTED: Device is currently offline.")
            return false
        }

        if (_isSyncing.value) return false
        _isSyncing.value = true
        MockCloudServer.log("SYNC START: Synchronizing projects and tasks with cloud...")

        try {
            // 1. Simulate network latency for visual fidelity
            delay(1200)

            // ------------------ PROJECTS SYNC ------------------
            // A. Fetch pending project changes
            val pendingProjectChanges = projectDao.getPendingSyncProjects()
            
            // B. Push local project insertions/updates to cloud
            pendingProjectChanges.forEach { proj ->
                if (proj.isDeletedLocally) {
                    MockCloudServer.deleteProject(proj.id)
                    projectDao.hardDeleteProject(proj.id)
                    MockCloudServer.log("SYNC PUSH: Deleted project '${proj.name}' on cloud.")
                } else {
                    val cloudProj = CloudProject(
                        id = proj.id,
                        name = proj.name,
                        description = proj.description,
                        isShared = proj.isShared,
                        members = proj.members,
                        createdAt = proj.createdAt
                    )
                    MockCloudServer.upsertProject(cloudProj)
                    projectDao.updateProject(proj.copy(syncStatus = "SYNCED"))
                    MockCloudServer.log("SYNC PUSH: Pushed project '${proj.name}' to cloud.")
                }
            }

            // C. Pull cloud projects
            val cloudProjects = MockCloudServer.getProjects()
            val localProjects = projectDao.getAllProjectsDirect()
            val localProjMap = localProjects.associateBy { it.id }
            val cloudProjMap = cloudProjects.associateBy { it.id }

            // D. Integrate cloud projects into local database
            cloudProjects.forEach { cp ->
                val lp = localProjMap[cp.id]
                if (lp == null) {
                    val newLocalProj = ProjectEntity(
                        id = cp.id,
                        name = cp.name,
                        description = cp.description,
                        isShared = cp.isShared,
                        members = cp.members,
                        createdAt = cp.createdAt,
                        syncStatus = "SYNCED"
                    )
                    projectDao.insertProject(newLocalProj)
                    MockCloudServer.log("SYNC PULL: Synced new shared project '${cp.name}' from cloud.")
                } else if (lp.syncStatus == "SYNCED") {
                    if (cp.name != lp.name || cp.description != lp.description || cp.isShared != lp.isShared || cp.members != lp.members) {
                        projectDao.insertProject(lp.copy(
                            name = cp.name,
                            description = cp.description,
                            isShared = cp.isShared,
                            members = cp.members,
                            syncStatus = "SYNCED"
                        ))
                        MockCloudServer.log("SYNC PULL: Updated project '${cp.name}' with cloud changes.")
                    }
                }
            }

            // E. Handle remote project deletions
            localProjects.forEach { lp ->
                if (lp.syncStatus == "SYNCED" && !cloudProjMap.containsKey(lp.id) && !lp.isDeletedLocally) {
                    projectDao.hardDeleteProject(lp.id)
                    MockCloudServer.log("SYNC CLEAN: Removed project '${lp.name}' locally.")
                }
            }


            // ------------------ TASKS SYNC ------------------
            // A. Fetch pending task changes
            val softDeletedTasks = taskDao.getSoftDeletedTasks()
            val pendingChanges = taskDao.getPendingSyncTasks().filter { !it.isDeletedLocally }

            // B. Push local deletions to cloud
            softDeletedTasks.forEach { task ->
                MockCloudServer.deleteTask(task.id)
                taskDao.hardDeleteTask(task.id)
                MockCloudServer.log("SYNC PUSH: Deleted task '${task.title}' on cloud.")
            }

            // C. Push local insertions and updates to cloud
            pendingChanges.forEach { task ->
                val cloudTask = CloudTask(
                    id = task.id,
                    title = task.title,
                    description = task.description,
                    priority = task.priority,
                    category = task.category,
                    isCompleted = task.isCompleted,
                    createdAt = task.createdAt,
                    updatedAt = task.updatedAt,
                    projectId = task.projectId,
                    assignedTo = task.assignedTo,
                    dueDate = task.dueDate
                )
                MockCloudServer.upsertTask(cloudTask)
                
                // Update local status to synced
                taskDao.updateTask(task.copy(syncStatus = "SYNCED"))
                MockCloudServer.log("SYNC PUSH: Pushed task '${task.title}' to cloud.")
            }

            // D. Pull cloud tasks
            val cloudTasks = MockCloudServer.getTasks()
            val localTasks = taskDao.getAllTasksDirect()
            val localMap = localTasks.associateBy { it.id }
            val cloudMap = cloudTasks.associateBy { it.id }

            // E. Integrate cloud changes into local database
            cloudTasks.forEach { cloudTask ->
                val localTask = localMap[cloudTask.id]
                if (localTask == null) {
                    val newLocal = TaskEntity(
                        id = cloudTask.id,
                        title = cloudTask.title,
                        description = cloudTask.description,
                        priority = cloudTask.priority,
                        category = cloudTask.category,
                        isCompleted = cloudTask.isCompleted,
                        createdAt = cloudTask.createdAt,
                        updatedAt = cloudTask.updatedAt,
                        syncStatus = "SYNCED",
                        isDeletedLocally = false,
                        projectId = cloudTask.projectId,
                        assignedTo = cloudTask.assignedTo,
                        dueDate = cloudTask.dueDate
                    )
                    taskDao.insertTask(newLocal)
                    MockCloudServer.log("SYNC PULL: Synced new task '${cloudTask.title}' from cloud to local.")
                } else {
                    if (localTask.syncStatus == "SYNCED") {
                        if (cloudTask.updatedAt > localTask.updatedAt || 
                            cloudTask.isCompleted != localTask.isCompleted || 
                            cloudTask.title != localTask.title || 
                            cloudTask.description != localTask.description || 
                            cloudTask.priority != localTask.priority || 
                            cloudTask.category != localTask.category ||
                            cloudTask.projectId != localTask.projectId ||
                            cloudTask.assignedTo != localTask.assignedTo ||
                            cloudTask.dueDate != localTask.dueDate) {
                            
                            val updatedLocal = localTask.copy(
                                title = cloudTask.title,
                                description = cloudTask.description,
                                priority = cloudTask.priority,
                                category = cloudTask.category,
                                isCompleted = cloudTask.isCompleted,
                                createdAt = cloudTask.createdAt,
                                updatedAt = cloudTask.updatedAt,
                                syncStatus = "SYNCED",
                                projectId = cloudTask.projectId,
                                assignedTo = cloudTask.assignedTo,
                                dueDate = cloudTask.dueDate
                            )
                            taskDao.updateTask(updatedLocal)
                            MockCloudServer.log("SYNC PULL: Updated task '${cloudTask.title}' with cloud changes.")
                        }
                    } else {
                        // CONFLICT RESOLUTION: Last-Write-Wins based on timestamps
                        if (cloudTask.updatedAt > localTask.updatedAt) {
                            val overriddenLocal = localTask.copy(
                                title = cloudTask.title,
                                description = cloudTask.description,
                                priority = cloudTask.priority,
                                category = cloudTask.category,
                                isCompleted = cloudTask.isCompleted,
                                createdAt = cloudTask.createdAt,
                                updatedAt = cloudTask.updatedAt,
                                syncStatus = "SYNCED",
                                projectId = cloudTask.projectId,
                                assignedTo = cloudTask.assignedTo,
                                dueDate = cloudTask.dueDate
                            )
                            taskDao.updateTask(overriddenLocal)
                            MockCloudServer.log("CONFLICT RESOLVED (Server Wins): Task '${cloudTask.title}' was updated on server and overrode local modifications.")
                        } else {
                            val updatedCloud = CloudTask(
                                id = localTask.id,
                                title = localTask.title,
                                description = localTask.description,
                                priority = localTask.priority,
                                category = localTask.category,
                                isCompleted = localTask.isCompleted,
                                createdAt = localTask.createdAt,
                                updatedAt = localTask.updatedAt,
                                projectId = localTask.projectId,
                                assignedTo = localTask.assignedTo,
                                dueDate = localTask.dueDate
                            )
                            MockCloudServer.upsertTask(updatedCloud)
                            taskDao.updateTask(localTask.copy(syncStatus = "SYNCED"))
                            MockCloudServer.log("CONFLICT RESOLVED (Local Wins): Pushed newer local version of '${localTask.title}' to cloud.")
                        }
                    }
                }
            }

            // F. Handle remote deletions
            localTasks.forEach { localTask ->
                if (localTask.syncStatus == "SYNCED" && !cloudMap.containsKey(localTask.id) && !localTask.isDeletedLocally) {
                    taskDao.hardDeleteTask(localTask.id)
                    MockCloudServer.log("SYNC CLEAN: Removed task '${localTask.title}' locally because it was deleted on cloud.")
                }
            }

            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _lastSyncTime.value = timestamp
            MockCloudServer.log("SYNC SUCCESS: Projects and tasks database is fully synchronized.")
            return true
        } catch (e: Exception) {
            MockCloudServer.log("SYNC ERROR: Failed to synchronize. ${e.message}")
            return false
        } finally {
            _isSyncing.value = false
        }
    }
}
