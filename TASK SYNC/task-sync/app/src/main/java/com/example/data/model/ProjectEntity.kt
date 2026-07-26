package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val isShared: Boolean = false,
    val members: String = "", // Comma-separated member names
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING_INSERT",
    val isDeletedLocally: Boolean = false
)
