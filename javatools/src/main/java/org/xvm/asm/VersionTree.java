package org.xvm.asm;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.xvm.util.Handy;


/**
 * A data structure that holds values associated with versions.
 */
public class VersionTree<V>
        implements Iterable<Version>
    {
    // ----- constructors --------------------------------------------------------------------------

    public VersionTree()
        {
        clear();
        }


    // ----- VersionTree API -----------------------------------------------------------------------

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
        return new Iterator<>()
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
     * Test for the presence of the specified version in this tree.
     *
     * @param ver  the version to test for
     *
     * @return true iff the version exists in this tree
     */
    public boolean contains(Version ver)
        {
        return findNode(ver) != null;
        }

    /**
     * Test for the presence of all of the version from the specified tree in this tree.
     *
     * @param that  the VersionTree of versions to test for; the values in the tree are ignored
     *
     * @return true iff all of the versions from that tree exist in this tree
     */
    public boolean containsAll(VersionTree<?> that)
        {
        for (Version ver : that)
            {
            if (!contains(ver))
                {
                return false;
                }
            }
        return true;
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
     * Find the closest version (that is present in the tree) to the specified version, and return
     * it. The "closest version" must be substitutable for the specified version, which means it
     * needs to be the exact same version as specified, or it needs to unambiguously precede it.
     * <p/>
     * Consider the following tree:
     * <code><pre>
     * VersionTree
     * |- 1:                v1
     * |- 2:                v2
     * |  |- 0:             v2.0
     * |  |- 1:             v2.1
     * |  |  |- 0:          v2.1.0
     * |  |     |- 0:       v2.1.0.0
     * |  |     |- 1:       v2.1.0.1
     * |  |        |- 0:    v2.1.0.1.0
     * |  |           |- 0: v2.1.0.1.0.0
     * |  |- 2:             v2.2
     * |- 4:                v4
     * </pre></code>
     * Examples:
     * <ul>
     * <li>findClosestVersion(1) -> v1</li>
     * <li>findClosestVersion(2) -> v2.0</li>
     * <li>findClosestVersion(3) -> v2</li>
     * <li>findClosestVersion(4) -> v4</li>
     * <li>findClosestVersion(5) -> v4</li>
     * <li>findClosestVersion(1.5) -> v1</li>
     * <li>findClosestVersion(2.1beta) -> v2.0</li>
     * <li>findClosestVersion(2.1) -> v2.1.0.0</li>
     * <li>findClosestVersion(2.1.0) -> v2.1.0.0</li>
     * <li>findClosestVersion(2.1.1) -> v2.1.0</li>
     * <li>findClosestVersion(2.1.0.1) -> v2.1.0.1.0.0</li>
     * <li>findClosestVersion(2.1.0.1.1) -> v2.1.0.1.0</li>
     * <li>findClosestVersion(2.1.0.2) -> v2.1.0.1</li>
     * <li>findClosestVersion(2.5.1.3) -> v2.2</li>
     * </ul>
     *
     * @param ver  the version to search for
     *
     * @return the closest version that is present in the tree
     */
    public Version findClosestVersion(Version ver)
        {
        int[] parts = ver.getIntArray();
        Node  node  = root.findClosestNode(parts, 0);
        return node == null ? null : node.getVersion();
        }

    /**
     * Find the latest (preferably GA) version in the tree.
     *
     * @return the latest version, or null
     */
    public Version findHighestVersion()
        {
        Node node = root.findHighestNode();
        return node == null ? null : node.getVersion();
        }

    /**
     * Find the latest (preferably GA) version in the tree that is later than the specified version.
     *
     * @param ver  the version requirement
     *
     * @return the latest version, or null
     */
    public Version findHighestVersion(Version ver)
        {
        int[] parts = ver.getIntArray();
        Node  node  = root.findHighestNode(parts, 0);
        return node == null ? null : node.getVersion();
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
     * Copy all of the data from that tree into this tree.
     *
     * @param that  another version tree with the same associated value type
     */
    public void putAll(VersionTree<V> that)
        {
        for (Version ver : that)
            {
            put(ver, that.get(ver));
            }
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
     * Remove all of the version in the specified tree from this tree.
     *
     * @param that  the VersionTree of versions to remove; the values in the tree are ignored
     */
    public void removeAll(VersionTree<?> that)
        {
        for (Version ver : that)
            {
            remove(ver);
            }
        }

    /**
     * Retain only the versions in this tree that exist in the specified tree.
     *
     * @param that  the VersionTree of versions to retain; the values in the tree are ignored
     */
    public void retainAll(VersionTree<?> that)
        {
        // first, collect a list of versions to remove, so that removal (in the middle of our
        // iteration) does not cause instability in the iterator
        ArrayList<Version> listRemove = null;
        for (Version ver : this)
            {
            if (that.get(ver) == null)
                {
                if (listRemove == null)
                    {
                    listRemove = new ArrayList<>();
                    }
                listRemove.add(ver);
                }
            }
        if (listRemove != null)
            {
            for (Version ver : listRemove)
                {
                remove(ver);
                }
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

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object o)
        {
        if (o == this)
            {
            return true;
            }

        if (o instanceof VersionTree<?> that)
            {
            if (this.size() == that.size())
                {
                for (Iterator<Version> iterThis = this.iterator(), iterThat = that.iterator();
                        iterThis.hasNext(); )
                    {
                    Version verThis = iterThis.next();
                    Version verThat = iterThat.next();
                    if (!verThis.equals(verThat) || !Handy.equals(this.get(verThis), that.get(verThat)))
                        {
                        return false;
                        }
                    }
                return true;
                }
            }

        return false;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("VersionTree");
        root.render(sb, "", "");
        return sb.toString();
        }


    // ----- internal ------------------------------------------------------------------------------

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
        int[]   parts = ver.getIntArray();
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
        int[]   parts = ver.getIntArray();
        for (int i = 0, c = parts.length; i < c; ++i)
            {
            node = node.ensureChild(parts[i]);
            }
        node.version = ver;
        return node;
        }


    // ----- inner class: Node ---------------------------------------------------------------------

    /**
     * Represents a node within the version tree. All nodes are used for building the hierarchical
     * organization of tree, but only a node that has a value is considered to be "present" in the
     * tree.
     */
    private static class Node<V>
        {
        /**
         * Construct a node.
         *
         * @param parent
         * @param part
         */
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
                version = new Version(parts, null);
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
         * Find a node (from this point down in the tree) that represents the "closest derivative"
         * of the specified version.
         *
         * @param parts  the parts of the version being searched for
         * @param iPart  the index of the part that potentially corresponds to a child of this node
         *
         * @return the node that most closely derives from the specified version, or null if none
         */
        Node findClosestNode(int[] parts, int iPart)
            {
            // what is the part that this node is looking for?
            int nPart  = iPart >= parts.length ? 0 : parts[iPart];

            // keep track of the best match that we've found; note that if the next part indicates a
            // pre-release, that means that this part can't match, because it's a pre-release of
            // this version (e.g. version 1.2.beta comes before version 1.2)
            Node nodeBestMatch = isPresent() && nPart >= 0 ? this : null;

            // go through the kids, looking for something that works, until we've passed the kids
            // that match
            Node[] kids = this.kids;
            if (kids != null)
                {
                for (int i = 0, c = kids.length; i < c; ++i)
                    {
                    Node kid = kids[i];
                    if (kid == null)
                        {
                        // no more kids
                        break;
                        }

                    if (kid.part == nPart)
                        {
                        // this is very good! we found a match
                        Node node = kid.findClosestNode(parts, iPart + 1);
                        if (node != null)
                            {
                            return node;
                            }

                        // we're not going to find a better match than an exact match
                        break;
                        }

                    if (kid.part < nPart)
                        {
                        if (kid.isPresent())
                            {
                            // it's not the exact version that we're looking for, but it is a
                            // "direct ancestor" version
                            nodeBestMatch = kid;
                            }
                        }
                    else
                        {
                        // we've past the version range that could match
                        break;
                        }
                    }
                }

            return nodeBestMatch;
            }

        /**
         * Find a node (from this point down in the tree) that represents the "latest version" by
         * a non-strictly-ordered measure:
         * <ul>
         * <li>Versions parts are evaluated left to right;</li>
         * <li>A higher version part number is assumed to be later;</li>
         * <li>A GA version is preferred over a non-GA version, even if the non-GA version is
         *     a provably later version.</li>
         * </ul>
         *
         * @param parts  the parts of the version being searched for
         * @param iPart  the index of the part that potentially corresponds to a child of this node
         *
         * @return the node that represents the highest version, using the rules defined by this
         *         method, or null if no node is present from this point down in the tree
         */
        Node findHighestNode(int[] parts, int iPart)
            {
            // the search is split into three modes:
            // 1. exact: for every version part except the last (or the last two in the case of a
            //    version ending with something like ".beta2")
            // 2. for the last version part, any substitutable version will work, so either an
            //    increment of the last part (e.g. 2.2 instead of 2.1), or a sub-version thereof
            //    (e.g. 2.1.1 instead of 2.1), with the highest being searched for left-to-right
            // 3. for any children beyond that, looking for the latest (since they are all
            //    substitutable at that point)

            Node[]  kids   = this.kids;
            int     cParts = parts.length;
            int     cMatch = cParts - 1;
            boolean fGA    = !(cParts >= 1 && parts[cParts - 1] < 0
                            || cParts >= 2 && parts[cParts - 2] < 0);
            if (!fGA)
                {
                cMatch -= parts[cParts - 1] < 0 ? 1 : 2;
                }

            if (iPart < cMatch)
                {
                // this part needs to be an exact match
                int  nPart = parts[iPart];
                Node kid   = getChild(nPart);
                if (kid != null)
                    {
                    return kid.findHighestNode(parts, iPart + 1);
                    }

                // it's possible that the request is for a .0.0.0 version of this node
                if (nPart == 0 && isPresent())
                    {
                    while (++iPart < cParts)
                        {
                        if (parts[iPart] != 0)
                            {
                            return null;
                            }
                        }
                    return this;
                    }

                return null;
                }

            if (iPart < cParts)
                {
                // find the highest substitutable version; this is the difficult part of the
                // algorithm, because if they ask for 1.2.beta (fGA==false, cParts==3) and there is
                // a 1.2.beta1 and a 1.3, it needs to take the 1.3; in a sense, it's like the
                // request was actually for 1.2, with the additional information being "but we'll
                // accept a beta or later of 1.2 itself"

                // go through the kids from newest to oldest, looking for a GA release (and keeping
                // track of the newest non-GA release, just in case)
                int  nPart     = parts[iPart];
                Node nodeNonGA = null;
                if (kids != null)
                    {
                    for (int i = kids.length - 1; i >= 0; --i)
                        {
                        Node kid = kids[i];
                        if (kid != null)
                            {
                            // make sure that the child meets the version requirement
                            if (kid.part < nPart)
                                {
                                // we've looked too far; this child's version is too early to meet the
                                // requirement, and all of the rest of the children will be even earlier
                                break;
                                }

                            Node node = kid.part == nPart
                                    ? kid.findHighestNode(parts, i+1)
                                    : kid.findHighestNode();
                            if (node != null)
                                {
                                if (node.getVersion().isGARelease())
                                    {
                                    return node;
                                    }

                                if (nodeNonGA == null || nodeNonGA.getVersion().getReleaseCategory()
                                        < node.getVersion().getReleaseCategory())
                                    {
                                    nodeNonGA = node;
                                    }
                                }
                            }
                        }
                    }

                // return this node, if it is present and substitutable for the requested version,
                // if there is no child that matches, or this node is a GA release
                return this.isPresent() && (nodeNonGA == null || this.getVersion().isGARelease())
                    && this.getVersion().isSubstitutableFor(new Version(parts, null))
                        ? this
                        : nodeNonGA;
                }

            // otherwise, find the highest version available
            return findHighestNode();
            }

        /**
         * Find a node (from this point down in the tree) that represents the "latest version" by
         * a non-strictly-ordered measure:
         * <ul>
         * <li>Versions parts are evaluated left to right;</li>
         * <li>A higher version part number is assumed to be later;</li>
         * <li>A GA version is preferred over a non-GA version, even if the non-GA version is
         *     a provably later version.</li>
         * </ul>
         *
         * @return the node that represents the highest version, using the rules defined by this
         *         method, or null if no node is present from this point down in the tree
         */
        Node findHighestNode()
            {
            // go through the kids from newest to oldest, looking for a GA release (and keeping
            // track of the newest non-GA release, just in case)
            Node nodeBestMatch = null;
            Node[] kids = this.kids;
            if (kids != null)
                {
                for (int i = kids.length - 1; i >= 0; --i)
                    {
                    Node kid = kids[i];
                    if (kid != null)
                        {
                        Node node = kid.findHighestNode();
                        if (node != null)
                            {
                            if (node.getVersion().isGARelease())
                                {
                                return node;
                                }

                            if (nodeBestMatch == null)
                                {
                                nodeBestMatch = node;
                                }
                            }
                        }
                    }
                }

            return this.isPresent() && (nodeBestMatch == null || this.getVersion().isGARelease())
                    ? this
                    : nodeBestMatch;
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
                      .append(this);
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

        // ----- helpers -----------------------------------------------------------------------

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

        // ----- fields ------------------------------------------------------------------------

        /**
         * The parent of this node; all nodes have a parent, except for the root node.
         */
        Node    parent;

        /**
         * The cached version (the key) of this node. The root node does not have a version.
         */
        Version version;

        /**
         * The version part that this node represents. The root node does not have a version part.
         */
        int     part;

        /**
         * The value that this node holds, if the version for this node is associated with a value.
         * The root node does not have a value. Nodes without values are used for hierarchical
         * organization of the tree, but are not considered to be "present" in the tree.
         */
        V       value;

        /**
         * The child nodes of this node. May be null, which indicates no children. May contain
         * nulls, but the non-null child references always start at index zero and occur in order
         * of the version part represented by each child.
         */
        Node[]  kids;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The root node of the tree.
     */
    Node<V> root;

    /**
     * The number of values in the tree.
     */
    int     count;
    }