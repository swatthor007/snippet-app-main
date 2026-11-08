package com.sockjaw.clipsy.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A single-level group of snippets. A snippet is in at most one folder. */
@Serializable
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Index into the UI colour palette. */
    val color: Int = 0,
    /** Key of one of the UI's folder icons. */
    val icon: String = "folder",
)
