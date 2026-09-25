package org.xvm.xdk;

import java.io.File;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HexFormat;
import java.util.List;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the exact text that the display methods produce.
 *
 * <p>This is a differential gate, not a purity gate. It says nothing about whether rendering has
 * side effects; it says only that the characters coming out are the ones that came out before.
 * Its purpose is to let a change to a display method be reviewed on its own terms: if this test is
 * green, the change altered no rendered character anywhere in the constant pool of the real system
 * modules, and the reviewer is free to think about the side effect the change is actually for.
 *
 * <p><b>Why it lives in the xdk project.</b> It needs a fully realized type system - a live
 * {@code ConstantPool} with real content in it - which means it cannot run until the XTC libraries
 * have been compiled. {@code :xdk:test} is the one test task that {@code dependsOn(installDist)},
 * so it is the only place where those modules are guaranteed to be on disk. The same test in
 * {@code javatools} is scheduled before the libraries it needs are built, where its only honest
 * option is to skip - and a gate that silently skips is worse than no gate, because it reports
 * green having checked nothing.
 *
 * <p>Correspondingly there is no {@code assumeTrue} here. Missing modules are a build-ordering
 * bug, not a reason to pass quietly, so this fails loudly and says what to run.
 *
 * <p><b>Why it is gated on RUN_INTEGRATION_TESTS.</b> Standing up a runtime and rendering the pool
 * twice costs around thirty seconds, which is a poor trade on the tail of every local build to
 * guard an invariant that can only break when someone edits a display method. CI sets the variable,
 * so the check runs on every commit, where that cost disappears into a build already several
 * minutes long; locally you ask for it. Note that this is a declared policy with a reason JUnit
 * reports, not the accidental skip described above - the distinction being that the environment
 * where the check matters is the one that enables it.
 *
 * <p>It renders every constant in a freshly built container-zero pool and hashes the result. The
 * corpus is the dependency closure of the native bridge - ecstasy, the turtle module, _native, and
 * the eight libraries the bridge pulls in (aggregate, collections, convert, crypto, json, net,
 * sec, web) - 83,977 constants across 56 distinct constant kinds, so a behavioural change to any
 * widely reached display method moves the digest.
 *
 * <p><b>On not merging the rest of the XDK.</b> Merging all 23 installed modules instead grows the
 * corpus to 108,326 constants but covers <i>no additional constant kind</i>, and in fact covers one
 * fewer: the rare kinds (a single RecursiveTypeConstant, ServiceTypeConstant and
 * TypeSequenceTypeConstant apiece, two MethodBindingConstants, twelve DifferenceTypeConstants) all
 * originate in ecstasy and appear at identical counts either way, while NativeRebaseConstant exists
 * only once the runtime has linked the native templates. The extra 24,000 constants are more of
 * what is already covered, bought at the price of a digest that churns whenever any of thirteen
 * further libraries changes.
 *
 * <p><b>On the recorded digest.</b> The expected value is not a magic number; it is produced by
 * this same test. Re-record it deliberately with {@code -Dxvm.render.record=true} and pass it back
 * in through {@code -Dxvm.render.expected=...}. Re-recording is the explicit act of saying "I meant
 * to change what the runtime renders", which is the decision that should never happen by accident.
 * It is deliberately not baked into the build, because the corpus moves whenever any of those
 * eleven modules changes and a hardcoded value would fail the build for unrelated library edits.
 *
 * <p><b>How to compare two builds.</b> Both the digest and the sorted per-constant dump are written
 * to {@code build/} and uploaded by CI as the {@code render-equivalence-<sha>} artifact, so that
 * comparing a branch against master is two downloads and a diff - the digest says whether anything
 * moved, the dump says which constant. Locally the same comparison needs the corpus held still,
 * since it is an input: run the two revisions with {@code -x :xdk:installDist} so that both render
 * identical .xtc files and only the display code differs.
 *
 * <p><b>Why it renders twice.</b> A display method that mutates on its first call and then finds
 * its own work already done can render differently the first time than the second. Hashing a
 * single pass would miss that, so both passes are hashed and compared to each other. That check
 * needs no configuration and therefore always runs.
 *
 * <p><b>Why the digest is over sorted lines.</b> The order constants occupy in the pool depends on
 * the order they were loaded, so hashing the raw sweep would make the digest a function of the
 * module path as well as of the text - two builds with identical rendering but a different load
 * order would disagree, which is a false positive in the only thing this test exists to report.
 * Sorting first makes the digest a function of the rendered multiset alone. Any change to what a
 * display method emits still moves it.
 */
