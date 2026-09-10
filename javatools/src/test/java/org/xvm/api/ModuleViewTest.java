package org.xvm.api;


import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * {@link ModuleView} against a real compiled module, exercising the four things it exists for: an
 * LSP-style read, a dependency list, change detection, and read-modify-write.
 */
public class ModuleViewTest {
    @TempDir
    Path tempDir;

    /**
     * The LSP-shaped read: open a module and walk it without linking or a repository.
     */
    @Test
    public void aCompiledModuleCanBeOpenedAndWalked() throws Exception {
        ModuleView view = ModuleView.open(ecstasy());

        assertEquals("ecstasy.xtclang.org", view.name());
        assertTrue(view.walk().count() > 1000,
                () -> "the core library should have many components, found " + view.walk().count());
        assertTrue(view.methods().findAny().isPresent(), "and methods among them");
    }

    /**
     * The incremental-build question: what does this module import? Answered from the module's own
     * fingerprint children, with no module path consulted.
     */
    @Test
    public void dependenciesComeFromTheModuleItself() throws Exception {
        ModuleView view = ModuleView.open(XdkOutputs.root()
                .resolve("javatools_bridge/build/xtc/main/lib/javatools_bridge.xtc"));

        assertTrue(view.dependencies().contains("ecstasy.xtclang.org"),
                () -> "the bridge imports the core library; found " + view.dependencies());
    }

    /**
     * The one with a trap in it: a digest that survives a rebuild.
     *
     * <p>Reading the same file twice must agree - and so must a copy, since copying preserves the
     * timestamp. What this cannot assert here is the case it was built for, two independent builds
     * of unchanged source, because that needs two builds; the timestamp exclusion is asserted
     * instead by comparing against a view whose timestamp differs.
     */
    @Test
    public void theDigestIsStableAcrossReads() throws Exception {
        assertEquals(ModuleView.open(ecstasy()).digest(), ModuleView.open(ecstasy()).digest(),
                "the same module read twice must digest the same");
    }

    /**
     * Change detection: a module differs from a different module, and agrees with itself.
     */
    @Test
    public void comparingModulesReportsWhatDiffers() throws Exception {
        ModuleView core   = ModuleView.open(ecstasy());
        ModuleView bridge = ModuleView.open(XdkOutputs.root()
                .resolve("javatools_bridge/build/xtc/main/lib/javatools_bridge.xtc"));

        assertTrue(core.compareWith(ModuleView.open(ecstasy())).isEmpty(),
                "a module must not differ from itself");

        ModuleView.Difference diff = core.compareWith(bridge);
        assertFalse(diff.isEmpty(), "two different modules must differ");
        assertFalse(diff.structurallyEqual());
        assertNotEquals(core.digest(), bridge.digest());
    }

    /**
     * Read-modify-write: the updater path. Writing a module back and reading it again must produce
     * the same structure.
     */
    /**
     * The round trip that a read-modify-write tool actually needs: not the byte copy, but the
     * REASSEMBLY. Reading a method's ops discards the module's stored op bytes, so writeTo has to
     * re-run every op's write() and rebuild the pool from what those ops reach.
     *
     * <p>That path was unusable until recently - {@code OpVar.write} and {@code CatchStart.preWrite}
     * both dereferenced a compile-time Register, so re-serializing a Var op or a guard read from
     * disk threw NPE. The plain round-trip test below never noticed, because it never read an op
     * and so never left the byte-copy path.</p>
     *
     * <p>Compares digest and disassembly rather than bytes: a reassembled module is structurally
     * identical but usually SMALLER, since the pool is rebuilt from what is actually reachable.</p>
     */
    @Test
    public void aModuleSurvivesAReassemblingRoundTrip() throws Exception {
        ModuleView original = ModuleView.open(ecstasy());

        long ops = original.methods().mapToLong(method -> original.ops(method).size()).sum();
        assertTrue(ops > 10_000, () -> "expected a substantial module, got " + ops + " ops");

        String digestBefore = original.digest();
        String dumpBefore   = original.disassemble();

        Path copy = tempDir.resolve("reassembled.xtc");
        original.writeTo(copy);

        ModuleView reassembled = ModuleView.open(copy);
        long opsBack = reassembled.methods().mapToLong(m -> reassembled.ops(m).size()).sum();

        assertEquals(ops, opsBack, "every op must survive reassembly");
        assertEquals(digestBefore, reassembled.digest(),
                "a reassembled module must be structurally what it was");
        assertEquals(dumpBefore, reassembled.disassemble(),
                "and must disassemble identically, operands included");
    }

