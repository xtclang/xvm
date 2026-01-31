package org.xvm.asm;

import java.util.Iterator;

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
        assertTrue(new Version("0"        ).isSubstitutableFor(new Version("beta"     )));
        assertTrue(new Version("1"        ).isSubstitutableFor(new Version("beta"     )));
        assertTrue(new Version("0"        ).isSubstitutableFor(new Version("0"        )));
        assertTrue(new Version("1"        ).isSubstitutableFor(new Version("0"        )));
        assertTrue(new Version("1"        ).isSubstitutableFor(new Version("1"        )));
        assertTrue(new Version("1.2.3.4.5").isSubstitutableFor(new Version("1"        )));
        assertTrue(new Version("2.1.rc"   ).isSubstitutableFor(new Version("2.1.beta3")));
        assertTrue(new Version("2.1.rc2"  ).isSubstitutableFor(new Version("2.1.beta3")));
        assertTrue(new Version("2.1"      ).isSubstitutableFor(new Version("2.1.beta" )));
        assertTrue(new Version("2.1"      ).isSubstitutableFor(new Version("2.1.beta3")));
        assertTrue(new Version("2.2"      ).isSubstitutableFor(new Version("2.1.beta3")));
        assertTrue(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.beta" )));
        assertTrue(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.beta3")));
        assertTrue(new Version("1.2"      ).isSubstitutableFor(new Version("1.beta"   )));
        assertTrue(new Version("1.2.alpha").isSubstitutableFor(new Version("1.beta"   )));
        assertTrue(new Version("1.2.beta" ).isSubstitutableFor(new Version("1.beta"   )));
        assertTrue(new Version("1.2.beta1").isSubstitutableFor(new Version("1.beta"   )));
        assertTrue(new Version("1.2"      ).isSubstitutableFor(new Version("1.beta1"  )));
        assertTrue(new Version("1.2.alpha").isSubstitutableFor(new Version("1.beta1"  )));
        assertTrue(new Version("1.2.beta1").isSubstitutableFor(new Version("1.beta1"  )));
        assertTrue(new Version("1.2.beta2").isSubstitutableFor(new Version("1.2.beta1")));
        assertTrue(new Version("1.2.beta" ).isSubstitutableFor(new Version("1.2.alpha")));
        assertTrue(new Version("beta"     ).isSubstitutableFor(new Version("alpha"    )));

        assertFalse(new Version("beta").isSubstitutableFor(new Version("0")));
        assertFalse(new Version("beta").isSubstitutableFor(new Version("1")));
        assertFalse(new Version("0").isSubstitutableFor(new Version("1")));
        assertFalse(new Version("1").isSubstitutableFor(new Version("1.2.3.4.5")));
        assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.rc")));
        assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.rc2")));
        assertFalse(new Version("2.1.beta").isSubstitutableFor(new Version("2.1")));
        assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1")));
        assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.2")));
        assertFalse(new Version("2.1.beta").isSubstitutableFor(new Version("2.1.beta3")));
        assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2")));
        assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2.alpha")));
        assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2.beta")));
        assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2.beta1")));
        assertFalse(new Version("1.beta1").isSubstitutableFor(new Version("1.2")));
        assertFalse(new Version("1.beta1").isSubstitutableFor(new Version("1.2.alpha")));
        assertFalse(new Version("1.beta1").isSubstitutableFor(new Version("1.2.beta1")));
        assertFalse(new Version("1.2.beta1").isSubstitutableFor(new Version("1.2.beta2")));
        assertFalse(new Version("1.2.alpha").isSubstitutableFor(new Version("1.2.beta")));
        assertFalse(new Version("alpha").isSubstitutableFor(new Version("beta")));
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
        VersionTree<String> tree = genTree();
        assertFalse(tree.isEmpty());
        assertEquals(6, tree.size());
    }

    @Test
    public void testDefaultTreeIterator() {
        VersionTree<String> tree = genTree();
        Iterator<Version> iter = tree.iterator();
        assertTrue(iter.hasNext());
        assertEquals(new Version("1.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2.0.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("3.0"), iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testDefaultTreeSnipe() {
        VersionTree<String> tree = genTree();
        tree.remove(new Version("2.2"));

        assertFalse(tree.isEmpty());
        assertEquals(5, tree.size());

        Iterator<Version> iter = tree.iterator();
        assertTrue(iter.hasNext());
        assertEquals(new Version("1.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2.0.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("3.0"), iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testDefaultTreePrune() {
        VersionTree<String> tree = genTree();
        tree.remove(new Version("2.2.0.1"));

        assertFalse(tree.isEmpty());
        assertEquals(5, tree.size());

        Iterator<Version> iter = tree.iterator();
        assertTrue(iter.hasNext());
        assertEquals(new Version("1.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("3.0"), iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testDefaultTreeClear() {
        VersionTree<String> tree = genTree();

        tree.remove(new Version("1.0"));
        tree.remove(new Version("2.0"));
        tree.remove(new Version("2.1"));
        tree.remove(new Version("2.2"));
        tree.remove(new Version("2.2.0.1"));
        tree.remove(new Version("3.0"));

        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());
        assertFalse(tree.iterator().hasNext());
    }

    @Test
    public void testDefaultTreePlus() {
        VersionTree<String> tree = genTree();
        tree.put(new Version("2.0"), "overwrite 2.0");
        tree.put(new Version("3.1"), "three-one");

        assertFalse(tree.isEmpty());
        assertEquals(7, tree.size());

        Iterator<Version> iter = tree.iterator();
        assertTrue(iter.hasNext());
        assertEquals(new Version("1.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2.0.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("3.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("3.1"), iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testDefaultSubTree() {
        VersionTree<String> tree = genTree().subTree(new Version("2"));
        assertFalse(tree.isEmpty());
        assertEquals(4, tree.size());
        Iterator<Version> iter = tree.iterator();
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.0"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.1"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2"), iter.next());
        assertTrue(iter.hasNext());
        assertEquals(new Version("2.2.0.1"), iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testDefaultSubTreeEmpty() {
        VersionTree<String> tree = genTree().subTree(new Version("2.3"));
        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());
        assertFalse(tree.iterator().hasNext());
    }

    @Test
    public void testClosestVersion() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1"          ), "1"          );
        tree.put(new Version("2"          ), "2"          );
        tree.put(new Version("2.0"        ), "2.0"        );
        tree.put(new Version("2.1"        ), "2.1"        );
        tree.put(new Version("2.1.0"      ), "2.1.0"      );
        tree.put(new Version("2.1.0.0"    ), "2.1.0.0"    );
        tree.put(new Version("2.1.0.1"    ), "2.1.0.1"    );
        tree.put(new Version("2.1.0.1.0"  ), "2.1.0.1.0"  );
        tree.put(new Version("2.1.0.1.0.0"), "2.1.0.1.beta");
        tree.put(new Version("2.1.0.1.0.0"), "2.1.0.1.0.0");
        tree.put(new Version("2.2"        ), "2.2"        );
        tree.put(new Version("4"          ), "4"          );

        assertNull(tree.findClosestVersion(new Version("beta")));
        assertNull(tree.findClosestVersion(new Version("beta2")));
        assertEquals(new Version("1"          ), tree.findClosestVersion(new Version("1")));
        assertEquals(new Version("2.0"        ), tree.findClosestVersion(new Version("2")));
        assertEquals(new Version("2"          ), tree.findClosestVersion(new Version("3")));
        assertEquals(new Version("4"          ), tree.findClosestVersion(new Version("4")));
        assertEquals(new Version("4"          ), tree.findClosestVersion(new Version("5")));
        assertEquals(new Version("1"          ), tree.findClosestVersion(new Version("1.5")));
        assertEquals(new Version("1"          ), tree.findClosestVersion(new Version("2.beta")));
        assertEquals(new Version("2.0"        ), tree.findClosestVersion(new Version("2.1.beta")));
        assertEquals(new Version("2.1.0.0"    ), tree.findClosestVersion(new Version("2.1")));
        assertEquals(new Version("2.1.0.0"    ), tree.findClosestVersion(new Version("2.1.0")));
        assertEquals(new Version("2.1.0"      ), tree.findClosestVersion(new Version("2.1.1")));
        assertEquals(new Version("2.1.0.1.0.0"), tree.findClosestVersion(new Version("2.1.0.1")));
        assertEquals(new Version("2.1.0.1.0"  ), tree.findClosestVersion(new Version("2.1.0.1.1")));
        assertEquals(new Version("2.1.0.1"    ), tree.findClosestVersion(new Version("2.1.0.2")));
        assertEquals(new Version("2.2"        ), tree.findClosestVersion(new Version("2.5.1.3")));
    }

    @Test
    public void testHighestVersion() {
        VersionTree<String> tree = genTree();
        assertEquals(new Version("3.0"), tree.findHighestVersion());
        assertEquals(new Version("3.0"), tree.findHighestVersion(new Version("3.0.0.0")));
        assertEquals(new Version("2.1"), tree.findHighestVersion(new Version("2.1.0")));
        assertEquals(new Version("2.2.0.1"), tree.findHighestVersion(new Version("2.1")));
    }

    @Test
    public void testBuildString() {
        assertTrue(new Version("1.2.3").isSameAs(new Version("1.2.3+this-is.a-Build.string-4.5.6")));
    }

    @Test
    public void testMnemonics() {
        assertTrue(new Version("1.2.3.alpha").isSameAs(new Version("1.2.3.Alpha")));
        assertTrue(new Version("1.2.3.alpha").isSameAs(new Version("1.2.3.A")));
        assertTrue(new Version("1.2.3.alpha").isSameAs(new Version("1.2.3.a")));
        assertTrue(new Version("1.2.3.alpha").isSameAs(new Version("1.2.3.aLpHa")));
        assertTrue(new Version("1.2.3.alpha2").isSameAs(new Version("1.2.3.Alpha2")));
        assertTrue(new Version("1.2.3.alpha3").isSameAs(new Version("1.2.3.A3")));
        assertTrue(new Version("1.2.3.alpha4").isSameAs(new Version("1.2.3.a4")));
        assertTrue(new Version("1.2.3.alpha5").isSameAs(new Version("1.2.3.aLpHa5")));
        assertTrue(new Version("1.2.3.alpha2").isSameAs(new Version("1.2.3.alpha.2")));
        assertTrue(new Version("1.2.3.alpha2").isSameAs(new Version("1.2.3.Alpha.2")));
        assertTrue(new Version("1.2.3.alpha3").isSameAs(new Version("1.2.3.A.3")));
        assertTrue(new Version("1.2.3.alpha4").isSameAs(new Version("1.2.3.a.4")));
        assertTrue(new Version("1.2.3.alpha5").isSameAs(new Version("1.2.3.aLpHa.5")));
        assertTrue(new Version("1.2.beta3").isSameAs(new Version("1.2.B3")));
        assertTrue(new Version("1.2.3rc").isSameAs(new Version("1.2.3R")));
        assertTrue(new Version("ci").isSameAs(new Version("C")));
        assertTrue(new Version("1.2.qa3").isSameAs(new Version("1.2.Q-3")));
    }

    @Test
    public void testBadVersions() {
        assertThrows(IllegalStateException.class, () -> { new Version(""); });
        assertThrows(IllegalStateException.class, () -> { new Version("1."); });
        assertThrows(IllegalStateException.class, () -> { new Version(".1"); });
        assertThrows(IllegalStateException.class, () -> { new Version("1.alpha.beta"); });
        assertThrows(IllegalStateException.class, () -> { new Version("1.0alph"); });
        assertThrows(IllegalStateException.class, () -> { new Version("1.0be"); });
        assertThrows(IllegalStateException.class, () -> { new Version("1.0+^"); });
        assertThrows(IllegalStateException.class, () -> { new Version("1.2.3B4+build!12345"); });
    }

    static VersionTree<String> genTree() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1.0"), "one-oh");
        tree.put(new Version("2.0"), "two-oh");
        tree.put(new Version("2.1"), "two-one");
        tree.put(new Version("2.2"), "two-two");
        tree.put(new Version("2.2.0.1"), "two-two-oh-one");
        tree.put(new Version("3.0"), "three-oh");
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
        assertEquals(Version.ReleaseCategory.GA, new Version("1.0").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.GA, new Version("2.3.4").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, new Version("1.0.alpha").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, new Version("1.0.alpha2").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, new Version("1.0.beta").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, new Version("2.1.beta3").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, new Version("1.0.rc").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, new Version("3.0.rc1").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, new Version("dev").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, new Version("1.0.dev").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.CI, new Version("ci").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.QA, new Version("1.0.qa").getReleaseCategory());

        // Test getReleaseCategoryString (display format from master)
        assertEquals("GA", new Version("1.0").getReleaseCategoryString());
        assertEquals("alpha", new Version("1.0.alpha").getReleaseCategoryString());
        assertEquals("beta", new Version("1.0.beta").getReleaseCategoryString());
        assertEquals("rc", new Version("1.0.rc").getReleaseCategoryString());
        assertEquals("Dev", new Version("1.0.dev").getReleaseCategoryString());
        assertEquals("CI", new Version("ci").getReleaseCategoryString());
        assertEquals("QA", new Version("1.0.qa").getReleaseCategoryString());
    }

    // ----- VersionTree with pre-release versions ------------------------------------------------

    @Test
    public void testVersionTreeWithPreRelease() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1.0.alpha"), "alpha");
        tree.put(new Version("1.0.beta"), "beta");
        tree.put(new Version("1.0.rc"), "rc");
        tree.put(new Version("1.0"), "ga");

        assertEquals(4, tree.size());
        assertTrue(tree.contains(new Version("1.0.alpha")));
        assertTrue(tree.contains(new Version("1.0.beta")));
        assertTrue(tree.contains(new Version("1.0.rc")));
        assertTrue(tree.contains(new Version("1.0")));

        // Test iteration order
        var iter = tree.iterator();
        assertEquals(new Version("1.0"), iter.next());          // GA comes first (lower parts)
        assertEquals(new Version("1.0.alpha"), iter.next());    // Then alpha (-3)
        assertEquals(new Version("1.0.beta"), iter.next());     // Then beta (-2)
        assertEquals(new Version("1.0.rc"), iter.next());       // Then rc (-1)
    }

    @Test
    public void testFindHighestVersionPrefersGA() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1.0.alpha"), "alpha");
        tree.put(new Version("1.0.beta"), "beta");
        tree.put(new Version("1.0.rc"), "rc");
        tree.put(new Version("1.0"), "ga");

        // Should prefer GA over pre-release
        assertEquals(new Version("1.0"), tree.findHighestVersion());
    }

    @Test
    public void testFindHighestVersionWithOnlyPreRelease() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1.0.alpha"), "alpha");
        tree.put(new Version("1.0.beta"), "beta");
        tree.put(new Version("1.0.rc"), "rc");

        // Should prefer RC (most stable pre-release)
        assertEquals(new Version("1.0.rc"), tree.findHighestVersion());
    }

    @Test
    public void testFindHighestVersionMixedMajorVersions() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1.0"), "1.0 ga");
        tree.put(new Version("2.0.beta"), "2.0 beta");

        // Should prefer 1.0 GA over 2.0 beta
        assertEquals(new Version("1.0"), tree.findHighestVersion());
    }

    @Test
    public void testFindHighestVersionWithConstraint() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1.0"), "1.0");
        tree.put(new Version("1.1.alpha"), "1.1 alpha");
        tree.put(new Version("1.1.beta"), "1.1 beta");
        tree.put(new Version("1.1"), "1.1");
        tree.put(new Version("2.0.alpha"), "2.0 alpha");

        // Find highest >= 1.1
        assertEquals(new Version("1.1"), tree.findHighestVersion(new Version("1.1")));

        // Find highest >= 2.0 (only alpha available)
        assertEquals(new Version("2.0.alpha"), tree.findHighestVersion(new Version("2.0")));
    }

    @Test
    public void testClosestVersionWithPreRelease() {
        var tree = new VersionTree<String>();
        tree.put(new Version("1.0"), "1.0");
        tree.put(new Version("1.1.beta"), "1.1 beta");
        tree.put(new Version("1.1"), "1.1");
        tree.put(new Version("2.0"), "2.0");

        // Looking for 1.1.alpha: beta (-2) > alpha (-3) in tree order, so falls back to 1.0
        assertEquals(new Version("1.0"), tree.findClosestVersion(new Version("1.1.alpha")));

        // Looking for 1.1.rc: beta (-2) < rc (-1) in tree order, so finds beta as closest predecessor
        // Note: This is tree proximity, not substitutability - beta is NOT substitutable for rc
        assertEquals(new Version("1.1.beta"), tree.findClosestVersion(new Version("1.1.rc")));

        // Looking for 1.2 should find 1.1
        assertEquals(new Version("1.1"), tree.findClosestVersion(new Version("1.2")));
    }

    // ----- Lexer edge cases (via Version parsing) -----------------------------------------------

    @Test
    public void testAllPreReleaseCategories() {
        // Test all pre-release categories parse correctly
        assertEquals(Version.ReleaseCategory.CI, new Version("ci").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.CI, new Version("1.0.ci").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, new Version("dev").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.DEV, new Version("1.0.dev2").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.QA, new Version("qa").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.QA, new Version("1.0.qa1").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, new Version("alpha").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.ALPHA, new Version("1.0.alpha3").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, new Version("beta").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.BETA, new Version("1.0.beta4").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, new Version("rc").getReleaseCategory());
        assertEquals(Version.ReleaseCategory.RC, new Version("1.0.rc5").getReleaseCategory());
    }

    @Test
    public void testVersionNormalize() {
        assertEquals(new Version("1"), new Version("1.0.0.0").normalize());
        assertEquals(new Version("1.2"), new Version("1.2.0.0").normalize());
        assertEquals(new Version("1.2.3"), new Version("1.2.3.0").normalize());
        assertEquals(new Version("1.2.3"), new Version("1.2.3").normalize());

        // Pre-release versions
        assertEquals(new Version("1.beta"), new Version("1.beta").normalize());
        assertEquals(new Version("1.beta2"), new Version("1.beta2").normalize());
    }

    @Test
    public void testVersionComparison() {
        // GA versions compare by numeric parts
        assertTrue(new Version("1.0").compareTo(new Version("2.0")) < 0);
        assertTrue(new Version("1.1").compareTo(new Version("1.2")) < 0);
        assertTrue(new Version("1.0.0").compareTo(new Version("1.0.1")) < 0);

        // Pre-release codes are negative, so alpha (-3) < beta (-2) < rc (-1)
        assertTrue(new Version("1.0.alpha").compareTo(new Version("1.0.beta")) < 0);
        assertTrue(new Version("1.0.beta").compareTo(new Version("1.0.rc")) < 0);

        // Note: compareTo is lexicographic by parts, not semantic stability ordering.
        // "1.0.rc" has 3 parts [1, 0, -1], "1.0" has 2 parts [1, 0].
        // After comparing shared parts (equal), the longer version is "greater".
        // Use isGARelease() and getReleaseCategory() for stability comparison.
        assertTrue(new Version("1.0.rc").compareTo(new Version("1.0")) > 0);
        assertTrue(new Version("1.0").compareTo(new Version("1.0.rc")) < 0);

        // But rc is still a pre-release
        assertFalse(new Version("1.0.rc").isGARelease());
        assertTrue(new Version("1.0").isGARelease());

        // And GA is more stable than RC
        assertTrue(new Version("1.0").getReleaseCategory().compareTo(
                new Version("1.0.rc").getReleaseCategory()) > 0);
    }

    static void out() {
        out("");
    }

    static void out(Object o) {
        System.out.println(o);
    }
}
