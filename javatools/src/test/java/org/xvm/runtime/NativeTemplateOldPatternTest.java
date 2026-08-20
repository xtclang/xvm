package org.xvm.runtime;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Runnable demonstrations of the static native-template cache pattern that the
 * runtime is moving away from.
 */
public class NativeTemplateOldPatternTest {
    @Test
    public void staticInstanceCacheIsLastWriterWinsAcrossContainers() {
        OldTemplate templateA = new OldTemplate("container-A");
        assertSame(templateA, OldTemplate.INSTANCE);

        OldTemplate templateB = new OldTemplate("container-B");
        assertSame(templateB, OldTemplate.INSTANCE);

        assertNotSame(templateA, OldTemplate.INSTANCE);
        assertEquals("container-B", OldTemplate.ownerObservedByContainerA());
    }

    @Test
    public void constructorAssignmentCanExposePartiallyInitializedObject()
            throws Exception {
        CountDownLatch published = new CountDownLatch(1);
        CountDownLatch finish    = new CountDownLatch(1);

        EscapingTemplate.INSTANCE = null;

        Thread builder = new Thread(
                () -> new EscapingTemplate("container-A", published, finish),
                "escaping-template-builder");
        builder.start();

        assertTrue(published.await(5, TimeUnit.SECONDS));

        EscapingTemplate observed = EscapingTemplate.INSTANCE;
        assertNull(observed.metadata);

        finish.countDown();
        builder.join(5_000);

        assertEquals("metadata-for-container-A", EscapingTemplate.INSTANCE.metadata);
    }

    /**
     * A minimal stand-in for constructor-assigned native template INSTANCE
     * fields.
     */
    private static final class OldTemplate {
        static OldTemplate INSTANCE;

        OldTemplate(String containerId) {
            this.containerId = containerId;
            INSTANCE = this;
        }

        static String ownerObservedByContainerA() {
            return INSTANCE.containerId;
        }

        private final String containerId;
    }

    /**
     * A minimal stand-in for assigning INSTANCE before subclass construction
     * and metadata initialization have completed.
     */
    private static final class EscapingTemplate {
        static EscapingTemplate INSTANCE;

        EscapingTemplate(String containerId, CountDownLatch published, CountDownLatch finish) {
            INSTANCE = this;
            published.countDown();

            try {
                assertTrue(finish.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }

            metadata = "metadata-for-" + containerId;
        }

        private String metadata;
    }
}
