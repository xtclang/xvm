package org.xvm.asm;


import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;


/**
 * Tests for Version and related functionality.
 */
public class VersionTest
    {
    @Test
    public void testSubstitutables()
        {
        Assert.assertTrue (new Version("0"        ).isSubstitutableFor(new Version("beta"     )));
        Assert.assertTrue (new Version("1"        ).isSubstitutableFor(new Version("beta"     )));
        Assert.assertTrue (new Version("0"        ).isSubstitutableFor(new Version("0"        )));
        Assert.assertTrue (new Version("1"        ).isSubstitutableFor(new Version("0"        )));
        Assert.assertTrue (new Version("1"        ).isSubstitutableFor(new Version("1"        )));
        Assert.assertTrue (new Version("1.2.3.4.5").isSubstitutableFor(new Version("1"        )));
        Assert.assertTrue (new Version("2.1.rc"   ).isSubstitutableFor(new Version("2.1.beta3")));
        Assert.assertTrue (new Version("2.1.rc2"  ).isSubstitutableFor(new Version("2.1.beta3")));
        Assert.assertTrue (new Version("2.1"      ).isSubstitutableFor(new Version("2.1.beta" )));
        Assert.assertTrue (new Version("2.1"      ).isSubstitutableFor(new Version("2.1.beta3")));
        Assert.assertTrue (new Version("2.2"      ).isSubstitutableFor(new Version("2.1.beta3")));
        Assert.assertTrue (new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.beta" )));
        Assert.assertTrue (new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.beta3")));
        Assert.assertTrue (new Version("1.2"      ).isSubstitutableFor(new Version("1.beta"   )));
        Assert.assertTrue (new Version("1.2.alpha").isSubstitutableFor(new Version("1.beta"   )));
        Assert.assertTrue (new Version("1.2.beta" ).isSubstitutableFor(new Version("1.beta"   )));
        Assert.assertTrue (new Version("1.2.beta1").isSubstitutableFor(new Version("1.beta"   )));
        Assert.assertTrue (new Version("1.2"      ).isSubstitutableFor(new Version("1.beta1"  )));
        Assert.assertTrue (new Version("1.2.alpha").isSubstitutableFor(new Version("1.beta1"  )));
        Assert.assertTrue (new Version("1.2.beta1").isSubstitutableFor(new Version("1.beta1"  )));
        Assert.assertTrue (new Version("1.2.beta2").isSubstitutableFor(new Version("1.2.beta1")));
        Assert.assertTrue (new Version("1.2.beta" ).isSubstitutableFor(new Version("1.2.alpha")));
        Assert.assertTrue (new Version("beta"     ).isSubstitutableFor(new Version("alpha"    )));

        Assert.assertFalse(new Version("beta").isSubstitutableFor(new Version("0")));
        Assert.assertFalse(new Version("beta").isSubstitutableFor(new Version("1")));
        Assert.assertFalse(new Version("0").isSubstitutableFor(new Version("1")));
        Assert.assertFalse(new Version("1").isSubstitutableFor(new Version("1.2.3.4.5")));
        Assert.assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.rc")));
        Assert.assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1.rc2")));
        Assert.assertFalse(new Version("2.1.beta").isSubstitutableFor(new Version("2.1")));
        Assert.assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.1")));
        Assert.assertFalse(new Version("2.1.beta3").isSubstitutableFor(new Version("2.2")));
        Assert.assertFalse(new Version("2.1.beta").isSubstitutableFor(new Version("2.1.beta3")));
        Assert.assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2")));
        Assert.assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2.alpha")));
        Assert.assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2.beta")));
        Assert.assertFalse(new Version("1.beta").isSubstitutableFor(new Version("1.2.beta1")));
        Assert.assertFalse(new Version("1.beta1").isSubstitutableFor(new Version("1.2")));
        Assert.assertFalse(new Version("1.beta1").isSubstitutableFor(new Version("1.2.alpha")));
        Assert.assertFalse(new Version("1.beta1").isSubstitutableFor(new Version("1.2.beta1")));
        Assert.assertFalse(new Version("1.2.beta1").isSubstitutableFor(new Version("1.2.beta2")));
        Assert.assertFalse(new Version("1.2.alpha").isSubstitutableFor(new Version("1.2.beta")));
        Assert.assertFalse(new Version("alpha").isSubstitutableFor(new Version("beta")));
        }

    @Test
    public void testEmptyTree()
        {
        VersionTree<String> tree = new VersionTree<>();
        Assert.assertTrue(tree.isEmpty());
        Assert.assertEquals(0, tree.size());
        Assert.assertFalse(tree.iterator().hasNext());
        }

    @Test
    public void testDefaultTree()
        {
        VersionTree<String> tree = genTree();
        Assert.assertFalse(tree.isEmpty());
        Assert.assertEquals(6, tree.size());
        }

    @Test
    public void testDefaultTreeIterator()
        {
        VersionTree<String> tree = genTree();
        Iterator<Version> iter = tree.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("1.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2.0.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("3.0"), iter.next());
        Assert.assertFalse(iter.hasNext());
        }

    @Test
    public void testDefaultTreeSnipe()
        {
        VersionTree<String> tree = genTree();
        tree.remove(new Version("2.2"));

        Assert.assertFalse(tree.isEmpty());
        Assert.assertEquals(5, tree.size());

        Iterator<Version> iter = tree.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("1.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2.0.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("3.0"), iter.next());
        Assert.assertFalse(iter.hasNext());
        }

    @Test
    public void testDefaultTreePrune()
        {
        VersionTree<String> tree = genTree();
        tree.remove(new Version("2.2.0.1"));

        Assert.assertFalse(tree.isEmpty());
        Assert.assertEquals(5, tree.size());

        Iterator<Version> iter = tree.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("1.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("3.0"), iter.next());
        Assert.assertFalse(iter.hasNext());
        }

    @Test
    public void testDefaultTreeClear()
        {
        VersionTree<String> tree = genTree();

        tree.remove(new Version("1.0"));
        tree.remove(new Version("2.0"));
        tree.remove(new Version("2.1"));
        tree.remove(new Version("2.2"));
        tree.remove(new Version("2.2.0.1"));
        tree.remove(new Version("3.0"));

        Assert.assertTrue(tree.isEmpty());
        Assert.assertEquals(0, tree.size());
        Assert.assertFalse(tree.iterator().hasNext());
        }

    @Test
    public void testDefaultTreePlus()
        {
        VersionTree<String> tree = genTree();
        tree.put(new Version("2.0"), "overwrite 2.0");
        tree.put(new Version("3.1"), "three-one");

        Assert.assertFalse(tree.isEmpty());
        Assert.assertEquals(7, tree.size());

        Iterator<Version> iter = tree.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("1.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2.0.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("3.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("3.1"), iter.next());
        Assert.assertFalse(iter.hasNext());
        }

    @Test
    public void testDefaultSubTree()
        {
        VersionTree<String> tree = genTree().subTree(new Version("2"));
        Assert.assertFalse(tree.isEmpty());
        Assert.assertEquals(4, tree.size());
        Iterator<Version> iter = tree.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.0"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.1"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2"), iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(new Version("2.2.0.1"), iter.next());
        Assert.assertFalse(iter.hasNext());
        }

    @Test
    public void testDefaultSubTreeEmpty()
        {
        VersionTree<String> tree = genTree().subTree(new Version("2.3"));
        Assert.assertTrue(tree.isEmpty());
        Assert.assertEquals(0, tree.size());
        Assert.assertFalse(tree.iterator().hasNext());
        }

    @Test
    public void testClosestVersion()
        {
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

        Assert.assertNull(tree.findClosestVersion(new Version("beta")));
        Assert.assertNull(tree.findClosestVersion(new Version("beta2")));
        Assert.assertEquals(new Version("1"          ), tree.findClosestVersion(new Version("1")));
        Assert.assertEquals(new Version("2.0"        ), tree.findClosestVersion(new Version("2")));
        Assert.assertEquals(new Version("2"          ), tree.findClosestVersion(new Version("3")));
        Assert.assertEquals(new Version("4"          ), tree.findClosestVersion(new Version("4")));
        Assert.assertEquals(new Version("4"          ), tree.findClosestVersion(new Version("5")));
        Assert.assertEquals(new Version("1"          ), tree.findClosestVersion(new Version("1.5")));
        Assert.assertEquals(new Version("1"          ), tree.findClosestVersion(new Version("2.beta")));
        Assert.assertEquals(new Version("2.0"        ), tree.findClosestVersion(new Version("2.1.beta")));
        Assert.assertEquals(new Version("2.1.0.0"    ), tree.findClosestVersion(new Version("2.1")));
        Assert.assertEquals(new Version("2.1.0.0"    ), tree.findClosestVersion(new Version("2.1.0")));
        Assert.assertEquals(new Version("2.1.0"      ), tree.findClosestVersion(new Version("2.1.1")));
        Assert.assertEquals(new Version("2.1.0.1.0.0"), tree.findClosestVersion(new Version("2.1.0.1")));
        Assert.assertEquals(new Version("2.1.0.1.0"  ), tree.findClosestVersion(new Version("2.1.0.1.1")));
        Assert.assertEquals(new Version("2.1.0.1"    ), tree.findClosestVersion(new Version("2.1.0.2")));
        Assert.assertEquals(new Version("2.2"        ), tree.findClosestVersion(new Version("2.5.1.3")));
        }

    @Test
    public void testHighestVersion()
        {
        VersionTree<String> tree = genTree();
        Assert.assertEquals(new Version("3.0"), tree.findHighestVersion());
        Assert.assertEquals(new Version("3.0"), tree.findHighestVersion(new Version("3.0.0.0")));
        Assert.assertEquals(new Version("2.1"), tree.findHighestVersion(new Version("2.1.0")));
        Assert.assertEquals(new Version("2.2.0.1"), tree.findHighestVersion(new Version("2.1")));
        }


    static VersionTree<String> genTree()
        {
        VersionTree<String> tree = new VersionTree<>();
        tree.put(new Version("1.0"), "one-oh");
        tree.put(new Version("2.0"), "two-oh");
        tree.put(new Version("2.1"), "two-one");
        tree.put(new Version("2.2"), "two-two");
        tree.put(new Version("2.2.0.1"), "two-two-oh-one");
        tree.put(new Version("3.0"), "three-oh");
        return tree;
        }

    static void out()
        {
        out("");
        }

    static void out(Object o)
        {
        System.out.println(o);
        }
    }