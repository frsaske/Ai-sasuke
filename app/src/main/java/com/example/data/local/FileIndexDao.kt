package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.FileIndexEntity

@Dao
interface FileIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FileIndexEntity>)

    @Query("SELECT * FROM local_file_index ORDER BY lastModified DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 1000): List<FileIndexEntity>

    @Query("SELECT * FROM local_file_index WHERE normalizedName = :name LIMIT 20")
    suspend fun findByNormalizedName(name: String): List<FileIndexEntity>

    @Query("SELECT * FROM local_file_index WHERE extension = :extension LIMIT 50")
    suspend fun findByExtension(extension: String): List<FileIndexEntity>

    @Query("DELETE FROM local_file_index")
    suspend fun clearAll()

    @Query("DELETE FROM local_file_index WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("SELECT COUNT(*) FROM local_file_index")
    suspend fun getCount(): Int
}
