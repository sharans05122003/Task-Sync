package com.example.data.repository

import com.example.data.local.ProjectDao
import com.example.data.local.TaskDao
import com.example.data.model.ProjectEntity
import com.example.data.model.TaskEntity
import com.example.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskRepository(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao
) {
    val activeTasksFlow: Flow<List<TaskEntity>> = taskDao.getActiveTasksFlow()
    val activeProjectsFlow: Flow<List<ProjectEntity>> = projectDao.getActiveProjectsFlow()

    suspend fun insertTask(
        title: String,
        description: String,
        priority: String,
        category: String,
        projectId: String = "personal",
        assignedTo: String = "",
        dueDate: Long? = null
    ): TaskEntity {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            id = id,
            title = title,
            description = description,
            priority = priority,
            category = category,
            isCompleted = false,
            createdAt = now,
            updatedAt = now,
            syncStatus = "PENDING_INSERT",
            projectId = projectId,
            assignedTo = assignedTo,
            dueDate = dueDate
        )
        taskDao.insertTask(task)
        return task
    }

    suspend fun updateTask(task: TaskEntity) {
        val current = taskDao.getTaskById(task.id)
        val now = System.currentTimeMillis()
        
        val nextSyncStatus = if (current?.syncStatus == "PENDING_INSERT") {
            "PENDING_INSERT"
        } else {
            "PENDING_UPDATE"
        }

        val updatedTask = task.copy(
            updatedAt = now,
            syncStatus = nextSyncStatus
        )
        taskDao.updateTask(updatedTask)
    }

    suspend fun toggleTaskCompletion(task: TaskEntity) {
        val nextCompletedState = !task.isCompleted
        val current = taskDao.getTaskById(task.id)
        val now = System.currentTimeMillis()
        
        val nextSyncStatus = if (current?.syncStatus == "PENDING_INSERT") {
            "PENDING_INSERT"
        } else {
            "PENDING_UPDATE"
        }

        val updatedTask = task.copy(
            isCompleted = nextCompletedState,
            updatedAt = now,
            syncStatus = nextSyncStatus
        )
        taskDao.updateTask(updatedTask)
    }

    suspend fun deleteTask(task: TaskEntity) {
        val current = taskDao.getTaskById(task.id)
        val now = System.currentTimeMillis()
        
        if (current?.syncStatus == "PENDING_INSERT") {
            taskDao.hardDeleteTask(task.id)
        } else {
            taskDao.softDeleteTask(task.id, now)
        }
    }

    // Projects methods
    suspend fun insertProject(
        name: String,
        description: String,
        isShared: Boolean,
        members: String
    ): ProjectEntity {
        val id = UUID.randomUUID().toString()
        val project = ProjectEntity(
            id = id,
            name = name,
            description = description,
            isShared = isShared,
            members = members,
            createdAt = System.currentTimeMillis(),
            syncStatus = "PENDING_INSERT"
        )
        projectDao.insertProject(project)
        return project
    }

    suspend fun updateProject(project: ProjectEntity) {
        val current = projectDao.getProjectById(project.id)
        val nextSyncStatus = if (current?.syncStatus == "PENDING_INSERT") {
            "PENDING_INSERT"
        } else {
            "PENDING_UPDATE"
        }
        projectDao.updateProject(project.copy(syncStatus = nextSyncStatus))
    }

    suspend fun deleteProject(project: ProjectEntity) {
        val current = projectDao.getProjectById(project.id)
        if (current?.syncStatus == "PENDING_INSERT") {
            projectDao.hardDeleteProject(project.id)
        } else {
            projectDao.softDeleteProject(project.id)
        }
    }

    suspend fun triggerSync(): Boolean {
        return SyncManager.sync(taskDao, projectDao)
    }
}
