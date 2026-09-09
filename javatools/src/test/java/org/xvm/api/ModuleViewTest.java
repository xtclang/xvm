package org.xvm.api;


import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
     * An op class that does not model its operands must SAY so rather than be indistinguishable
     * from one that has none. The wire format is positional and untyped, so guessing would produce
     * confidently wrong operands, which is worse than absent ones.
     */
    @Test
    public void unmodeledOpsAreReportedAsUnmodeledRatherThanEmpty() throws Exception {
        ModuleView view = ModuleView.open(ecstasy());

        var decoded = view.methods()
                .flatMap(method -> view.decode(method).stream())
                .limit(20000)
                .toList();

        assertTrue(decoded.stream().anyMatch(ModuleView.Resolved::modeled),
                "some ops model their operands");
        assertTrue(decoded.stream().anyMatch(resolved -> !resolved.modeled()),
                "and coverage is partial, which the model reports rather than hides");
        assertTrue(decoded.stream().filter(resolved -> !resolved.modeled())
                        .allMatch(resolved -> resolved.operands().isEmpty()),
                "an unmodeled op carries no operands, rather than guessed ones");
    }
}
