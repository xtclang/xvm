package org.xvm.tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sanity checks for the versions written into a generated {@code gradle/libs.versions.toml}.
 *
 * <p>Regression coverage for the auto-updated IntelliJ/VS Code plugin baking its own
 * publication build-id (base + UTC timestamp, e.g. {@code 0.4.4-SNAPSHOT.20260526090539})
 * into the catalog, which is not a resolvable Maven/Gradle coordinate. The catalog must only
 * ever pin a real XTC version: {@code MAJOR.MINOR.PATCH} optionally with {@code -SNAPSHOT},
 * {@code -alpha}, or {@code -beta} -- and nothing else. Both the snapshot dependency and a
 * "real" (released) dependency must work.
 */
class XtcVersionSanityTest {

    private static final Pattern CATALOG_XTC_VERSION = Pattern.compile("xtc = \"([^\"]+)\"");

    // ---- version validation: real release AND snapshot are both valid ----

    @Test
    void acceptsRealReleaseAndSnapshotVersions() {
        for (var v : new String[] {"0.4.4", "0.4.5", "0.4.5-SNAPSHOT", "0.4.4-SNAPSHOT", "0.4.4-alpha", "0.4.4-beta"}) {
            assertTrue(XtcProjectCreator.isValidXtcVersion(v), v);
        }
    }

    @Test
    void rejectsTimestampedAndMalformedVersions() {
        for (var v : new String[] {
                "0.4.4-SNAPSHOT.20260526090539", // JetBrains Marketplace upload id
                "0.4.4-alpha.20260523T120000",   // VS Code packaging id
                "0.4.4-20260529.201252-1",       // Maven unique-snapshot backing version
                "0.4.4-rc1",
                "0.4",
                "",
                "garbage"}) {
            assertFalse(XtcProjectCreator.isValidXtcVersion(v), v);
        }
        assertFalse(XtcProjectCreator.isValidXtcVersion(null), "null");
    }

    // ---- sanitization ----

    @Test
    void stripsPublicationBuildSuffixToResolvableCoordinate() {
        assertEquals("0.4.4-SNAPSHOT", XtcProjectCreator.sanitizeXtcVersion("0.4.4-SNAPSHOT.20260526090539"));
        assertEquals("0.4.4-alpha", XtcProjectCreator.sanitizeXtcVersion("0.4.4-alpha.20260523T120000"));
        assertEquals("0.4.4", XtcProjectCreator.sanitizeXtcVersion("0.4.4-20260529.201252-1"));
    }

    @Test
    void leavesRealSnapshotAndReleaseVersionsUntouched() {
        assertEquals("0.4.4-SNAPSHOT", XtcProjectCreator.sanitizeXtcVersion("0.4.4-SNAPSHOT"));
        assertEquals("0.4.5", XtcProjectCreator.sanitizeXtcVersion("0.4.5"));
        assertEquals("0.4.4-alpha", XtcProjectCreator.sanitizeXtcVersion("0.4.4-alpha"));
    }

    @Test
    void fallsBackToDefaultForUnusableInput() {
        for (var v : new String[] {null, "", "   ", "garbage", "not-a-version"}) {
            assertEquals(XtcProjectCreator.DEFAULT_XTC_VERSION, XtcProjectCreator.sanitizeXtcVersion(v), String.valueOf(v));
        }
    }

    @Test
    void sanitizeIsIdempotentAndAlwaysValid() {
        // Idempotency is what makes the generated catalog stable across CI reruns / republishes:
        // no matter how many times a decorated version flows through, the pinned coordinate is the same.
        for (var v : new String[] {
                "0.4.4-SNAPSHOT.20260526090539", "0.4.4-alpha.20260523T120000",
                "0.4.4-20260529.201252-1", "0.4.4-SNAPSHOT", "0.4.5", "garbage", null}) {
            var once = XtcProjectCreator.sanitizeXtcVersion(v);
            var twice = XtcProjectCreator.sanitizeXtcVersion(once);
            assertEquals(once, twice, "sanitize not idempotent for " + v);
            assertTrue(XtcProjectCreator.isValidXtcVersion(once), "sanitize produced invalid version for " + v);
        }
    }

    // ---- generated catalog sanity check (the actual libs.versions.toml) ----

    @Test
    void generatedCatalogStripsMarketplaceTimestampForSnapshot() throws Exception {
        var catalog = catalogFor("0.4.4-SNAPSHOT.20260526090539");
        assertTrue(catalog.contains("xtc = \"0.4.4-SNAPSHOT\""), catalog);
        assertFalse(catalog.contains("20260526090539"), catalog);
    }

    @Test
    void generatedCatalogKeepsRealReleaseVersion() throws Exception {
        var catalog = catalogFor("0.4.5");
        assertTrue(catalog.contains("xtc = \"0.4.5\""), catalog);
    }

    @Test
    void generatedCatalogNeverPinsNonResolvableVersion() throws Exception {
        for (var raw : new String[] {
                "0.4.4-SNAPSHOT.20260526090539", "0.4.4-alpha.20260523T120000",
                "0.4.4-20260529.201252-1", "0.4.5-SNAPSHOT", "0.4.5", "garbage"}) {
            var pinned = pinnedXtcVersion(catalogFor(raw));
            assertNotNull(pinned, "no xtc version pinned for input '" + raw + "'");
            assertTrue(XtcProjectCreator.isValidXtcVersion(pinned),
                "catalog pinned non-resolvable version '" + pinned + "' for input '" + raw + "'");
        }
    }

    // ---- gradle wrapper sourced verbatim from the composite build (no hardcoded drift) ----

    @Test
    void generatedWrapperMatchesBundledCompositeWrapper() throws Exception {
        String bundled;
        try (var in =
                XtcProjectCreator.class.getResourceAsStream("/gradle-wrapper/gradle/wrapper/gradle-wrapper.properties")) {
            assumeTrue(in != null, "bundled gradle-wrapper.properties not on the test classpath");
            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        var projectPath = newProjectDir();
        var result = new XtcProjectCreator(projectPath, XtcProjectCreator.ProjectType.APPLICATION, false).create();
        assertTrue(result.success(), result.message());

        var generated = Files.readString(projectPath.resolve("gradle/wrapper/gradle-wrapper.properties"));
        assertEquals(bundled, generated, "generated wrapper must match the composite build's bundled wrapper verbatim");
    }

    // ---- helpers ----

    private static String catalogFor(String xtcVersion) throws Exception {
        var projectPath = newProjectDir();
        var creator =
            new XtcProjectCreator(projectPath, XtcProjectCreator.ProjectType.APPLICATION, false, xtcVersion, null);
        var result = creator.create();
        assertTrue(result.success(), String.valueOf(xtcVersion) + ": " + result.message());
        return Files.readString(projectPath.resolve("gradle/libs.versions.toml"));
    }

    /** A fresh, empty project directory with a module-name-safe leaf (no hyphens). */
    private static Path newProjectDir() throws Exception {
        return Files.createTempDirectory("xtctest").resolve("app");
    }

    private static String pinnedXtcVersion(String catalog) {
        var matcher = CATALOG_XTC_VERSION.matcher(catalog);
        return matcher.find() ? matcher.group(1) : null;
    }
}
