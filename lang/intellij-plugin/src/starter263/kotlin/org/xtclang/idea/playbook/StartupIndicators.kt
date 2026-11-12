package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ManualWaitForIndicators
import com.intellij.driver.sdk.waitForIndicators
import org.xtclang.idea.playbook.probe.HANG_LIMIT

/**
 * Waits for the IDE's startup progress, indexing among it, to finish before the playbook's first case. Starter 263 asks
 * tests to pause the driver on indicators instead; the playbook keeps the one wait, in its first case, that it has on 262.
 */
@OptIn(ManualWaitForIndicators::class)
internal fun Driver.awaitStartupIndicators() = waitForIndicators(HANG_LIMIT)
