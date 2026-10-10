package com.mundus.agenda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset of the agenda-core `Todo` shape the Phase 0 screen renders.
 * Field names match the Rust serde names; unknown fields are ignored on
 * decode so the full object survives untouched inside the Engine.
 */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String? = null,
    val priority: Int = 0,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    val deadline: String? = null,
    val status: String = "inbox",
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("is_cancelled") val isCancelled: Boolean = false,
    @SerialName("is_trashed") val isTrashed: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
)
