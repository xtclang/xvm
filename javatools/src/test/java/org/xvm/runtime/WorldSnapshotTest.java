package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;

import org.xvm.test.XdkOutputs;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.runtime.OwnershipDiagnostics.WorldSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the world-state snapshot: a structured, printable, diffable capture of every live
 * container in a runtime, with the full ownership validation across the set. Slice 1 of the
 * world-state plan in ownership-diagnostics.md - the tool that lets same-JVM sequential and
 * parallel runs compare whole worlds instead of eyeballing text dumps.
 */
public class WorldSnapshotTest {
    /**
     * The snapshot must enumerate the world from the runtime registry (no caller-supplied
     * container list), validate ownership across the complete set, and diff by container identity
     * so that a completed run's container surviving into a later world shows up as retained -
     * the sequential-run leak signal.
     */
    @Test
    public void worldsAreCapturedValidatedAndDiffable() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        var runtime = new Runtime();
        try {
            var containerA = NativeContainer.create(runtime, XdkOutputs.systemRepository(), ErrorListener.RUNTIME);

            WorldSnapshot world1 = OwnershipDiagnostics.snapshotWorld(runtime);
            assertEquals(1, world1.containers().size());
            assertTrue(world1.isValid(), () -> world1.validation().message());

            var containerB = NativeContainer.create(runtime, XdkOutputs.systemRepository(), ErrorListener.RUNTIME);

            WorldSnapshot world2 = OwnershipDiagnostics.snapshotWorld(runtime);
            assertEquals(2, world2.containers().size());
            assertTrue(world2.isValid(), () -> world2.validation().message());

            var diff = world2.diffFrom(world1);
            assertEquals(1, diff.added().size(), "the new container must appear as added");
            assertEquals(1, diff.retained().size(),
                    "the still-referenced container must appear as retained");
            assertEquals(0, diff.removed().size());
            assertEquals(System.identityHashCode(containerB),
                    diff.added().get(0).identity());
            assertEquals(System.identityHashCode(containerA),
                    diff.retained().get(0).identity());

            var render = world2.render();
            assertTrue(render.contains("world: 2 container(s)"), render);
            assertTrue(render.contains("ownership: valid"), render);
            assertTrue(diff.render().contains("added: 1"), diff.render());
        } finally {
            runtime.shutdownXVM();
        }
    }

    // ----- helpers (same discovery as ClassCompositionSafePublicationTest) ----------------------





}