class XdkRenderEquivalenceTest {
    /**
     * The digest of every constant's rendered form over the system modules, supplied on the
     * command line. Empty means "only check that the two passes agree", which is still a real
     * check.
     */
    private static final String EXPECTED_DIGEST = System.getProperty("xvm.render.expected", "");

    /**
     * The installed distribution, relative to the working directory. {@code :xdk:test} sets the
     * working directory to the xdk project, but the second candidate keeps the test runnable from
     * the repository root.
     */
    private static final List<String> INSTALL_ROOTS =
            List.of("build/install/xdk", "xdk/build/install/xdk");

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Renders the whole constant pool; enable with RUN_INTEGRATION_TESTS=true")
    void renderingIsTextuallyIdenticalAcrossTheWholeConstantPool() {
        var runtime = new Runtime();
        runtime.start();
        try {
            ConstantPool pool =
                    new NativeContainer(runtime, installedModules()).getConstantPool();

            // snapshot first: rendering may intern, and the corpus should be stable either way
            Constant[] corpus = pool.getConstants();

            String firstPass  = sorted(render(corpus));
            String secondPass = sorted(render(corpus));

            String actual = digest(firstPass);
            assertEquals(actual, digest(secondPass),
                    "rendering the same constants twice produced different text, which means a "
                  + "display method changed what it renders as a result of having been called");

            record(actual, corpus.length, firstPass);
            if (Boolean.getBoolean("xvm.render.record")) {
                return;
            }
            if (!EXPECTED_DIGEST.isEmpty()) {
                assertEquals(EXPECTED_DIGEST, actual,
                        "the rendered text of the constant pool changed; if that was intended, "
                      + "re-record with -Dxvm.render.record=true");
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    /**
     * @return a repository over the installed distribution; the lib directory carries ecstasy and
     *         the libraries, the javatools directory carries the turtle module and the bridge
     */
    private static ModuleRepository installedModules() {
        File install = INSTALL_ROOTS.stream()
                .map(File::new)
                .filter(File::isDirectory)
                .findFirst()
                .orElseGet(() -> fail("no installed XDK found under " + INSTALL_ROOTS
                        + " (working directory " + System.getProperty("user.dir")
                        + "); run ./gradlew installDist"));

        return new LinkedRepository(Stream.of("lib", "javatools")
                .map(name -> new File(install, name))
                .map(XdkRenderEquivalenceTest::requireDirectory)
                .<ModuleRepository>map(dir -> new DirRepository(dir, true))
                .toArray(ModuleRepository[]::new));
    }

    private static File requireDirectory(File dir) {
        return dir.isDirectory()
                ? dir
                : fail("the installed XDK is incomplete: " + dir.getAbsolutePath()
                     + " is missing; run ./gradlew installDist");
    }

    /**
     * @return the rendered lines in a stable order, so that the digest depends on the text and not
     *         on the order the constants happen to occupy in the pool
     */
    private static String sorted(String text) {
        return text.lines().sorted().collect(Collectors.joining("\n"));
    }

    /**
     * @return one line per constant, so that a digest mismatch can be localized by dumping the text
     *         instead of hashing it
     */
    private static String render(Constant[] corpus) {
        var sb = new StringBuilder();
        for (Constant constant : corpus) {
            if (constant == null) {
                continue;
            }
            sb.append(constant.getClass().getSimpleName()).append('|')
              .append(safely(constant::getValueString)).append('|');
            if (constant instanceof TypeConstant type) {
                sb.append(safely(type::toString));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * A display method that throws is itself a finding, but it must not abort the sweep: the point
     * is to compare all of the text, so the throw is recorded as text and compared like the rest.
     */
    private static String safely(Rendering rendering) {
        try {
            return String.valueOf(rendering.render());
        } catch (Throwable e) {
            return "<<" + e.getClass().getSimpleName() + ">>";
        }
    }

    @FunctionalInterface
    private interface Rendering {
        String render();
    }

    /**
     * Always writes the digest out, so that comparing two builds is a file comparison rather than a
     * hunt through captured stdout. Failing to write it must not fail the test.
     */
    private static void record(String digest, int constantCount, String text) {
        try {
            Path out = Path.of("build", "render-digest.txt");
            Files.createDirectories(out.getParent());
            Files.writeString(out, digest + "  constants=" + constantCount + "\n");
            // the sorted line set, so two builds can be compared with diff and any difference
            // attributed to a specific constant rather than to an opaque hash
            Files.writeString(Path.of("build", "render-dump.txt"), text);
        } catch (IOException e) {
            // recording is a convenience, never a reason to fail
        }
    }

    private static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
