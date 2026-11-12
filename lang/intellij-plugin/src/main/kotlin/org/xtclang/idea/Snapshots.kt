package org.xtclang.idea

/**
 * A copy of this collection as it is now, also when it is a view of a concurrent map or set that other threads change
 * meanwhile.
 *
 * Kotlin's toList() reads a collection of one element through its iterator after asking its size, so it throws
 * NoSuchElementException when that element is removed in between. This copy takes the collection's toArray(), which
 * a concurrent collection makes from whatever it holds at that moment.
 */
internal fun <T> Collection<T>.snapshot(): List<T> = ArrayList(this)
