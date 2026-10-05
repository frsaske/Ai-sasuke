package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_file_index",
    indices = [
        Index(value = ["name"]),
        Index(value = ["normalizedName"]),
        Index(value = ["extension"])
    ]
)
data class FileIndexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val extension: String,
    val parentFolder: String,
    val uriString: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val displayPath: String
)
