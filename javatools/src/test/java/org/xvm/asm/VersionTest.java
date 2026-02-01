package org.xvm.asm;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Version and related functionality.
 */
public class VersionTest {
    @Test
    public void testSubstitutables() {
        // Pairs where first isSubstitutableFor second
        Stream.of(
                "0:beta", "1:beta", "0:0", "1:0", "1:1", "1.2.3.4.5:1",
                "2.1.rc:2.1.beta3", "2.1.rc2:2.1.beta3", "2.1:2.1.beta", "2.1:2.1.beta3",
                "2.2:2.1.beta3", "2.1.beta3:2.1.beta", "2.1.beta3:2.1.beta3",
                "1.2:1.beta", "1.2.alpha:1.beta", "1.2.beta:1.beta", "1.2.beta1:1.beta",
                "1.2:1.beta1", "1.2.alpha:1.beta1", "1.2.beta1:1.beta1",
                "1.2.beta2:1.2.beta1", "1.2.beta:1.2.alpha", "beta:alpha"
        ).forEach(pair -> {
            String[] parts = pair.split(":");
            assertTrue(Version.of(parts[0]).isSubstitutableFor(Version.of(parts[1])),
                    () -> parts[0] + " should be substitutable for " + parts[1]);
        });

        // Pairs where first is NOT substitutable for second
        Stream.of(
                "beta:0", "beta:1", "0:1", "1:1.2.3.4.5",
                "2.1.beta3:2.1.rc", "2.1.beta3:2.1.rc2", "2.1.beta:2.1", "2.1.beta3:2.1",
                "2.1.beta3:2.2", "2.1.beta:2.1.beta3",
                "1.beta:1.2", "1.beta:1.2.alpha", "1.beta:1.2.beta", "1.beta:1.2.beta1",
                "1.beta1:1.2", "1.beta1:1.2.alpha", "1.beta1:1.2.beta1",
                "1.2.beta1:1.2.beta2", "1.2.alpha:1.2.beta", "alpha:beta"
        ).forEach(pair -> {
            String[] parts = pair.split(":");
            assertFalse(Version.of(parts[0]).isSubstitutableFor(Version.of(parts[1])),
                    () -> parts[0] + " should NOT be substitutable for " + parts[1]);
        });
    }

