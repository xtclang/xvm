package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class XtcFailureFormattingTest {
    @Test
    void literalBracesDoNotMaskTheOriginalFailure() {
        final var cause = new IOException("original failure");
        final var failure = XtcPluginUtils.failure(cause, "User's {path} '{}': {}", "input", "bad");
        assertEquals("[plugin] User's {path} 'input': bad", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    @Test
    void onlyEmptyBracesArePlaceholders() {
        assertEquals("[plugin] Literal {0}, value {}, unfinished {",
            XtcPluginUtils.failure("Literal {0}, value {}, unfinished {", "{}").getMessage());
    }

    @Test
    void missingArgumentsLeaveTheirPlaceholders() {
        assertEquals("[plugin] first / {}", XtcPluginUtils.failure("{} / {}", "first").getMessage());
    }

    @Test
    void numericIdentifiersUseTheirPlainStringRepresentation() {
        assertEquals("[plugin] module 10000", XtcPluginUtils.failure("module {}", 10000).getMessage());
    }
}
