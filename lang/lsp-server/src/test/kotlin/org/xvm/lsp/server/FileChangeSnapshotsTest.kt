package org.xvm.lsp.server

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FileChangeSnapshotsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `operation and watcher duplicates coalesce but equal timestamp content changes survive`() {
        val file = directory.resolve("Member.x")
        Files.writeString(file, "first")
        val stamp = Files.getLastModifiedTime(file)
        val events = FileChangeSnapshots()
        val created = FileEvent(file.toUri().toString(), FileChangeType.Created)
        val changed = FileEvent(created.uri, FileChangeType.Changed)
        assertThat(events.changed(listOf(created, created))).containsExactly(created)
        assertThat(events.changed(listOf(changed))).isEmpty()
        Files.writeString(file, "other")
        Files.setLastModifiedTime(file, stamp)
        assertThat(events.changed(listOf(changed))).containsExactly(changed)
        Files.delete(file)
        val deleted = FileEvent(created.uri, FileChangeType.Deleted)
        assertThat(events.changed(listOf(deleted))).containsExactly(deleted)
        assertThat(events.changed(listOf(deleted))).isEmpty()
        Files.writeString(file, "other")
        assertThat(events.changed(listOf(created))).containsExactly(created)
    }

    @Test
    fun `folder notifications always reach discovery without scanning trees on the notification thread`() {
        val events = FileChangeSnapshots()
        val folder = FileEvent(directory.toUri().toString(), FileChangeType.Created)
        assertThat(events.changed(listOf(folder))).hasSize(1)
        assertThat(events.changed(listOf(folder))).hasSize(1)
        Files.writeString(directory.resolve("Child.x"), "class Child {}")
        assertThat(events.changed(listOf(folder))).hasSize(1)
    }
}
