package com.utility.toolbox.domain.model

/**
 * Domain model representing a workspace/clone space.
 */
data class Workspace(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean,
    val iconColor: Int,
    val storagePath: String,
    val profileId: Int,
    val appCount: Int = 0,
    val totalSize: Long = 0L
)
