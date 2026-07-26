package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isDeletedLocally = 0 ORDER BY createdAt DESC")
    fun getActiveProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsDirect(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE syncStatus != 'SYNCED' OR isDeletedLocally = 1")
    suspend fun getPendingSyncProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<ProjectEntity>)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET isDeletedLocally = 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteProject(id: String)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun hardDeleteProject(id: String)
}
