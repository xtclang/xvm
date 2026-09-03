package org.xvm.api;


import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * The engine must compile what the CLI compiles.
 *
 * <p>{@code lib_json} is the smallest input found so far on which the two disagree. {@code xcc}
 * compiles it with zero diagnostics and produces {@code json.xtc}; {@link XtcEngine} reports five,
 * beginning with {@code COMPILER-137 "the evaluating expression buf has a type of Null"} at
 * {@code json/Lexer.x:457} and failing at {@code COMPILER-56 "could not find a matching method add
 * for type StringBuffer?"} on line 482. The declared type of a nullable local is being lost, so
 * the narrowing after its assignment never happens.</p>
 *
 * <p>Three explanations are ruled out by construction, and each was checked rather than assumed:</p>
 * <ul><li><b>Not concurrency</b> - one compile, one thread.</li>
 *     <li><b>Not warm reuse</b> - the engine is built here and compiles exactly one module, so no
 *         earlier compile can have left state behind. It reproduces identically when the same
 *         module is the only engine compile in a whole Gradle build.</li>
 *     <li><b>Not the module</b> - the CLI compiles the same source, from the same module path,
 *         cleanly.</li></ul>
 *
 * <p>What is left is that the two compile paths differ: {@code Launcher.launch} is a CLI entry
 * point, while the engine drives the compiler stages directly. That difference is the suspect, and
 * it is why engine compiles are gated behind {@code -Dxtc.plugin.engineCompile=true} in the Gradle
 * plugin rather than being the default.</p>
 *
 * <p>A smaller reduction was attempted and <b>rejected</b>: a seven-line module declaring
 * {@code StringBuffer? buf = Null}, assigning it inside an {@code if}, then calling {@code add}
 * fails on the CLI too, with the same two codes. Both compilers agree there, so it is a different
 * question and not a reduction of this one. Anyone shrinking this further must check the CLI still
 * succeeds on the reduced input, or they are pinning the wrong thing.</p>
 */
public class EngineCompilesLikeTheCliTest {
    @Test
    public void theEngineCompilesLibJson() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        Path root   = XdkOutputs.root();
        Path source = root.resolve("lib_json/src/main/x/json.x");
        assumeTrue(Files.isRegularFile(source), "lib_json sources are required");

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {
            var result = engine.compile(source);

            assertEquals(List.of(), result.diagnostics().stream()
                            .filter(d -> d.toString().contains("ERROR"))
                            .map(Object::toString)
                            .toList(),
                    "the engine reports errors the CLI does not on the same source");
        }
    }
}
