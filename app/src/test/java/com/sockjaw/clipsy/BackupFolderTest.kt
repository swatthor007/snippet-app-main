package com.sockjaw.clipsy

import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.remapFolderIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupFolderTest {
    @Test fun reusesExistingFolderByNameIgnoringCase() = runBlocking {
        val created = mutableListOf<String>()
        val map = remapFolderIds(listOf(Folder(5, "Work")), mapOf("work" to 42L)) { created += it.name; 99L }
        assertEquals(42L, map[5L])
        assertEquals(emptyList<String>(), created)
    }

    @Test fun createsMissingFolderOncePerName() = runBlocking {
        val created = mutableListOf<String>()
        val map = remapFolderIds(listOf(Folder(1, "Home"), Folder(2, "home")), emptyMap()) { created += it.name; 7L }
        assertEquals(7L, map[1L])
        assertEquals(7L, map[2L])
        assertEquals(1, created.size)
    }

    @Test fun createdFolderKeepsItsStyle() = runBlocking {
        var made: Folder? = null
        remapFolderIds(listOf(Folder(1, "A", color = 3, icon = "star")), emptyMap()) { made = it; 3L }
        assertEquals(3, made?.color)
        assertEquals("star", made?.icon)
    }

    @Test fun unknownFolderIdMapsToNothing() = runBlocking {
        val map = remapFolderIds(listOf(Folder(1, "A")), emptyMap()) { 3L }
        assertNull(map[9L])
    }
}
