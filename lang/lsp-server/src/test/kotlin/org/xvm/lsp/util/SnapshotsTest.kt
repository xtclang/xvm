package org.xvm.lsp.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SnapshotsTest {
    /** A collection whose one element was removed after its size was read: what another thread's remove leaves. */
    private class Emptied : AbstractCollection<String>() {
        override val size = 1

        override fun iterator(): Iterator<String> = emptyList<String>().iterator()
    }

    @Test
    fun `a copy of a collection whose one element goes meanwhile holds nothing`() {
        assertThat(Emptied().snapshot()).isEmpty()
    }

    @Test
    fun `a copy holds what the collection holds`() {
        assertThat(listOf("a", "b").snapshot()).containsExactly("a", "b")
        assertThat(setOf("only").snapshot()).containsExactly("only")
    }
}
