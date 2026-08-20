package org.xvm.runtime;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.util.Lazy;

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

    @Test
    public void staticStringFactoryCanReturnForeignOwnerHandles() {
        Owner ownerA = new Owner("container-A");
        Owner ownerB = new Owner("container-B");

        OldStringGlobals.init(ownerA);
        OwnerStringHandle handleA = OldStringGlobals.makeHandle("alpha");
        assertDoesNotThrow(() -> ownerA.stringTemplate.use(handleA));

        OldStringGlobals.init(ownerB);

        OwnerStringHandle handleFromGlobal = OldStringGlobals.makeHandle("alpha");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ownerA.stringTemplate.use(handleFromGlobal));
        assertEquals("string handle owner container-B used with template owner container-A",
                e.getMessage());

        OwnerScopedStrings stringsA = new OwnerScopedStrings(ownerA);
        OwnerScopedStrings stringsB = new OwnerScopedStrings(ownerB);

        assertDoesNotThrow(() -> ownerA.stringTemplate.use(stringsA.makeHandle("alpha")));
        assertDoesNotThrow(() -> ownerB.stringTemplate.use(stringsB.makeHandle("alpha")));
        assertNotSame(stringsA.emptyString(), stringsB.emptyString());
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
            stringTemplate = new OwnerStringTemplate(id);
        }

        private final OwnerTemplate       template;
        private final OwnerMethod         method;
        private final OwnerStringTemplate stringTemplate;
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

    /**
     * A minimal stand-in for xString's old JVM-global INSTANCE and common
     * handle caches.
     */
    private static final class OldStringGlobals {
        static OwnerStringTemplate template;
        static OwnerStringHandle   emptyString;

        static void init(Owner owner) {
            template    = owner.stringTemplate;
            emptyString = template.makeHandle("");
        }

        static OwnerStringHandle makeHandle(String value) {
            return value.isEmpty()
                    ? emptyString
                    : template.makeHandle(value);
        }
    }

    /**
     * A minimal stand-in for the owner-scoped replacement: the cache is still
     * lazy, but the owner is selected before any handle is made.
     */
    private static final class OwnerScopedStrings {
        OwnerScopedStrings(Owner owner) {
            template    = Lazy.of(() -> owner.stringTemplate);
            emptyString = Lazy.of(() -> template.get().makeHandle(""));
        }

        OwnerStringHandle makeHandle(String value) {
            return value.isEmpty()
                    ? emptyString()
                    : template.get().makeHandle(value);
        }

        OwnerStringHandle emptyString() {
            return emptyString.get();
        }

        private final Lazy<OwnerStringTemplate> template;
        private final Lazy<OwnerStringHandle>   emptyString;
    }

    private static final class OwnerStringTemplate {
        OwnerStringTemplate(String ownerId) {
            this.ownerId = ownerId;
        }

        OwnerStringHandle makeHandle(String value) {
            return new OwnerStringHandle(ownerId, value);
        }

        void use(OwnerStringHandle handle) {
            if (!ownerId.equals(handle.ownerId)) {
                throw new IllegalStateException(
                        "string handle owner " + handle.ownerId +
                        " used with template owner " + ownerId);
            }
        }

        private final String ownerId;
    }

    private static final class OwnerStringHandle {
        OwnerStringHandle(String ownerId, String value) {
            this.ownerId = ownerId;
            this.value   = value;
        }

        private final String ownerId;
        private final String value;
    }
}
