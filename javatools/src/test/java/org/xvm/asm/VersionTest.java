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
        VersionTree<String> tree = new VersionTree<>();
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

    /**
     * VersionTree equality must have a matching stable hash code. The old mismatch made map/set
     * behavior incorrect even without parallelism, and unsafe once trees are cached or shared.
     */
    @Test
    public void testVersionTreeHashMatchesEquality() {
        var tree1 = genTree();
        var tree2 = genTree();

        assertEquals(tree1, tree2);
        assertEquals(tree1.hashCode(), tree2.hashCode());
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
        VersionTree<String> tree = new VersionTree<>();
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

    @Test
    public void testCiVersionSurvivesIntPartsReconstruction() {
        // "CI" is the lowest legal pre-release category (part value -6); reconstructing a Version
        // from int parts - which VersionTree iteration does internally - must accept it rather
        // than reject it as illegal
        Version ci = new Version(new int[] {1, 2, -6}, null);
        assertEquals("1.2-CI", ci.toString());
        assertTrue(ci.isSameAs(new Version("1.2-ci")));

        VersionTree<String> tree = new VersionTree<>();
        tree.put(new Version("1.2-ci"), "ci-build");
        Iterator<Version> iter = tree.iterator();
        assertTrue(iter.hasNext());
        assertEquals("ci-build", tree.get(iter.next()));

        // the NONE sentinel category below "CI" remains unconstructable from int parts
        assertThrows(IllegalStateException.class, () -> new Version(new int[] {1, -7}, null));
    }

    /**
     * {@code isSameAs} compares the shared version parts and then requires every part beyond the
     * shared prefix to be zero, so "1.2.0" and "1.2" are the same version.
     *
     * <p>The remainder loop selected the longer array into {@code remaining}, iterated to ITS
     * length, and then indexed {@code thatInts} anyway. Whenever the receiver was the longer of
     * the two, that read ran past the end of {@code thatInts}: "1.2.3".isSameAs("1.2") threw
     * {@link ArrayIndexOutOfBoundsException} rather than returning false, and "1.2.0".isSameAs("1.2")
     * threw rather than returning true. The reversed direction worked by accident, because there
     * {@code remaining} IS {@code thatInts}.</p>
     */
    @Test
    public void testIsSameAsAcrossDifferingPartCounts() {
        // the receiver is longer - the direction that used to throw
        assertTrue(new Version("1.2.0").isSameAs(new Version("1.2")));
        assertFalse(new Version("1.2.3").isSameAs(new Version("1.2")));

        // the argument is longer - the direction that always worked
        assertTrue(new Version("1.2").isSameAs(new Version("1.2.0")));
        assertFalse(new Version("1.2").isSameAs(new Version("1.2.3")));

        // trailing zeros are ignorable however many there are, in both directions
        assertTrue(new Version("1.2.0.0").isSameAs(new Version("1.2")));
        assertTrue(new Version("1.2").isSameAs(new Version("1.2.0.0")));

        // a non-zero part anywhere past the shared prefix still differs
        assertFalse(new Version("1.2.0.1").isSameAs(new Version("1.2")));
        assertFalse(new Version("1.2").isSameAs(new Version("1.2.0.1")));

        // equal-length versions are unaffected by the remainder loop
        assertTrue(new Version("1.2.3").isSameAs(new Version("1.2.3")));
        assertFalse(new Version("1.2.3").isSameAs(new Version("1.2.4")));
    }

    static VersionTree<String> genTree() {
        VersionTree<String> tree = new VersionTree<>();
        tree.put(new Version("1.0"), "one-oh");
        tree.put(new Version("2.0"), "two-oh");
        tree.put(new Version("2.1"), "two-one");
        tree.put(new Version("2.2"), "two-two");
        tree.put(new Version("2.2.0.1"), "two-two-oh-one");
        tree.put(new Version("3.0"), "three-oh");
        return tree;
    }

    static void out() {
        out("");
    }

    static void out(Object o) {
        System.out.println(o);
    }
}
