package com.example.flickfind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val movieId: Int,
    val username: String,
    val displayName: String,
    val rating: Int, // Số sao đánh giá từ 1 đến 5
    val comment: String,
    val timestamp: Long
)
