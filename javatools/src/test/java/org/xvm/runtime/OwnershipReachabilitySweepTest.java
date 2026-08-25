package org.xvm.runtime;


import java.lang.reflect.Proxy;

import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.CompletableFuture;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.xvm.asm.FileStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.runtime.OwnershipDiagnostics.SweepReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detector's own red/green harness (world X-ray slice 3). A reachability sweep is only as
 * trustworthy as the proof that it actually visits what it claims to visit, so these tests PLANT
 * foreign-owned state at every structural depth the sweep must penetrate - a direct field, an
 * array element, a map value, a deep nesting chain, an AtomicReference - and assert each plant is
 * found with a correct path-to-root; then they assert the legitimate shapes (clean containers,
 * ancestor/descendant ownership, immutable cross-owner handles) sweep clean. If a future change
 * makes the walker skip a shape, the corresponding plant goes unfound and this harness fails -
 * the answer to "can the detector detect everything" is measured here, not assumed.
 */
public class OwnershipReachabilitySweepTest {
    @Test
    public void plantedForeignStateIsFoundAtEveryDepth() {
        var runtime = new Runtime();
        try {
            var left  = new TestContainer(runtime, null, "left");
            var right = new TestContainer(runtime, null, "right");
            // one shared foreign composition so the plants themselves stay the only per-depth
            // violations (the composition itself is deduplicated by the visited set)
            var clzForeign = compositionOwnedBy(right);

            var holder = new Holder();
            holder.direct = new TestHandle(clzForeign, true);
            holder.array  = new Object[] {null, new TestHandle(clzForeign, true)};
            holder.map    = new HashMap<>();
            holder.map.put("planted", new TestHandle(clzForeign, true));
            holder.nested = new Nested(new Nested(new TestHandle(clzForeign, true)));
            holder.cell   = new AtomicReference<>(new TestHandle(clzForeign, true));
            holder.future = CompletableFuture.completedFuture(new TestHandle(clzForeign, true));

            left.putRuntimeOpCacheIfAbsent(new TestOp(), Category.SWEEP, holder, Holder.class);

            SweepReport report = OwnershipDiagnostics.sweepForeignReferences(left);

            assertFalse(report.isClean(), report::render);
            assertPathFound(report, ".direct");
            assertPathFound(report, ".array[1]");
            assertPathFound(report, ".value[0]");
            assertPathFound(report, ".payload");
            assertPathFound(report, ".get()");
            assertPathFound(report, ".getNow()");
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    public void mutableForeignHandleIsFlaggedButImmutableIsLegitimateCurrency() {
        var runtime = new Runtime();
        try {
            var left  = new TestContainer(runtime, null, "left");
            var right = new TestContainer(runtime, null, "right");

            var holder = new Holder();
            holder.direct = new TestHandle(compositionOwnedBy(right), true);
            holder.array  = new Object[] {new TestHandle(compositionOwnedBy(right), false)};

            left.putRuntimeOpCacheIfAbsent(new TestOp(), Category.SWEEP, holder, Holder.class);

            SweepReport report = OwnershipDiagnostics.sweepForeignReferences(left);

            assertPathFound(report, ".direct");
            assertTrue(report.violations().stream()
                            .noneMatch(violation -> violation.path().endsWith(".array[0]")),
                    () -> "immutable handles are legitimate cross-owner currency:\n" + report.render());
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    public void relatedOwnersSweepClean() {
        var runtime = new Runtime();
        try {
            var parent = new TestContainer(runtime, null, "parent");
            var child  = new TestContainer(runtime, parent, "child");

            var holderParent = new Holder();
            holderParent.direct = new TestHandle(compositionOwnedBy(child), true);
            parent.putRuntimeOpCacheIfAbsent(new TestOp(), Category.SWEEP, holderParent, Holder.class);

            var holderChild = new Holder();
            holderChild.direct = new TestHandle(compositionOwnedBy(parent), true);
            child.putRuntimeOpCacheIfAbsent(new TestOp(), Category.SWEEP, holderChild, Holder.class);

            SweepReport reportParent = OwnershipDiagnostics.sweepForeignReferences(parent);
            SweepReport reportChild  = OwnershipDiagnostics.sweepForeignReferences(child);

            assertTrue(reportParent.isClean(), reportParent::render);
            assertTrue(reportChild.isClean(), reportChild::render);
        } finally {
            runtime.shutdownXVM();
        }
    }

    @Test
    public void cleanContainerSweepsClean() {
        var runtime = new Runtime();
        try {
            var container = new TestContainer(runtime, null, "clean");
            var holder    = new Holder();
            holder.direct = new TestHandle(compositionOwnedBy(container), true);
            container.putRuntimeOpCacheIfAbsent(new TestOp(), Category.SWEEP, holder, Holder.class);

            SweepReport report = OwnershipDiagnostics.sweepForeignReferences(container);

            assertTrue(report.isClean(), report::render);
            assertTrue(report.blindSpots().isEmpty(),
                    () -> "a clean sweep must also have followed every edge:\n" + report.render());
            assertTrue(report.objectsVisited() > 1, "the sweep must actually walk the container");
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * A pending future is counted and rendered - never silent - but does not fail the sweep:
     * its value does not exist yet and its dependent continuations are same-container by
     * construction (cross-container calls exchange proxies), so the unread content cannot hide
     * a foreign reference. The stress harness proved runs routinely end with fire-and-forget
     * futures still pending; only genuinely unfollowable edges are failing blind spots.
     */
    @Test
    public void pendingFutureIsCountedAndRenderedButDoesNotFailTheSweep() {
        var runtime = new Runtime();
        try {
            var container = new TestContainer(runtime, null, "pending");
            var holder    = new Holder();
            holder.future = new CompletableFuture<>();
            container.putRuntimeOpCacheIfAbsent(new TestOp(), Category.SWEEP, holder, Holder.class);

            SweepReport report = OwnershipDiagnostics.sweepForeignReferences(container);

            assertTrue(report.isClean(),
                    () -> "a pending future must not fail the sweep:\n" + report.render());
            assertTrue(report.violations().isEmpty(), report::render);
            assertTrue(report.blindSpots().isEmpty(), report::render);
            assertEquals(1, report.pendingFutures().size(), report::render);
            assertTrue(report.pendingFutures().get(0).contains("pending CompletableFuture"),
                    report::render);
            assertTrue(report.render().contains("pending CompletableFuture"),
                    () -> "pending futures must stay visible in the render:\n" + report.render());
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- fixtures ------------------------------------------------------------------------------

    private static void assertPathFound(SweepReport report, String pathSuffix) {
        assertEquals(1, report.violations().stream()
                        .filter(violation -> violation.path().endsWith(pathSuffix))
                        .count(),
                () -> "expected exactly one violation with path ending '" + pathSuffix
                        + "':\n" + report.render());
    }

    /**
     * A TypeComposition answering only {@link TypeComposition#getContainer()}; a reflective proxy
     * spares this harness the ~30-method fake that owner extraction does not consult.
     */
    private static TypeComposition compositionOwnedBy(Container container) {
        return (TypeComposition) Proxy.newProxyInstance(
                OwnershipReachabilitySweepTest.class.getClassLoader(),
                new Class<?>[] {TypeComposition.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getContainer" -> container;
                    case "toString"     -> "FakeComposition";
                    case "hashCode"     -> System.identityHashCode(proxy);
                    case "equals"       -> proxy == args[0];
                    default             -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class TestHandle
            extends ObjectHandle {
        TestHandle(TypeComposition clazz, boolean fMutable) {
            super(clazz);
            m_fMutable = fMutable;
        }
    }

    private static final class Holder {
        Object               direct;
        Object[]             array;
        Map<String, Object>  map;
        Nested               nested;
        AtomicReference<?>   cell;
        CompletableFuture<?> future;
    }

    private record Nested(Object payload) {
        Nested(Nested inner) {
            this((Object) inner);
        }
    }

    private enum Category {SWEEP}

    private static class TestOp
            extends Op {
        @Override
        public int getOpCode() {
            return 0;
        }

        @Override
        public int process(Frame frame, int iPC) {
            return iPC + 1;
        }
    }

    private static class TestContainer
            extends Container {
        TestContainer(Runtime runtime, Container parent, String name) {
            super(runtime, parent, new FileStructure(name).getModuleId());
        }

        @Override
        public boolean isSpecified(String name) {
            return false;
        }

        @Override
        public boolean isPresent(IdentityConstant id) {
            return false;
        }

        @Override
        public boolean isVersionMatch(ModuleConstant module, VersionConstant version) {
            return false;
        }

        @Override
        public boolean isVersion(VersionConstant version) {
            return false;
        }

        @Override
        public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type,
                                          ObjectHandle opts) {
            return null;
        }
    }
}
