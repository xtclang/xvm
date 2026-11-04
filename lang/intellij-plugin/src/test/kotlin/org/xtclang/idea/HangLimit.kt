package org.xtclang.idea

/**
 * How long, in seconds, a test waits for what it expects before it fails as hung. It ends only a hang: an hour, which
 * no machine reaches however slow or loaded it is, so no result depends on speed.
 */
const val HANG_SECONDS = 3_600L
