package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.waitForIndicators
import org.xtclang.idea.playbook.probe.HANG_LIMIT

/** Waits for the IDE's startup progress, indexing among it, to finish before the playbook's first case. */
internal fun Driver.awaitStartupIndicators() = waitForIndicators(HANG_LIMIT)
