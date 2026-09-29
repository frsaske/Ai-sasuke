package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.MessageRole

class Converters {
    @TypeConverter
    fun fromRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toRole(value: String): MessageRole = try {
        MessageRole.valueOf(value)
    } catch (e: Exception) {
        MessageRole.USER
    }
}
