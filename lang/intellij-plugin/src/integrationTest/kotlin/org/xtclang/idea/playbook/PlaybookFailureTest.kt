package org.xtclang.idea.playbook

import com.intellij.driver.sdk.WaitForException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds

class PlaybookFailureTest {
    @Test fun `timeouts stop the run including wrapped driver failures`() {
        assertTrue(WaitForException(1.seconds, "Editor did not change").mustStopPlaybook())
        assertTrue(IllegalStateException("Remote call", TimeoutException()).mustStopPlaybook())
    }

    @Test fun `a suppressed cleanup failure prevents continuation`() {
        val failure = AssertionError("Wrong source")
        failure.addSuppressed(PlaybookCleanupFailure(IllegalStateException("Cannot reset settings")))
        assertTrue(failure.mustStopPlaybook())
    }

    @Test fun `ordinary completed assertions remain collectable`() {
        assertFalse(AssertionError("Wrong source").mustStopPlaybook())
    }

    @Test fun `an expected refusal cannot turn a timeout into a collectable assertion`() {
        val failure =
            assertThrows(AssertionError::class.java) {
                assertThrows(ClientRequestFailure::class.java) { throw TimeoutException("No protocol reply") }
            }
        assertTrue(failure.mustStopPlaybook())
    }
}
