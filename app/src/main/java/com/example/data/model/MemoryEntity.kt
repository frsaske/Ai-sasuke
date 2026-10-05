package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "user_memories")
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fact: String,
    val category: String = "Personal",
    val timestamp: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true
)
