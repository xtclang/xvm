package org.xvm.asm;


import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * A data structure that holds values associated with versions.
 *
 * @author cp 2017.06.12
 */
public class VersionTree<V>
        implements Iterable<Version>
    {
    public VersionTree()
        {
        clear();
        }

    /**
     * @return true iff the tree is empty
     */
    public boolean isEmpty()
        {
        return count == 0;
        }

    /**
     * Determine the number of version entries in the tree.
     *
     * @return the number of nodes that are present in the tree
     */
    public int size()
        {
        return count;
        }

    /**
     * Iterate the keys in the tree.
     *
     * @return an iterator of the versions that act as the keys in the tree
     */
    public Iterator<Version> iterator()
        {
        return new Iterator<Version>()
            {
            @Override
            public boolean hasNext()
                {
                return loadNext() != null;
                }

            @Override
            public Version next()
                {
                Node node = loadNext();
                this.prev = node;
                this.next = null;
                if (node == null)
                    {
                    throw new NoSuchElementException();
                    }
                return node.getVersion();
                }

            /**
             * Depth-first search of the tree.
             *
             * @return the next node, or null if the tree is exhausted
             */
            private Node loadNext()
                {
                if (next != null)
                    {
                    return next;
                    }

                if (prev == null)
                    {
                    return null;
                    }

                // first check if the previous node has any children to iterate
                Node[] kids = prev.kids;
                if (kids != null && kids[0] != null)
                    {
                    return next = kids[0].firstContainedPresent();
                    }

                // next check for a sibling of the previous node
                Node nodeRewind = prev;
                while (nodeRewind != null)
                    {
                    Node nodeSibling = nodeRewind.nextSibling();
                    if (nodeSibling != null)
                        {
                        return next = nodeSibling.firstContainedPresent();
                        }

                    nodeRewind = nodeRewind.parent;
                    }

                return null;
                }

            private Node prev = root;
            private Node next = null;
            };
        }

    /**
     * Obtain the value stored associated with the specified version.
     *
     * @param ver  the version
     *
     * @return the value, or null if that version is not present
     */
    public V get(Version ver)
        {
        Node<V> node = findNode(ver);
        return node == null ? null : node.value;
        }

    /**
     * Store the specified value for the specified version.
     *
     * @param ver    the version
     * @param value  the value to store for that version
     */
    public void put(Version ver, V value)
        {
        if (value == null)
            {
            throw new IllegalArgumentException("value cannot be null");
            }

        Node node = ensureNode(ver);
        if (!node.isPresent())
            {
            ++count;
            }
        node.value = value;
        }

    /**
     * Remove the specified version and its associated value from this tree.
     *
     * @param ver  the version to remove
     */
    public void remove(Version ver)
        {
        Node<V> node = findNode(ver);
        if (node != null)
            {
            if (node.isPresent())
                {
                --count;
                }

            node.remove();
            }
        }

    /**
     * Clear the tree entirely.
     */
    public void clear()
        {
        root  = new Node<>(null, 0);
        count = 0;
        }

    /**
     * Obtain just the portion of the tree starting with the specified version on down.
     *
     * @param ver  the "root" of the nodes to include in the new tree
     *
     * @return a new VersionTree, which is not affected by changes to this tree, nor vice versa
     */
    public VersionTree<V> subTree(Version ver)
        {
        VersionTree<V> that = new VersionTree<>();
        Node node = findNode(ver);
        if (node != null)
            {
            node.copyTo(that);
            }
        return that;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("VersionTree");
        root.render(sb, "", "");
        return sb.toString();
        }

    /**
     * Find the node corresponding to the specified version. Only used internally.
     *
     * @param ver  the version to search for in the tree
     *
     * @return the node iff it exists in the tree (whether or not it is "present"), otherwise null
     */
    private Node<V> findNode(Version ver)
        {
        Node<V> node  = root;
        int[]   parts = ver.ensureIntArray();
        for (int i = 0, c = parts.length; i < c && node != null; ++i)
            {
            node = node.getChild(parts[i]);
            }
        return node;
        }

    /**
     * Find the node corresponding to the specified version, creating it if necessary. Only used
     * internally.
     *
     * @param ver  the version to search for in the tree
     *
     * @return the specified node; never null
     */
    private Node<V> ensureNode(Version ver)
        {
        Node<V> node  = root;
        int[]   parts = ver.ensureIntArray();
        for (int i = 0, c = parts.length; i < c; ++i)
            {
            node = node.ensureChild(parts[i]);
            }
        node.version = ver;
        return node;
        }


    private static class Node<V>
        {
        Node(Node parent, int part)
            {
            this.parent = parent;
            this.part   = part;
            }

        /**
         * Obtain the exact version of this node.
         *
         * @return the version that this node represents
         */
        Version getVersion()
            {
            if (version == null)
                {
                int cDepth = 0;
                Node node = this;
                while (node.parent != null)
                    {
                    ++cDepth;
                    node = node.parent;
                    }

                int[] parts = new int[cDepth];
                node = this;
                for (int i = cDepth - 1; i >= 0; --i)
                    {
                    parts[i] = node.part;
                    node = node.parent;
                    }
                version = new Version(parts);
                }

            return version;
            }

        /**
         * Determine if this node is actually present in the tree. A node is present if it has a
         * non-null value.
         *
         * @return true if this node actually exists in the tree
         */
        boolean isPresent()
            {
            return value != null;
            }

        /**
         * @return this node, if it is present, otherwise the first contained node that is present
         */
        Node firstContainedPresent()
            {
            if (isPresent())
                {
                return this;
                }

            if (kids == null)
                {
                throw new IllegalStateException(toString());
                }

            Node first = kids[0];
            return first == null ? null : first.firstContainedPresent();
            }

        /**
         * @return the next sibling of this node
         */
        Node nextSibling()
            {
            if (parent == null)
                {
                return null;
                }

            Node[] siblings = parent.kids;
            for (int i = 0, c = siblings.length; i < c; ++i)
                {
                if (siblings[i] == this)
                    {
                    return i < c-1 ? siblings[i+1] : null;
                    }
                }

            throw new IllegalStateException(toString());
            }

        /**
         * Find the child with the specified version part, if it exists.
         *
         * @param part  the version part
         *
         * @return the child, iff it exists, otherwise null
         */
        Node getChild(int part)
            {
            Node[] kids = this.kids;
            int i = indexOf(kids, part);
            return i < 0 ? null : kids[i];
            }

        /**
         * Create a child with the specified version part, if one does not already exist.
         *
         * @param part  the version part
         *
         * @return the child node
         */
        Node ensureChild(int part)
            {
            Node node = getChild(part);
            if (node == null)
                {
                // node wasn't found; create a new one
                node = new Node(this, part);
                this.kids = addNode(kids, node);
                }
            return node;
            }

        /**
         * Get rid of this node.
         */
        void remove()
            {
            // nulling the value causes the node to no longer be present
            this.value = null;
            if (kids == null || kids[0] == null)
                {
                // there are no kids; delete the node
                if (parent != null)
                    {
                    parent.removeChild(this);
                    }
                }
            }

        /**
         * Remove the specified child node from this node. Only used internally.
         *
         * @param child  the child node to remove
         */
        private void removeChild(Node child)
            {
            Node[] kids = this.kids;
            int iKid = indexOf(kids, child);
            assert iKid >= 0;
            deleteNode(kids, iKid);
            if (kids[0] == null)
                {
                this.kids = null;
                if (value == null)
                    {
                    // no kids left, no value on this node, so get rid of the node altogether
                    remove();
                    }
                }
            }

        /**
         * Recursively copy this node and its children to the specified tree.
         *
         * @param tree  the tree to copy to
         */
        void copyTo(VersionTree<V> tree)
            {
            if (isPresent())
                {
                tree.put(version, value);
                }

            if (kids != null)
                {
                for (int i = 0, c = kids.length; i < c; ++i)
                    {
                    Node kid = kids[i];
                    if (kid == null)
                        {
                        return;
                        }
                    else
                        {
                        kid.copyTo(tree);
                        }
                    }
                }
            }

        @Override
        public String toString()
            {
            return getVersion() + "=" + value;
            }

        void render(StringBuilder sb, String sIndentFirst, String sIndent)
            {
            if (parent != null)
                {
                sb.append('\n')
                  .append(sIndentFirst)
                  .append(part);

                if (isPresent())
                    {
                    sb.append(":  ")
                      .append(toString());
                    }
                }

            if (kids != null)
                {
                String sIndentNext = sIndent + "|- ";
                String sIndentKids = sIndent + "|  ";
                String sIndentLast = sIndent + "   ";
                for (int i = 0, c = kids.length; i < c; ++i)
                    {
                    Node cur = kids[i];
                    if (cur == null)
                        {
                        return;
                        }

                    boolean fLast = (i == c-1 || kids[i+1] == null);
                    cur.render(sb, sIndentNext, fLast ? sIndentLast : sIndentKids);
                    }
                }
            }

        /**
         * Find the specified node instance in the array of nodes passed in. Only used internally.
         *
         * @param nodes  the array of nodes
         * @param node   the node to look for
         *
         * @return the index of the node in the array, or -1 if it could not be found
         */
        private static int indexOf(Node[] nodes, Node node)
            {
            if (nodes == null)
                {
                return -1;
                }

            for (int i = 0, c = nodes.length; i < c; ++i)
                {
                Node cur = nodes[i];
                if (cur == null)
                    {
                    return -1;
                    }

                if (cur == node)
                    {
                    return i;
                    }
                }

            return -1;
            }

        /**
         * Find the specified version part number in the array of nodes. Only used internally.
         *
         * @param nodes  the array of nodes
         * @param part   the part to look for in the array
         *
         * @return the index of the node in the array, or -1 if it could not be found
         */
        private static int indexOf(Node[] nodes, int part)
            {
            if (nodes == null)
                {
                return -1;
                }

            for (int i = 0, c = nodes.length; i < c; ++i)
                {
                Node cur = nodes[i];
                if (cur == null)
                    {
                    return -1;
                    }

                if (cur.part == part)
                    {
                    return i;
                    }
                }

            return -1;
            }

        /**
         * Insert the specified node in the ordered array of nodes passed in. Only used internally.
         *
         * @param nodes  the old array of nodes
         * @param node   the node to add
         *
         * @return a new array of nodes (it may be a different array from the one passed in)
         */
        private static Node[] addNode(Node[] nodes, Node node)
            {
            if (nodes == null)
                {
                // most common case: the node has no children yet
                nodes = new Node[4];
                nodes[0] = node;
                return nodes;
                }

            // make sure that there's room in the array
            int c = nodes.length;
            if (nodes[c-1] != null)
                {
                // allocate and copy to a new array
                int    cNew     = c * 2;
                Node[] nodesNew = new Node[cNew];
                System.arraycopy(nodes, 0, nodesNew, 0, c);

                // use the new array
                nodes = nodesNew;
                c     = cNew;
                }

            int nodePart = node.part;
            for (int i = 0; i < c; ++i)
                {
                Node cur = nodes[i];
                if (cur == null)
                    {
                    // append to the end of the array
                    nodes[i] = node;
                    return nodes;
                    }

                if (nodePart <= cur.part)
                    {
                    assert nodePart != cur.part;
                    System.arraycopy(nodes, i, nodes, i+1, c-i-1);
                    nodes[i] = node;
                    return nodes;
                    }
                }

            throw new IllegalStateException();
            }

        /**
         * Delete the node at the specified location in the array of nodes passed in. Only used
         * internally.
         *
         * @param nodes  an array of nodes to delete from
         * @param iNode  the index of the node to delete
         */
        private static void deleteNode(Node[] nodes, int iNode)
            {
            int iLast = nodes.length - 1;
            System.arraycopy(nodes, iNode + 1, nodes, iNode, iLast - iNode);
            nodes[iLast] = null;
            }

        Node    parent;
        Version version;
        int     part;
        V       value;
        Node[]  kids;
        }

    Node<V> root;
    int     count;
    }
