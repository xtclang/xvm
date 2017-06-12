package org.xvm.asm;


import java.util.Iterator;
import org.junit.Assert;
import org.junit.Test;


/**
 * Tests for Version and related functionality.
 *
 * @author cp 2017.06.12
 */
public class VersionTest
    {
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
