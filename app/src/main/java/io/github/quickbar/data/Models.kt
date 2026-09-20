package io.github.quickbar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String,
    val sortOrder: Int,
    val enabled: Boolean = true,
)

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "script_steps",
    foreignKeys = [
        ForeignKey(
            entity = ScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scriptId")],
)
data class ScriptStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptId: Long,
    val sortOrder: Int,
    val type: String,
    val snippetId: Long? = null,
    val textValue: String? = null,
    val numberValue: Long? = null,
)

object StepType {
    const val INSERT_SNIPPET = "insert_snippet"
    const val INSERT_TEXT = "insert_text"
    const val NEXT_FIELD = "next_field"
    const val IME_ENTER = "ime_enter"
    const val DELAY = "delay"
}

