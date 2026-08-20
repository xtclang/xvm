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

    @Test
    public void staticRefSignatureCacheCanUseForeignOwner() {
        Owner ownerA = new Owner("container-A");
        Owner ownerB = new Owner("container-B");

        OldRefGlobals.init(ownerA);
        OwnerRefHandle refA = ownerA.refTemplate.makeHandle();
        assertDoesNotThrow(() -> OldRefGlobals.getReferent(refA));

        OldRefGlobals.init(ownerB);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> OldRefGlobals.getReferent(refA));
        assertEquals("ref handle owner container-A used with signature owner container-B",
                e.getMessage());

        OwnerScopedRefMetadata refsA = new OwnerScopedRefMetadata(ownerA);
        OwnerScopedRefMetadata refsB = new OwnerScopedRefMetadata(ownerB);

        assertDoesNotThrow(() -> refsA.getReferent(refA));
        assertDoesNotThrow(() -> refsB.getReferent(ownerB.refTemplate.makeHandle()));
    }

    @Test
    public void derivedRefAndVarTemplatesUseOwnerBaseSignatures() {
        Owner owner = new Owner("container-A");

        IllegalStateException getError = assertThrows(IllegalStateException.class,
                () -> new PerTemplateRefMetadata(owner.derivedVarTemplate)
                        .getReferent(owner.derivedVarTemplate.makeHandle()));
        assertEquals("template @Lazy Var in container-A does not declare get",
                getError.getMessage());

        IllegalStateException setError = assertThrows(IllegalStateException.class,
                () -> new PerTemplateVarMetadata(owner.derivedVarTemplate)
                        .setReferent(owner.derivedVarTemplate.makeHandle()));
        assertEquals("template @Lazy Var in container-A does not declare set",
                setError.getMessage());

        OwnerRefHandle        handle = owner.derivedVarTemplate.makeHandle();
        OwnerScopedRefMetadata refs  = new OwnerScopedRefMetadata(owner);
        OwnerScopedVarMetadata vars  = new OwnerScopedVarMetadata(owner);

        assertDoesNotThrow(() -> refs.getReferent(handle));
        assertDoesNotThrow(() -> vars.setReferent(handle));
    }

    @Test
    public void staticExceptionClassCacheCanUseForeignOwner() {
        Owner ownerA = new Owner("container-A");
        Owner ownerB = new Owner("container-B");

        OldExceptionGlobals.init(ownerA);
        assertDoesNotThrow(() -> ownerA.raise(OldExceptionGlobals.illegalState("first")));

        OldExceptionGlobals.init(ownerB);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ownerA.raise(OldExceptionGlobals.illegalState("second")));
        assertEquals("exception class owner container-B used with frame owner container-A",
                e.getMessage());

        OwnerScopedExceptions exceptionsA = new OwnerScopedExceptions(ownerA);
        OwnerScopedExceptions exceptionsB = new OwnerScopedExceptions(ownerB);

        assertDoesNotThrow(() -> ownerA.raise(exceptionsA.illegalState("third")));
        assertDoesNotThrow(() -> ownerB.raise(exceptionsB.illegalState("fourth")));
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
            this.id            = id;
            template           = new OwnerTemplate(id);
            method             = new OwnerMethod(id);
            exceptionInfo      = new OwnerExceptionInfo(id);
            stringTemplate     = new OwnerStringTemplate(id);
            refTemplate        = new OwnerRefTemplate(id);
            refSignature       = new OwnerSignature(id);
            varSignature       = new OwnerSignature(id);
            derivedVarTemplate = new OwnerDerivedVarTemplate(id);
        }

        void raise(OwnerExceptionHandle handle) {
            if (!id.equals(handle.ownerId)) {
                throw new IllegalStateException(
                        "exception class owner " + handle.ownerId +
                        " used with frame owner " + id);
            }
        }

        private final String                  id;
        private final OwnerTemplate           template;
        private final OwnerMethod             method;
        private final OwnerExceptionInfo      exceptionInfo;
        private final OwnerStringTemplate     stringTemplate;
        private final OwnerRefTemplate        refTemplate;
        private final OwnerSignature          refSignature;
        private final OwnerSignature          varSignature;
        private final OwnerDerivedVarTemplate derivedVarTemplate;
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
     * A minimal stand-in for xException's old static well-known class cache.
     */
    private static final class OldExceptionGlobals {
        static OwnerExceptionInfo info;

        static void init(Owner owner) {
            info = owner.exceptionInfo;
        }

        static OwnerExceptionHandle illegalState(String message) {
            return info.illegalState(message);
        }
    }

    /**
     * A minimal stand-in for the owner-scoped replacement: exception classes are still cached, but
     * the selected cache is the caller's owner.
     */
    private static final class OwnerScopedExceptions {
        OwnerScopedExceptions(Owner owner) {
            info = Lazy.of(() -> owner.exceptionInfo);
        }

        OwnerExceptionHandle illegalState(String message) {
            return info.get().illegalState(message);
        }

        private final Lazy<OwnerExceptionInfo> info;
    }

    private static final class OwnerExceptionInfo {
        OwnerExceptionInfo(String ownerId) {
            this.ownerId = ownerId;
        }

        OwnerExceptionHandle illegalState(String message) {
            return new OwnerExceptionHandle(ownerId, "IllegalState", message);
        }

        private final String ownerId;
    }

    private static final class OwnerExceptionHandle {
        OwnerExceptionHandle(String ownerId, String className, String message) {
            this.ownerId   = ownerId;
            this.className = className;
            this.message   = message;
        }

        private final String ownerId;
        private final String className;
        private final String message;
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

    /**
     * A minimal stand-in for xRef/xVar's old static signature caches.
     */
    private static final class OldRefGlobals {
        static OwnerSignature getSignature;

        static void init(Owner owner) {
            getSignature = owner.refSignature;
        }

        static void getReferent(OwnerRefHandle handle) {
            handle.invoke(getSignature);
        }
    }

    /**
     * A minimal stand-in for the owner-scoped replacement: the get signature is
     * still computed lazily, but it is owned by one Ref template owner.
     */
    private static final class OwnerScopedRefMetadata {
        OwnerScopedRefMetadata(Owner owner) {
            getSignature = Lazy.of(() -> owner.refSignature);
        }

        void getReferent(OwnerRefHandle handle) {
            handle.invoke(getSignature.get());
        }

        private final Lazy<OwnerSignature> getSignature;
    }

    /**
     * A stand-in for the incorrect "make every Ref-derived template own its own
     * get signature" rewrite. Var and annotation templates inherit Ref.get() and
     * must use the owner base Ref signature.
     */
    private static final class PerTemplateRefMetadata {
        PerTemplateRefMetadata(OwnerDerivedVarTemplate template) {
            getSignature = Lazy.of(template::findGetSignature);
        }

        void getReferent(OwnerRefHandle handle) {
            handle.invoke(getSignature.get());
        }

        private final Lazy<OwnerSignature> getSignature;
    }

    /**
     * A stand-in for the incorrect "make every Var-derived template own its own
     * set signature" rewrite. Var annotations such as @Lazy inherit Var.set()
     * and must use the owner base Var signature.
     */
    private static final class PerTemplateVarMetadata {
        PerTemplateVarMetadata(OwnerDerivedVarTemplate template) {
            setSignature = Lazy.of(template::findSetSignature);
        }

        void setReferent(OwnerRefHandle handle) {
            handle.invoke(setSignature.get());
        }

        private final Lazy<OwnerSignature> setSignature;
    }

    /**
     * A minimal stand-in for the owner-scoped replacement: the set signature is
     * still lazy, but it is owned by one base Var template owner.
     */
    private static final class OwnerScopedVarMetadata {
        OwnerScopedVarMetadata(Owner owner) {
            setSignature = Lazy.of(() -> owner.varSignature);
        }

        void setReferent(OwnerRefHandle handle) {
            handle.invoke(setSignature.get());
        }

        private final Lazy<OwnerSignature> setSignature;
    }

    private static final class OwnerRefTemplate {
        OwnerRefTemplate(String ownerId) {
            this.ownerId = ownerId;
        }

        OwnerRefHandle makeHandle() {
            return new OwnerRefHandle(ownerId);
        }

        private final String ownerId;
    }

    private static final class OwnerDerivedVarTemplate {
        OwnerDerivedVarTemplate(String ownerId) {
            this.ownerId = ownerId;
        }

        OwnerRefHandle makeHandle() {
            return new OwnerRefHandle(ownerId);
        }

        OwnerSignature findGetSignature() {
            throw new IllegalStateException(
                    "template @Lazy Var in " + ownerId + " does not declare get");
        }

        OwnerSignature findSetSignature() {
            throw new IllegalStateException(
                    "template @Lazy Var in " + ownerId + " does not declare set");
        }

        private final String ownerId;
    }

    private static final class OwnerRefHandle {
        OwnerRefHandle(String ownerId) {
            this.ownerId = ownerId;
        }

        void invoke(OwnerSignature signature) {
            if (!ownerId.equals(signature.ownerId)) {
                throw new IllegalStateException(
                        "ref handle owner " + ownerId +
                        " used with signature owner " + signature.ownerId);
            }
        }

        private final String ownerId;
    }

    private static final class OwnerSignature {
        OwnerSignature(String ownerId) {
            this.ownerId = ownerId;
        }

        private final String ownerId;
    }
}
