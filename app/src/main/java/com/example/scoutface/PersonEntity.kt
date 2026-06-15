package com.example.scoutface

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people_memory")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    // This stores the mathematical "signature" of the face
    val faceFeatures: String,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val isFamily: Boolean = false
)
