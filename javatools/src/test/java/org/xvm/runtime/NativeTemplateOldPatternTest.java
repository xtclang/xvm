package org.xvm.runtime;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void splitStaticMetadataCanMixOwnersAcrossContainers()
            throws Exception {
        CountDownLatch templateFromA = new CountDownLatch(1);
        CountDownLatch finishA       = new CountDownLatch(1);

        Owner ownerA = new Owner("container-A");
        Owner ownerB = new Owner("container-B");

        Thread initA = new Thread(
                () -> OldUtilsMetadata.init(ownerA, templateFromA, finishA),
                "old-utils-metadata-a");
        initA.start();

        assertTrue(templateFromA.await(5, TimeUnit.SECONDS));

        OldUtilsMetadata.init(ownerB);
        finishA.countDown();
        initA.join(5_000);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                OldUtilsMetadata::construct);
        assertEquals("template owner container-B used with method owner container-A",
                e.getMessage());

        OwnerScopedMetadata metadataA = new OwnerScopedMetadata(ownerA);
        OwnerScopedMetadata metadataB = new OwnerScopedMetadata(ownerB);

        assertDoesNotThrow(metadataA::construct);
        assertDoesNotThrow(metadataB::construct);
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

    /**
     * A minimal stand-in for Utils' old split static metadata block: a template
     * and a method are both owner-derived, but are stored in separate JVM-global
     * fields.
     */
    private static final class OldUtilsMetadata {
        static OwnerTemplate template;
        static OwnerMethod   method;

        static void init(Owner owner) {
            template = owner.template;
            method   = owner.method;
        }

        static void init(Owner owner, CountDownLatch templatePublished, CountDownLatch finish) {
            template = owner.template;
            templatePublished.countDown();

            try {
                assertTrue(finish.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }

            method = owner.method;
        }

        static void construct() {
            template.construct(method);
        }
    }

    /**
     * A minimal stand-in for the owner-scoped replacement: related metadata is
     * one immutable bundle selected from the caller's owner.
     */
    private static final class OwnerScopedMetadata {
        OwnerScopedMetadata(Owner owner) {
            template = owner.template;
            method   = owner.method;
        }

        void construct() {
            template.construct(method);
        }

        private final OwnerTemplate template;
        private final OwnerMethod   method;
    }

    private static final class Owner {
        Owner(String id) {
            template = new OwnerTemplate(id);
            method   = new OwnerMethod(id);
        }

        private final OwnerTemplate template;
        private final OwnerMethod   method;
    }

    private static final class OwnerTemplate {
        OwnerTemplate(String ownerId) {
            this.ownerId = ownerId;
        }

        void construct(OwnerMethod method) {
            if (!ownerId.equals(method.ownerId)) {
                throw new IllegalStateException(
                        "template owner " + ownerId + " used with method owner " + method.ownerId);
            }
        }

        private final String ownerId;
    }

    private static final class OwnerMethod {
        OwnerMethod(String ownerId) {
            this.ownerId = ownerId;
        }

        private final String ownerId;
    }
}
