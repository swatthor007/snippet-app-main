package com.sockjaw.clipsy.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * What a snippet holds. Media is images and GIFs; Document is any other file. The type is
 * chosen when the file is added (an image added as a Document stays a Document).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class SnippetType(val label: String) {
    TEXT("Text"),
    MEDIA("Media"),
    // Older backups called this FILE.
    @JsonNames("FILE") DOCUMENT("Document");

    val isFile: Boolean get() = this != TEXT

    companion object {
        /** Images (GIFs included) are media; everything else is a document. */
        fun forMime(mime: String?): SnippetType = if (mime?.startsWith("image/") == true) MEDIA else DOCUMENT
    }
}

@Serializable
enum class ExpandAfter(val label: String) {
    SPACE("Space"),
    ENTER("Enter"),
    INSTANT("Instantly"),
}

@Serializable
enum class FileMode(val title: String, val subtitle: String) {
    PASTE("Paste the file", "In chat, mail and notes apps"),
    LINK("Type a share link", "Works in any text field"),
    SHARE("Open the share sheet", "Pick where it goes each time"),
}

@Serializable
@Entity(tableName = "snippets", indices = [Index(value = ["keyword"], unique = true), Index(value = ["folderId"])])
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Null when the snippet has no keyword. Never an empty string (the unique index allows many nulls). */
    val keyword: String? = null,
    val type: SnippetType = SnippetType.TEXT,
    val content: String = "",
    val filePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val fileMode: FileMode = FileMode.PASTE,
    val expandAfter: ExpandAfter = ExpandAfter.SPACE,
    val keepSpace: Boolean = true,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
    val folderId: Long? = null,
    /** How many numbers {counter} has handed out; the next one is this plus one. */
    val counter: Int = 0,
    /** True for an encrypted text snippet: [content] is empty and the text lives in [sealedContent]. */
    val locked: Boolean = false,
    /** AES-GCM ciphertext of the text, sealed with the vault's data key. Null unless [locked]. */
    val sealedContent: String? = null,
    /** Let the keyword expand without unlocking. Needs [openSeal]. */
    val expandUnlocked: Boolean = false,
    /** The text sealed with a key that needs no sign-in, so the expansion service can type it. Only when [expandUnlocked]. */
    val openSeal: String? = null,
) {
    val lastActivity: Long get() = lastUsedAt ?: createdAt

    /** Locked and not yet opened: [content] is blank, so copying or typing it would produce nothing. */
    val needsUnlock: Boolean get() = locked && content.isEmpty()

    /** This snippet with its text filled in, for handing to copy, share and typing code after an unlock. */
    fun revealed(text: String): Snippet = copy(content = text)

    /** Derived, so editing a link into plain text never leaves a stale type behind. */
    val isLink: Boolean get() = type == SnippetType.TEXT && Links.parse(content) != null
}

@Entity(tableName = "usage_events")
data class UsageEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snippetId: Long,
    val timestamp: Long,
    val charsSaved: Int,
)
