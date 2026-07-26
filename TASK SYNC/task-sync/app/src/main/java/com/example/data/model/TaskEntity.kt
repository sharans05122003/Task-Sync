package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priority: String, // "High", "Medium", "Low"
    val category: String, // "Work", "Personal", "Health", "Shopping", "Other"
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING_INSERT", // "SYNCED", "PENDING_INSERT", "PENDING_UPDATE", "PENDING_DELETE"
    val isDeletedLocally: Boolean = false,
    val projectId: String = "personal",
    val assignedTo: String = "",
    val dueDate: Long? = null
)