    @Test
    public void testEmptyTree() {
        var tree = new VersionTree<String>();
        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());
        assertFalse(tree.iterator().hasNext());
    }

    @Test
    public void testDefaultTree() {
        var tree = genTree();
        assertFalse(tree.isEmpty());
        assertEquals(6, tree.size());
    }

    @Test
    public void testDefaultTreeIterator() {
        var tree = genTree();
        assertIteratorContains(tree, "1.0", "2.0", "2.1", "2.2", "2.2.0.1", "3.0");
    }

    @Test
    public void testDefaultTreeSnipe() {
        var tree = genTree();
        tree.remove(Version.of("2.2"));

        assertFalse(tree.isEmpty());
        assertEquals(5, tree.size());
        assertIteratorContains(tree, "1.0", "2.0", "2.1", "2.2.0.1", "3.0");
    }

    @Test
    public void testDefaultTreePrune() {
        var tree = genTree();
        tree.remove(Version.of("2.2.0.1"));

        assertFalse(tree.isEmpty());
        assertEquals(5, tree.size());
        assertIteratorContains(tree, "1.0", "2.0", "2.1", "2.2", "3.0");
    }

    @Test
    public void testDefaultTreeClear() {
        var tree = genTree();
        tree.removeAll("1.0", "2.0", "2.1", "2.2", "2.2.0.1", "3.0");

        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());
        assertFalse(tree.iterator().hasNext());
    }

    @Test
    public void testDefaultTreePlus() {
        var tree = genTree();
        tree.put("2.0", "overwrite 2.0");
        tree.put("3.1", "three-one");

        assertFalse(tree.isEmpty());
        assertEquals(7, tree.size());
        assertIteratorContains(tree, "1.0", "2.0", "2.1", "2.2", "2.2.0.1", "3.0", "3.1");
    }

    @Test
    public void testDefaultSubTree() {
        var tree = genTree().subTree(Version.of("2"));
        assertFalse(tree.isEmpty());
        assertEquals(4, tree.size());
        assertIteratorContains(tree, "2.0", "2.1", "2.2", "2.2.0.1");
    }

    @Test
    public void testDefaultSubTreeEmpty() {
        var tree = genTree().subTree(Version.of("2.3"));
        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());
        assertFalse(tree.iterator().hasNext());
    }

    @Test
    public void testClosestVersion() {
        var tree = new VersionTree<String>();
        tree.put("1"          , "1"          );
        tree.put("2"          , "2"          );
        tree.put("2.0"        , "2.0"        );
        tree.put("2.1"        , "2.1"        );
        tree.put("2.1.0"      , "2.1.0"      );
        tree.put("2.1.0.0"    , "2.1.0.0"    );
        tree.put("2.1.0.1"    , "2.1.0.1"    );
        tree.put("2.1.0.1.0"  , "2.1.0.1.0"  );
        tree.put("2.1.0.1.0.0", "2.1.0.1.beta");
        tree.put("2.1.0.1.0.0", "2.1.0.1.0.0");
        tree.put("2.2"        , "2.2"        );
        tree.put("4"          , "4"          );

        assertNull(tree.findClosestVersion(Version.of("beta")));
        assertNull(tree.findClosestVersion(Version.of("beta2")));
        assertEquals(Version.of("1"          ), tree.findClosestVersion(Version.of("1")));
        assertEquals(Version.of("2.0"        ), tree.findClosestVersion(Version.of("2")));
        assertEquals(Version.of("2"          ), tree.findClosestVersion(Version.of("3")));
        assertEquals(Version.of("4"          ), tree.findClosestVersion(Version.of("4")));
        assertEquals(Version.of("4"          ), tree.findClosestVersion(Version.of("5")));
        assertEquals(Version.of("1"          ), tree.findClosestVersion(Version.of("1.5")));
        assertEquals(Version.of("1"          ), tree.findClosestVersion(Version.of("2.beta")));
        assertEquals(Version.of("2.0"        ), tree.findClosestVersion(Version.of("2.1.beta")));
        assertEquals(Version.of("2.1.0.0"    ), tree.findClosestVersion(Version.of("2.1")));
        assertEquals(Version.of("2.1.0.0"    ), tree.findClosestVersion(Version.of("2.1.0")));
        assertEquals(Version.of("2.1.0"      ), tree.findClosestVersion(Version.of("2.1.1")));
        assertEquals(Version.of("2.1.0.1.0.0"), tree.findClosestVersion(Version.of("2.1.0.1")));
        assertEquals(Version.of("2.1.0.1.0"  ), tree.findClosestVersion(Version.of("2.1.0.1.1")));
        assertEquals(Version.of("2.1.0.1"    ), tree.findClosestVersion(Version.of("2.1.0.2")));
        assertEquals(Version.of("2.2"        ), tree.findClosestVersion(Version.of("2.5.1.3")));
    }

    @Test
    public void testHighestVersion() {
        var tree = genTree();
        assertEquals(Version.of("3.0"), tree.findHighestVersion());
        assertEquals(Version.of("3.0"), tree.findHighestVersion(Version.of("3.0.0.0")));
        assertEquals(Version.of("2.1"), tree.findHighestVersion(Version.of("2.1.0")));
        assertEquals(Version.of("2.2.0.1"), tree.findHighestVersion(Version.of("2.1")));
    }

    @Test
    public void testBuildString() {
        assertTrue(Version.of("1.2.3").isSameAs(Version.of("1.2.3+this-is.a-Build.string-4.5.6")));
    }

    @Test
    public void testMnemonics() {
        // Pairs of versions that should be equivalent (isSameAs)
        Stream.of(
                "1.2.3.alpha:1.2.3.Alpha", "1.2.3.alpha:1.2.3.A", "1.2.3.alpha:1.2.3.a",
                "1.2.3.alpha:1.2.3.aLpHa", "1.2.3.alpha2:1.2.3.Alpha2", "1.2.3.alpha3:1.2.3.A3",
                "1.2.3.alpha4:1.2.3.a4", "1.2.3.alpha5:1.2.3.aLpHa5", "1.2.3.alpha2:1.2.3.alpha.2",
                "1.2.3.alpha2:1.2.3.Alpha.2", "1.2.3.alpha3:1.2.3.A.3", "1.2.3.alpha4:1.2.3.a.4",
                "1.2.3.alpha5:1.2.3.aLpHa.5", "1.2.beta3:1.2.B3", "1.2.3rc:1.2.3R",
                "ci:C", "1.2.qa3:1.2.Q-3"
        ).forEach(pair -> {
            String[] parts = pair.split(":");
            assertTrue(Version.of(parts[0]).isSameAs(Version.of(parts[1])),
                    () -> parts[0] + " should be same as " + parts[1]);
        });
    }

    @Test
    public void testBadVersions() {
        Stream.of("", "1.", ".1", "1.alpha.beta", "1.0alph", "1.0be", "1.0+^", "1.2.3B4+build!12345")
                .forEach(bad -> assertThrows(IllegalStateException.class, () -> Version.of(bad)));
    }

    static VersionTree<String> genTree() {
        var tree = new VersionTree<String>();
        tree.put("1.0", "one-oh");
        tree.put("2.0", "two-oh");
        tree.put("2.1", "two-one");
        tree.put("2.2", "two-two");
        tree.put("2.2.0.1", "two-two-oh-one");
        tree.put("3.0", "three-oh");
        return tree;
    }

    // ----- ReleaseCategory tests -----------------------------------------------------------------

    @Test
    public void testReleaseCategoryEnum() {
        // Test enum ordering (least stable to most stable)
        assertTrue(Version.ReleaseCategory.CI.compareTo(Version.ReleaseCategory.GA) < 0);
        assertTrue(Version.ReleaseCategory.DEV.compareTo(Version.ReleaseCategory.GA) < 0);
        assertTrue(Version.ReleaseCategory.ALPHA.compareTo(Version.ReleaseCategory.BETA) < 0);
        assertTrue(Version.ReleaseCategory.BETA.compareTo(Version.ReleaseCategory.RC) < 0);
        assertTrue(Version.ReleaseCategory.RC.compareTo(Version.ReleaseCategory.GA) < 0);

        // Test isPreRelease
        assertTrue(Version.ReleaseCategory.CI.isPreRelease());
        assertTrue(Version.ReleaseCategory.DEV.isPreRelease());
        assertTrue(Version.ReleaseCategory.QA.isPreRelease());
        assertTrue(Version.ReleaseCategory.ALPHA.isPreRelease());
        assertTrue(Version.ReleaseCategory.BETA.isPreRelease());
        assertTrue(Version.ReleaseCategory.RC.isPreRelease());
        assertFalse(Version.ReleaseCategory.GA.isPreRelease());

        // Test fromCode
        assertEquals(Version.ReleaseCategory.CI, Version.ReleaseCategory.fromCode(-6));
        assertEquals(Version.ReleaseCategory.DEV, Version.ReleaseCategory.fromCode(-5));
        assertEquals(Version.ReleaseCategory.QA, Version.ReleaseCategory.fromCode(-4));
        assertEquals(Version.ReleaseCategory.ALPHA, Version.ReleaseCategory.fromCode(-3));
        assertEquals(Version.ReleaseCategory.BETA, Version.ReleaseCategory.fromCode(-2));
        assertEquals(Version.ReleaseCategory.RC, Version.ReleaseCategory.fromCode(-1));
        assertEquals(Version.ReleaseCategory.GA, Version.ReleaseCategory.fromCode(0));
        assertNull(Version.ReleaseCategory.fromCode(-7));
        assertNull(Version.ReleaseCategory.fromCode(1));

        // Test fromChar (only for pre-release)
        assertEquals(Version.ReleaseCategory.CI, Version.ReleaseCategory.fromChar('c'));
        assertEquals(Version.ReleaseCategory.CI, Version.ReleaseCategory.fromChar('C'));
        assertEquals(Version.ReleaseCategory.DEV, Version.ReleaseCategory.fromChar('d'));
        assertEquals(Version.ReleaseCategory.QA, Version.ReleaseCategory.fromChar('q'));
        assertEquals(Version.ReleaseCategory.ALPHA, Version.ReleaseCategory.fromChar('a'));
        assertEquals(Version.ReleaseCategory.BETA, Version.ReleaseCategory.fromChar('b'));
        assertEquals(Version.ReleaseCategory.RC, Version.ReleaseCategory.fromChar('r'));
        assertNull(Version.ReleaseCategory.fromChar('g')); // GA not parseable from char
        assertNull(Version.ReleaseCategory.fromChar('x'));
    }

    @Test
    public void testVersionReleaseCategory() {
        assertEquals(Version.ReleaseCategory.GA, Version.of("1.0").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.GA, Version.of("2.3.4").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, Version.of("1.0.alpha").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, Version.of("1.0.alpha2").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, Version.of("1.0.beta").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, Version.of("2.1.beta3").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, Version.of("1.0.rc").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, Version.of("3.0.rc1").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, Version.of("dev").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, Version.of("1.0.dev").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.CI, Version.of("ci").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.QA, Version.of("1.0.qa").getReleaseCategory());

        // Test getReleaseCategoryString (display format from master)
        assertEquals("GA", Version.of("1.0").getReleaseCategoryString());
        assertEquals("alpha", Version.of("1.0.alpha").getReleaseCategoryString());
        assertEquals("beta", Version.of("1.0.beta").getReleaseCategoryString());
        assertEquals("rc", Version.of("1.0.rc").getReleaseCategoryString());
        assertEquals("Dev", Version.of("1.0.dev").getReleaseCategoryString());
        assertEquals("CI", Version.of("ci").getReleaseCategoryString());
        assertEquals("QA", Version.of("1.0.qa").getReleaseCategoryString());
    }

    // ----- VersionTree with pre-release versions ------------------------------------------------

    @Test
    public void testVersionTreeWithPreRelease() {
        var tree = new VersionTree<String>();
        tree.put("1.0.alpha", "alpha");
        tree.put("1.0.beta", "beta");
        tree.put("1.0.rc", "rc");
        tree.put("1.0", "ga");

        assertEquals(4, tree.size());
        assertTrue(tree.contains(Version.of("1.0.alpha")));
        assertTrue(tree.contains(Version.of("1.0.beta")));
        assertTrue(tree.contains(Version.of("1.0.rc")));
        assertTrue(tree.contains(Version.of("1.0")));

        // Test iteration order
        var iter = tree.iterator();
        assertEquals(Version.of("1.0"), iter.next());          // GA comes first (lower parts)
        assertEquals(Version.of("1.0.alpha"), iter.next());    // Then alpha (-3)
        assertEquals(Version.of("1.0.beta"), iter.next());     // Then beta (-2)
        assertEquals(Version.of("1.0.rc"), iter.next());       // Then rc (-1)
    }

    @Test
    public void testFindHighestVersionPrefersGA() {
        var tree = new VersionTree<String>();
        tree.put("1.0.alpha", "alpha");
        tree.put("1.0.beta", "beta");
        tree.put("1.0.rc", "rc");
        tree.put("1.0", "ga");

        // Should prefer GA over pre-release
        assertEquals(Version.of("1.0"), tree.findHighestVersion());
    }

    @Test
    public void testFindHighestVersionWithOnlyPreRelease() {
        var tree = new VersionTree<String>();
        tree.put("1.0.alpha", "alpha");
        tree.put("1.0.beta", "beta");
        tree.put("1.0.rc", "rc");

        // Should prefer RC (most stable pre-release)
        assertEquals(Version.of("1.0.rc"), tree.findHighestVersion());
    }

    @Test
    public void testFindHighestVersionMixedMajorVersions() {
        var tree = new VersionTree<String>();
        tree.put("1.0", "1.0 ga");
        tree.put("2.0.beta", "2.0 beta");

        // Should prefer 1.0 GA over 2.0 beta
        assertEquals(Version.of("1.0"), tree.findHighestVersion());
    }

    @Test
    public void testFindHighestVersionWithConstraint() {
        var tree = new VersionTree<String>();
        tree.put("1.0", "1.0");
        tree.put("1.1.alpha", "1.1 alpha");
        tree.put("1.1.beta", "1.1 beta");
        tree.put("1.1", "1.1");
        tree.put("2.0.alpha", "2.0 alpha");

        // Find highest >= 1.1
        assertEquals(Version.of("1.1"), tree.findHighestVersion(Version.of("1.1")));

        // Find highest >= 2.0 (only alpha available)
        assertEquals(Version.of("2.0.alpha"), tree.findHighestVersion(Version.of("2.0")));
    }

    @Test
    public void testClosestVersionWithPreRelease() {
        var tree = new VersionTree<String>();
        tree.put("1.0", "1.0");
        tree.put("1.1.beta", "1.1 beta");
        tree.put("1.1", "1.1");
        tree.put("2.0", "2.0");

        // Looking for 1.1.alpha: beta (-2) > alpha (-3) in tree order, so falls back to 1.0
        assertEquals(Version.of("1.0"), tree.findClosestVersion(Version.of("1.1.alpha")));

        // Looking for 1.1.rc: beta (-2) < rc (-1) in tree order, so finds beta as closest predecessor
        // Note: This is tree proximity, not substitutability - beta is NOT substitutable for rc
        assertEquals(Version.of("1.1.beta"), tree.findClosestVersion(Version.of("1.1.rc")));

        // Looking for 1.2 should find 1.1
        assertEquals(Version.of("1.1"), tree.findClosestVersion(Version.of("1.2")));
    }

    // ----- findLowestSubstitutable tests ---------------------------------------------------------

    @Test
    public void testLowestSubstitutable() {
        var tree = new VersionTree<String>();
        tree.put("1.0", "1.0");
        tree.put("2.0", "2.0");
        tree.put("2.1", "2.1");
        tree.put("2.2", "2.2");
        tree.put("3.0", "3.0");

        // Exact match
        assertEquals(Version.of("2.0"), tree.findLowestSubstitutable(Version.of("2.0")));

        // Same version (normalized)
        assertEquals(Version.of("2.0"), tree.findLowestSubstitutable(Version.of("2.0.0")));

        // 2.0 is substitutable for 2 (derives from it)
        assertEquals(Version.of("2.0"), tree.findLowestSubstitutable(Version.of("2")));

        // Nothing substitutable for 4.0
        assertNull(tree.findLowestSubstitutable(Version.of("4.0")));

        // 2.0 is NOT substitutable for 1.5 (different branch)
        // The lowest substitutable would be... nothing, since nothing derives from 1.5
        assertNull(tree.findLowestSubstitutable(Version.of("1.5")));
    }

    @Test
    public void testLowestSubstitutableWithPreRelease() {
        var tree = new VersionTree<String>();
        tree.put("1.0.beta", "1.0.beta");
        tree.put("1.0.rc", "1.0.rc");
        tree.put("1.0", "1.0");

        // Tree iteration order: 1.0, 1.0.beta, 1.0.rc
        // GA (1.0) is substitutable for all pre-release versions of 1.0.x
        // So findLowestSubstitutable returns the lowest, which is 1.0
        assertEquals(Version.of("1.0"), tree.findLowestSubstitutable(Version.of("1.0.beta")));
        assertEquals(Version.of("1.0"), tree.findLowestSubstitutable(Version.of("1.0.rc")));
        assertEquals(Version.of("1.0"), tree.findLowestSubstitutable(Version.of("1.0")));
        assertEquals(Version.of("1.0"), tree.findLowestSubstitutable(Version.of("1.0.alpha")));

        // When there's only pre-release versions
        var preReleaseTree = new VersionTree<String>();
        preReleaseTree.put("1.0.beta", "1.0.beta");
        preReleaseTree.put("1.0.rc", "1.0.rc");

        // beta is substitutable for alpha (comes after alpha in release cycle)
        assertEquals(Version.of("1.0.beta"), preReleaseTree.findLowestSubstitutable(Version.of("1.0.alpha")));
        // rc is substitutable for beta (comes after beta)
        assertEquals(Version.of("1.0.beta"), preReleaseTree.findLowestSubstitutable(Version.of("1.0.beta")));
        assertEquals(Version.of("1.0.rc"), preReleaseTree.findLowestSubstitutable(Version.of("1.0.rc")));
    }

    // ----- Version resolution tests (for ModuleRepository use case) ------------------------------

    @Test
    public void testVersionResolutionExactMatch() {
        var tree = new VersionTree<String>();
        tree.put("1.0", "1.0");
        tree.put("2.0", "2.0");

        // When requested version exists exactly
        assertEquals(Version.of("2.0"), tree.findLowestSubstitutable(Version.of("2.0")));
    }

    @Test
    public void testVersionResolutionNormalizedMatch() {
        var tree = new VersionTree<String>();
        tree.put("2.0", "2.0");

        // 2.0 and 2.0.0 are the "same" version
        assertEquals(Version.of("2.0"), tree.findLowestSubstitutable(Version.of("2.0.0")));
    }

    @Test
    public void testVersionResolutionSubstituteWhenNotExact() {
        var tree = new VersionTree<String>();
        tree.put("2.0", "2.0");
        tree.put("2.1", "2.1");

        // 2.0 and 2.1 are both substitutable for version "2"
        // findLowestSubstitutable returns the LOWEST one that works
        assertEquals(Version.of("2.0"), tree.findLowestSubstitutable(Version.of("2")));
    }

    @Test
    public void testVersionResolutionNoMatch() {
        var tree = new VersionTree<String>();
        tree.put("1.0", "1.0");

        // Nothing substitutable for 2.0
        assertNull(tree.findLowestSubstitutable(Version.of("2.0")));
    }

    @Test
    public void testVersionResolutionWithIsSameAs() {
        // Tests that isSameAs works correctly for normalized versions
        assertTrue(Version.of("2.0").isSameAs(Version.of("2.0.0")));
        assertTrue(Version.of("2.0.0").isSameAs(Version.of("2.0")));

        // And mutual substitutability
        assertTrue(Version.of("2.0").isSubstitutableFor(Version.of("2.0.0")));
        assertTrue(Version.of("2.0.0").isSubstitutableFor(Version.of("2.0")));
    }

    // ----- Lexer edge cases (via Version parsing) -----------------------------------------------

    @Test
    public void testAllPreReleaseCategories() {
        // Test all pre-release categories parse correctly
        assertEquals(Version.ReleaseCategory.CI, Version.of("ci").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.CI, Version.of("1.0.ci").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, Version.of("dev").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, Version.of("1.0.dev2").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.QA, Version.of("qa").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.QA, Version.of("1.0.qa1").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, Version.of("alpha").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, Version.of("1.0.alpha3").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, Version.of("beta").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, Version.of("1.0.beta4").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, Version.of("rc").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, Version.of("1.0.rc5").getReleaseCategory());
    }

    @Test
    public void testVersionNormalize() {
        assertEquals(Version.of("1"), Version.of("1.0.0.0").normalize());
        assertEquals(Version.of("1.2"), Version.of("1.2.0.0").normalize());
        assertEquals(Version.of("1.2.3"), Version.of("1.2.3.0").normalize());
        assertEquals(Version.of("1.2.3"), Version.of("1.2.3").normalize());

        // Pre-release versions
        assertEquals(Version.of("1.beta"), Version.of("1.beta").normalize());
        assertEquals(Version.of("1.beta2"), Version.of("1.beta2").normalize());
    }

    @Test
    public void testVersionComparison() {
        // GA versions compare by numeric parts
        assertTrue(Version.of("1.0").compareTo(Version.of("2.0")) < 0);
        assertTrue(Version.of("1.1").compareTo(Version.of("1.2")) < 0);
        assertTrue(Version.of("1.0.0").compareTo(Version.of("1.0.1")) < 0);

        // Pre-release codes are negative, so alpha (-3) < beta (-2) < rc (-1)
        assertTrue(Version.of("1.0.alpha").compareTo(Version.of("1.0.beta")) < 0);
        assertTrue(Version.of("1.0.beta").compareTo(Version.of("1.0.rc")) < 0);

        // Note: compareTo is lexicographic by parts, not semantic stability ordering.
        // "1.0.rc" has 3 parts [1, 0, -1], "1.0" has 2 parts [1, 0].
        // After comparing shared parts (equal), the longer version is "greater".
        // Use isGARelease() and getReleaseCategory() for stability comparison.
        assertTrue(Version.of("1.0.rc").compareTo(Version.of("1.0")) > 0);
        assertTrue(Version.of("1.0").compareTo(Version.of("1.0.rc")) < 0);

        // But rc is still a pre-release
        assertFalse(Version.of("1.0.rc").isGARelease());
        assertTrue(Version.of("1.0").isGARelease());

        // And GA is more stable than RC
        assertTrue(Version.of("1.0").getReleaseCategory().compareTo(
                Version.of("1.0.rc").getReleaseCategory()) > 0);
    }

    static void assertIteratorContains(VersionTree<?> tree, String... expectedVersions) {
        List<Version> expected = Stream.of(expectedVersions).map(Version::of).toList();
        List<Version> actual = tree.stream().toList();
        assertEquals(expected, actual);
    }

    static void out() {
        out("");
    }

    static void out(Object o) {
        System.out.println(o);
    }
}
