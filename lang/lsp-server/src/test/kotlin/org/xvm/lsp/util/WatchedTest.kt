package org.xvm.lsp.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class WatchedTest {
    private class Watchable

    private class Holder(
        var held: Any? = null,
    )

    private class Registry {
        val entries = ConcurrentHashMap<String, Any>()
    }

    private class Cached {
        companion object {
            val cache = mutableListOf<Any>()
        }
    }

    @Test
    fun `an object held through fields, collections and holders is reached, with the path to it`() {
        val watchable = Watchable()
        val watched = Watched().apply { add(watchable) }
        val registry = Registry().apply { entries["key"] = arrayOf(Holder(AtomicReference(listOf(watchable)))) }

        assertThat(watched.reachedFrom(Holder(registry))).singleElement().satisfies({ path ->
            assertThat(path).startsWith("WatchedTest\$Holder.held: WatchedTest\$Registry.entries: ConcurrentHashMap contains")
            assertThat(path).endsWith(": WatchedTest\$Watchable")
        })
        registry.entries.clear()
        assertThat(watched.reachedFrom(Holder(registry))).isEmpty()
    }

    @Test
    fun `a weak reference or a WeakHashMap key does not keep an object, a soft reference does`() {
        val watchable = Watchable()
        val watched = Watched().apply { add(watchable) }

        assertThat(watched.reachedFrom(Holder(WeakReference(watchable)), WeakHashMap(mapOf(watchable to "value")))).isEmpty()
        assertThat(watched.reachedFrom(Holder(SoftReference(watchable)))).hasSize(1)
        assertThat(watched.reachedFrom(WeakHashMap(mapOf("key" to watchable)))).hasSize(1)
    }

    @Test
    fun `the statics of the class of a reached object are walked`() {
        val watchable = Watchable()
        val watched = Watched().apply { add(watchable) }
        Cached.cache += watchable
        try {
            assertThat(watched.reachedFrom(Holder())).isEmpty()
            assertThat(watched.reachedFrom(Holder(Cached()))).singleElement().satisfies({ path ->
                assertThat(path).isEqualTo("static WatchedTest\$Cached.cache: ArrayList contains: WatchedTest\$Watchable")
            })
        } finally {
            Cached.cache.clear()
        }
    }
}