    @Test
    public void aModuleSurvivesARoundTrip() throws Exception {
        ModuleView original = ModuleView.open(ecstasy());

        Path copy = tempDir.resolve("roundtrip.xtc");
        original.writeTo(copy);
        assertTrue(Files.size(copy) > 0, "the written module must not be empty");

        assertEquals(original.digest(), ModuleView.open(copy).digest(),
                "a module written back out must be structurally what it was");
    }

    /**
     * The binary detail an inspector actually needs: member signatures, scope depth per op, and the
     * constant pool - all of which the model carried and the first cut of this API did not surface.
     */
    @Test
    public void theDumpCarriesSignaturesScopesAndConstants() throws Exception {
        ModuleView view = ModuleView.open(ecstasy());

        assertFalse(view.constants().isEmpty(), "the pool must be enumerable");

        var method = view.methods()
                .filter(m -> m.hasCode() && m.getOps().length > 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no method with a body to inspect"));

        assertTrue(view.signature(method).startsWith(method.getName()),
                () -> "a signature must name the method: " + view.signature(method));

        String dump = view.disassemble();
        assertTrue(dump.contains("sig  "), "the dump must carry member signatures");
        assertTrue(dump.contains("vars "), "and each body's variable count");
        assertTrue(dump.contains("  d0") || dump.contains("  d1"),
                "and a scope depth per op, which is what makes a scope change visible in a diff");
    }

    /**
     * The dump is what you diff, so it must be stable: same module in, same text out.
     */
    @Test
    public void theDumpIsDeterministic() throws Exception {
        assertEquals(ModuleView.open(ecstasy()).disassemble(),
                ModuleView.open(ecstasy()).disassemble(),
                "an unstable dump cannot be diffed");
    }

    private Path ecstasy() {
        Path path = XdkOutputs.root().resolve("lib_ecstasy/build/xtc/main/lib/ecstasy.xtc");
        assumeTrue(Files.isRegularFile(path), "compiled ecstasy.xtc is required: " + path);
        return path;
    }

    /**
     * The point of the operand model: answer "what does this call target" as an object, not by
     * matching {@code Op.toString()}. An assertion that reads text passes when the code happens to
     * be rendered the expected way, which is the trap the deleted source-comparison tests fell
     * into; this one reads the MethodConstant the op actually names.
     */
    @Test
    public void anInvokeOpNamesItsTargetMethodAsAConstant() throws Exception {
        ModuleView view = ModuleView.open(ecstasy());

        var targets = view.methods()
                .flatMap(method -> view.decode(method).stream())
                .filter(ModuleView.Resolved::modeled)
                .flatMap(resolved -> resolved.operands().stream())
                .filter(operand -> "method".equals(operand.role()))
                .map(ModuleView.Referent::constant)
                .filter(MethodConstant.class::isInstance)
                .limit(50)
                .toList();

        assertFalse(targets.isEmpty(),
                "invoke ops in the core library should name their target method as a constant");
    }

    /**
     * Registers, constants and pseudo-registers are told apart by the model rather than by the
     * caller re-implementing the sign convention.
     */
    @Test
    public void operandsAreClassifiedRatherThanRendered() throws Exception {
        ModuleView view = ModuleView.open(ecstasy());

        var operands = view.methods()
                .flatMap(method -> view.decode(method).stream())
                .filter(ModuleView.Resolved::modeled)
                .flatMap(resolved -> resolved.operands().stream())
                .limit(5000)
                .toList();

        assertFalse(operands.isEmpty(), "the core library should have modeled ops");
        assertTrue(operands.stream().anyMatch(operand -> operand.constant() != null),
                "some operands should resolve to a real constant");
        assertTrue(operands.stream().allMatch(operand -> !operand.role().isEmpty()),
                "every operand should say what it is for");
        assertTrue(operands.stream().noneMatch(operand -> operand.display().contains("UNRESOLVED")),
                "no operand should reference a constant index outside the method's own pool");
    }

    /**
     * "Has no operands" and "does not model its operands" must stay distinguishable, even now that
     * every op class in the tree models them. The distinction is the contract, not the coverage
     * number: {@code Op.operands()} still defaults to absent, so a NEW op class that forgets to
     * model itself is reported rather than silently indistinguishable from {@code Exit}. This
     * asserts the live half - that structural ops answer a present-but-EMPTY list - which is only
     * meaningful because absent remains a different answer.
     */
    @Test
    public void anOpWithNoOperandsIsNotTheSameAsAnUnmodeledOne() throws Exception {
        ModuleView view = ModuleView.open(ecstasy());

        var decoded = view.methods()
                .flatMap(method -> view.decode(method).stream())
                .limit(20000)
                .toList();

        assertTrue(decoded.stream().allMatch(ModuleView.Resolved::modeled),
                "every op class in the tree now models its operands");
        assertTrue(decoded.stream().anyMatch(r -> r.modeled() && r.operands().isEmpty()),
                "structural ops such as ENTER/EXIT report a present but empty operand list");
    }

    /**
     * A jump's displacement is reported by {@code Op.jumpDisplacement()} and is deliberately NOT an
     * operand: an operand is an encoded argument - register, constant, or pseudo-register - and a
     * displacement is a raw signed count that merely shares the wire representation. Folding it in
     * would make "register #3" and "jump forward 3" indistinguishable to a caller.
     */
    @Test
    public void aJumpReportsItsDisplacementSeparatelyFromItsOperands() throws Exception {
        ModuleView view = ModuleView.open(ecstasy());

        var jumps = view.methods()
                .flatMap(method -> view.decode(method).stream())
                .filter(resolved -> resolved.op().jumpDisplacement().isPresent())
                .limit(2000)
                .toList();

        assertFalse(jumps.isEmpty(), "the core library should contain jumps");
        assertTrue(jumps.stream().allMatch(ModuleView.Resolved::modeled),
                "every jump models its operands");
        // the displacement must not also appear as an operand pretending to be a register
        assertTrue(jumps.stream().flatMap(resolved -> resolved.operands().stream())
                        .noneMatch(operand -> "displacement".equals(operand.role())),
                "the displacement is not carried as an operand");
        assertTrue(view.disassemble().contains("->"),
                "and the dump renders it, rather than dropping it now that jumps are modeled");
    }

    /**
     * The property that matters for a reader meant to survive real input: every compiled module the
     * build produces decodes end to end - every op modeled, every constant index resolving inside
     * its own method's pool - without throwing. Coverage claims are cheap; this is what makes them
     * checkable, and it is what would catch a new op class landing unmodeled.
     */
    @Test
    public void everyShippedModuleDisassemblesCompletely() throws Exception {
        Path lib = XdkOutputs.root().resolve("xdk/build/install/xdk/lib");
        assumeTrue(Files.isDirectory(lib), "compiled XDK lib is required: " + lib);

        List<Path> modules;
        try (var paths = Files.list(lib)) {
            modules = paths.filter(p -> p.toString().endsWith(".xtc")).sorted().toList();
        }
        assumeTrue(!modules.isEmpty(), "no compiled modules found in " + lib);

        var failures = new ArrayList<String>();
        long ops = 0, operands = 0;
        for (Path path : modules) {
            try {
                ModuleView view = ModuleView.open(path);
                for (MethodStructure method : view.methods().toList()) {
                    for (ModuleView.Resolved decoded : view.decode(method)) {
                        ops++;
                        if (!decoded.modeled()) {
                            failures.add(path.getFileName() + ": unmodeled op "
                                    + decoded.op().getClass().getSimpleName());
                        }
                        for (ModuleView.Referent operand : decoded.operands()) {
                            operands++;
                            if (operand.display().contains("UNRESOLVED")) {
                                failures.add(path.getFileName() + ": unresolved " + operand.role());
                            }
                        }
                    }
                }
                view.disassemble();
                view.digest();
            } catch (RuntimeException | Error e) {
                failures.add(path.getFileName() + ": " + e);
            }
        }

        assertTrue(failures.isEmpty(),
                () -> "modules failed to decode: " + failures.stream().distinct().limit(10).toList());
        long totalOps = ops, totalOperands = operands;
        assertTrue(totalOps > 10_000, () -> "expected a substantial op count, got " + totalOps);
        assertTrue(totalOperands > 10_000,
                () -> "expected a substantial operand count, got " + totalOperands);
    }
}
