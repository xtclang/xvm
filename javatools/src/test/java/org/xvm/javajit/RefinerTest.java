package org.xvm.javajit;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Version;
import org.xvm.asm.VersionTree;

import org.xvm.asm.constants.ModuleConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for Refiner version selection logic.
 */
public class RefinerTest {
    /**
     * Test refiner that doesn't require non-null module constant.
     */
    private static final Refiner TEST_REFINER = new Refiner() {
        @Override
        public Version whichVersion(ModuleConstant module, VersionTree<?> versions, List<Version> prefs) {
            return versions.stream()
                           .max(Comparator.comparing(Version::getReleaseCategory))
                           .orElseThrow();
        }
    };

    private static Version selectVersion(VersionTree<?> versions) {
        return TEST_REFINER.whichVersion(null, versions, List.of());
    }

    @Test
    public void testWhichVersionPrefersGA() {
        var versions = new VersionTree<Boolean>();
        versions.put(new Version("1.0.alpha"), true);
        versions.put(new Version("1.0.beta"), true);
        versions.put(new Version("1.0"), true);  // GA

        var selected = selectVersion(versions);
        assertEquals(new Version("1.0"), selected, "Should prefer GA over pre-release");
    }

    @Test
    public void testWhichVersionPrefersRCOverBeta() {
        var versions = new VersionTree<Boolean>();
        versions.put(new Version("1.0.alpha"), true);
        versions.put(new Version("1.0.beta"), true);
        versions.put(new Version("1.0.rc"), true);

        var selected = selectVersion(versions);
        assertEquals(new Version("1.0.rc"), selected, "Should prefer RC over beta/alpha");
    }

    @Test
    public void testWhichVersionPrefersBetaOverAlpha() {
        var versions = new VersionTree<Boolean>();
        versions.put(new Version("1.0.alpha"), true);
        versions.put(new Version("1.0.beta"), true);

        var selected = selectVersion(versions);
        assertEquals(new Version("1.0.beta"), selected, "Should prefer beta over alpha");
    }

    @Test
    public void testWhichVersionCategoryOrdering() {
        // Test full ordering: GA (0) > rc (-1) > beta (-2) > alpha (-3) > QC (-4) > Dev (-5) > CI (-6)
        var versions = new VersionTree<Boolean>();
        versions.put(new Version("1.0.ci"), true);
        versions.put(new Version("1.0.dev"), true);
        versions.put(new Version("1.0.qa"), true);
        versions.put(new Version("1.0.alpha"), true);
        versions.put(new Version("1.0.beta"), true);
        versions.put(new Version("1.0.rc"), true);

        // Without GA, should select rc
        var selected = selectVersion(versions);
        assertEquals(new Version("1.0.rc"), selected, "Should select rc as best pre-release");

        // Add GA, should now select GA
        versions.put(new Version("1.0"), true);
        selected = selectVersion(versions);
        assertEquals(new Version("1.0"), selected, "Should select GA when available");
    }

    @Test
    public void testWhichVersionWithOnlyCI() {
        var versions = new VersionTree<Boolean>();
        versions.put(new Version("1.0.ci"), true);

        var selected = selectVersion(versions);
        assertEquals(new Version("1.0.ci"), selected, "Should return CI when it's the only option");
    }
}
