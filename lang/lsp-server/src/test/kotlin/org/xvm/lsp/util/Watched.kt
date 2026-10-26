package org.xvm.lsp.util

import java.lang.ref.PhantomReference
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Optional
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Objects a test watches, to ask later whether anything still reaches them through strong references. It holds
 * each one weakly, only to know it again. Whether one is reached is found by walking the object graph from roots,
 * never by waiting for the collector, so the answer is the same whether or not a collection has run: an object
 * the collector has taken was reached from nothing.
 *
 * The walk follows instance fields, array elements, the contents of the JDK's collections, maps, atomic and
 * optional holders, executors' queues and completed futures, whose own fields are closed to reflection, a soft
 * reference's referent, and the static fields of each class a reached object belongs to. It does not follow a weak
 * or phantom reference's referent or a WeakHashMap's keys, which do not keep an object, nor threads, their stacks
 * and thread locals, what an unfinished future will run, or the statics of a class none of whose instances it
 * reaches, such as a Kotlin file's top-level values.
 */
class Watched {
    private val handles: MutableList<WeakReference<Any>> = Collections.synchronizedList(mutableListOf())

    /** How many objects are watched, also those that the collector has taken since. */
    val size: Int get() = handles.size

    /** Watches the object, unless it is null. */
    fun add(value: Any?) {
        if (value != null) handles += WeakReference(value)
    }

    fun clear() = handles.clear()

    /**
     * @return the path from one of the roots to each watched object they reach, which is none once nothing they
     *         hold keeps one
     */
    fun reachedFrom(vararg roots: Any?): List<String> {
        val targets = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        synchronized(handles) { handles.mapNotNullTo(targets) { it.get() } }
        return if (targets.isEmpty()) emptyList() else Walk(targets).from(roots.filterNotNull())
    }

    /** One walk of the graph, which records what each object was first reached from, for the path to it. */
    private class Walk(
        private val targets: Set<Any>,
    ) {
        private val parents = IdentityHashMap<Any, Any>()
        private val pending = ArrayDeque<Any>()
        private val walkedStatics = HashSet<Class<*>>()

        fun from(roots: List<Any>): List<String> {
            val reached = mutableListOf<Any>()
            roots.forEach { reach(it, ROOT) }
            while (pending.isNotEmpty()) {
                val value = pending.removeLast()
                if (value in targets) reached += value
                statics(value.javaClass)
                children(value)
            }
            println(
                parents.keys
                    .groupingBy { it.javaClass.name }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(12),
            )
            return reached.map(::path)
        }

        private fun reach(
            value: Any?,
            from: Any,
        ) {
            if (value == null || leaf(value) || parents.containsKey(value)) return
            parents[value] = from
            pending += value
        }

        private fun children(value: Any) {
            val type = value.javaClass
            if (type.isArray) {
                (value as Array<*>).forEach { reach(it, value) }
                return
            }
            if (value is Reference<*> && value !is WeakReference<*> && value !is PhantomReference<*>) reach(value.get(), value)
            fieldsOf(type).forEach { field -> reach(field.get(value), value) }
            contents(value)
        }

        /** What the JDK's holders hold, which their fields, closed to reflection, would not show. */
        private fun contents(value: Any) =
            retried {
                when (value) {
                    is WeakHashMap<*, *> -> {
                        value.values.forEach { reach(it, value) }
                    }

                    is Map<*, *> -> {
                        value.forEach { (key, item) ->
                            reach(key, value)
                            reach(item, value)
                        }
                    }

                    is Collection<*> -> {
                        value.forEach { reach(it, value) }
                    }

                    is Map.Entry<*, *> -> {
                        reach(value.key, value)
                        reach(value.value, value)
                    }

                    is AtomicReference<*> -> {
                        reach(value.get(), value)
                    }

                    is AtomicReferenceArray<*> -> {
                        (0 until value.length()).forEach { reach(value.get(it), value) }
                    }

                    is Optional<*> -> {
                        reach(value.orElse(null), value)
                    }

                    is CompletableFuture<*> -> {
                        if (value.isDone && !value.isCompletedExceptionally) reach(value.getNow(null), value)
                    }

                    is ThreadPoolExecutor -> {
                        value.queue.forEach { reach(it, value) }
                    }

                    is Throwable -> {
                        reach(value.cause, value)
                        value.suppressed.forEach { reach(it, value) }
                    }

                    else -> {
                        Unit
                    }
                }
            }

        private fun statics(type: Class<*>) {
            hierarchy(type).filter(walkedStatics::add).forEach { owner ->
                staticsOf(owner).forEach { field -> reach(field.get(null), owner) }
            }
        }

        /** The path to a reached object, from the root or the class whose static holds the first step. */
        private fun path(target: Any): String {
            val steps = generateSequence(target) { parents[it]?.takeUnless { parent -> parent === ROOT } }.toList().asReversed()
            return buildString {
                append(steps.first().let { if (it is Class<*>) "static ${nameOf(it)}" else nameOf(it.javaClass) })
                steps.zipWithNext { holder, held -> append(member(holder, held)).append(": ").append(nameOf(held.javaClass)) }
            }
        }

        private fun member(
            holder: Any,
            held: Any,
        ): String =
            when {
                holder is Class<*> -> "." + staticsOf(holder).firstOrNull { it.get(null) === held }?.name
                holder.javaClass.isArray -> "[]"
                else -> fieldsOf(holder.javaClass).firstOrNull { it.get(holder) === held }?.let { ".${it.name}" } ?: " contains"
            }

        private companion object {
            val ROOT = Any()

            val FIELDS = ConcurrentHashMap<Class<*>, List<Field>>()

            /** Objects that hold nothing worth following. */
            fun leaf(value: Any) =
                value is Class<*> ||
                    value is String ||
                    value is Boolean ||
                    value is Char ||
                    (value is Number && value.javaClass.module === Any::class.java.module) ||
                    value is Thread ||
                    value is ThreadGroup ||
                    value is ClassLoader ||
                    value is Module ||
                    (value.javaClass.isArray && value.javaClass.componentType.isPrimitive)

            /** The instance fields of a class and its superclasses that reflection may read. */
            fun fieldsOf(type: Class<*>): List<Field> =
                FIELDS.computeIfAbsent(type) {
                    readable(hierarchy(type).flatMap { owner -> owner.declaredFields.filterNot { Modifier.isStatic(it.modifiers) } })
                }

            fun staticsOf(owner: Class<*>): List<Field> = readable(owner.declaredFields.filter { Modifier.isStatic(it.modifiers) })

            /** The class and its superclasses, apart from those whose package is closed to this test, as the JDK's are. */
            fun hierarchy(type: Class<*>): List<Class<*>> =
                generateSequence(type) { it.superclass }
                    .filter { it.module.isOpen(it.packageName, Watched::class.java.module) }
                    .toList()

            fun readable(fields: List<Field>): List<Field> = fields.filter { !it.type.isPrimitive && it.trySetAccessible() }

            fun nameOf(type: Class<*>) = type.name.substringAfterLast('.')

            /** Runs an iteration again if another thread changed what it iterates meanwhile. */
            fun retried(iterate: () -> Unit) {
                repeat(9) {
                    try {
                        return iterate()
                    } catch (_: ConcurrentModificationException) {
                        // iterate again: what was reached stays reached
                    }
                }
                iterate()
            }
        }
    }
}
