package org.xvm.lsp

/**
 * How long, in seconds, a test waits for what it expects before it fails as hung. It ends only a hang: an hour, which
 * no machine reaches however slow or loaded it is, so no result depends on speed. A check that something has not
 * happened yet keeps a short wait of its own, which can only miss a bug and never fails a correct run.
 */
const val HANG_SECONDS = 3_600L
