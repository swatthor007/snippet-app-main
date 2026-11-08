package com.sockjaw.clipsy.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** What a link points at. */
enum class LinkKind {
    /** A web page: title, description and a social image come from its tags. */
    PAGE,

    /** The link is itself an image (or GIF): it can be kept as a link or saved as media. */
    IMAGE,

    /** Some other file (PDF, ZIP…): there's no page to read. */
    FILE,
}

enum class PreviewStatus { OK, FAILED }

/**
 * What Clipsy learned about a link the first time it looked, kept so the info page opens
 * instantly and works offline afterwards. A cache: it can be deleted at any time and rebuilt.
 */
@Entity(tableName = "link_previews")
data class LinkPreview(
    /** The link with a scheme, as saved: the key. */
    @PrimaryKey val url: String,
    val kind: LinkKind = LinkKind.PAGE,
    val title: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    /** The social/share image's address on the web. */
    val imageUrl: String? = null,
    /** The same image saved on this phone, when it could be downloaded. */
    val imagePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val status: PreviewStatus = PreviewStatus.OK,
    val fetchedAt: Long = System.currentTimeMillis(),
    /** The user chose "keep as link" for an image link, so don't ask again. */
    val keepAsLink: Boolean = false,
)

@Dao
interface LinkPreviewDao {
    @Query("SELECT * FROM link_previews WHERE url = :url")
    fun observe(url: String): Flow<LinkPreview?>

    @Query("SELECT * FROM link_previews WHERE imagePath IS NOT NULL")
    fun observeWithImages(): Flow<List<LinkPreview>>

    @Query("SELECT * FROM link_previews WHERE url = :url")
    suspend fun get(url: String): LinkPreview?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preview: LinkPreview)

    @Query("UPDATE link_previews SET keepAsLink = :keep WHERE url = :url")
    suspend fun setKeepAsLink(url: String, keep: Boolean)

    @Query("DELETE FROM link_previews WHERE url = :url")
    suspend fun delete(url: String)
}
