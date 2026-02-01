package org.xvm.asm;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * A data structure that holds values associated with versions.
 */
public final class VersionTree<V>
        implements Iterable<Version> {

    // ----- VersionTree API -----------------------------------------------------------------------

    /**
     * @return true iff the tree is empty
     */
    public boolean isEmpty() {
        return root.kids.isEmpty();
    }

    /**
     * Determine the number of version entries in the tree.
     *
     * @return the number of nodes that are present in the tree
     */
    public int size() {
        return (int) stream().count();
    }

    /**
     * Iterate the keys in the tree.
     *
     * @return an iterator of the versions that act as the keys in the tree
     */
    @Override
    @NotNull
    public Iterator<Version> iterator() {
        return new Iterator<>() {
            private Node<V> prev = root;
            private Node<V> next = null;

            @Override
            public boolean hasNext() {
                return loadNext() != null;
            }

            @Override
            public Version next() {
                var node = loadNext();
                if (node == null) {
                    throw new NoSuchElementException();
                }
                prev = node;
                next = null;
                return node.getVersion();
            }

            /**
             * Depth-first search of the tree.
             *
             * @return the next node, or null if the tree is exhausted
             */
            private Node<V> loadNext() {
                if (next != null) {
                    return next;
                }
                if (prev == null) {
                    return null;
                }

                // Check children first
                if (!prev.kids.isEmpty()) {
                    return next = prev.kids.getFirst().firstPresent();
                }

                // Then check siblings going up
                for (var node = prev; node != null; node = node.parent) {
                    var sibling = node.nextSibling();
                    if (sibling != null) {
                        return next = sibling.firstPresent();
                    }
                }
                return null;
            }
        };
    }

    /**
     * Obtain a stream of versions in this tree.
     *
     * @return a stream of the versions that act as the keys in the tree
     */
    @NotNull
    public Stream<Version> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Perform an action for each version-value pair in this tree.
     *
     * @param action  the action to perform for each version-value pair
     */
    public void forEach(BiConsumer<Version, V> action) {
        entryStream().forEach(e -> action.accept(e.getKey(), e.getValue()));
    }

    /**
     * Obtain a stream of version-value entries in this tree.
     *
     * @return a stream of the version-value entries in the tree
     */
    @NotNull
    public Stream<Map.Entry<Version, V>> entryStream() {
        return stream().map(ver -> Map.entry(ver, Objects.requireNonNull(get(ver))));
    }

    /**
     * Test for the presence of the specified version in this tree.
     *
     * @param ver  the version to test for
     *
     * @return true iff the version exists in this tree
     */
    public boolean contains(@NotNull Version ver) {
        return findNode(Objects.requireNonNull(ver)).isPresent();
    }

    /**
     * Obtain the value stored associated with the specified version.
     *
     * @param ver  the version
     *
     * @return the value, or null if that version is not present
     */
    @Nullable
    public V get(@NotNull Version ver) {
        return findNode(Objects.requireNonNull(ver)).map(n -> n.value).orElse(null);
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
     * @return the closest version that is present in the tree, or null if none found
     */
    @Nullable
    public Version findClosestVersion(@NotNull Version ver) {
        return Optional.ofNullable(root.findClosestNode(Objects.requireNonNull(ver), 0))
                .map(Node::getVersion)
                .orElse(null);
    }

    /**
     * Retrieve the lowest version in the tree.
     *
     * @return the lowest version, or null if the tree is empty
     */
    @Nullable
    public Version findLowestVersion() {
        return isEmpty() ? null : root.kids.getFirst().firstPresent().getVersion();
    }

    /**
     * Find the latest (preferably GA) version in the tree.
     *
     * @return the latest version, or null if none found
     */
    @Nullable
    public Version findHighestVersion() {
        return Optional.ofNullable(root.findHighestNode()).map(Node::getVersion).orElse(null);
    }

    /**
     * Find the latest (preferably GA) version in the tree that is later than the specified version.
     *
     * @param ver  the version requirement
     *
     * @return the latest version, or null if none found
     */
    @Nullable
    public Version findHighestVersion(@NotNull Version ver) {
        return Optional.ofNullable(root.findHighestNode(Objects.requireNonNull(ver), 0))
                .map(Node::getVersion)
                .orElse(null);
    }

    /**
     * Find the lowest (oldest) version in the tree that is substitutable for the specified version.
     * A version is substitutable if it is the same as or derives from the requested version.
     * <p/>
     * This is useful for dependency resolution where you need version X or any compatible
     * newer version, preferring the oldest match for stability.
     * <p/>
     * Examples (given tree contains: 1.0, 2.0, 2.1, 2.2, 3.0):
     * <ul>
     * <li>findLowestSubstitutable(2.0) -> 2.0 (exact match)</li>
     * <li>findLowestSubstitutable(2.0.0) -> 2.0 (same version, normalized)</li>
     * <li>findLowestSubstitutable(1.5) -> 2.0 (lowest version >= 1.5)</li>
     * <li>findLowestSubstitutable(2) -> 2.0 (2.0 is substitutable for 2)</li>
     * <li>findLowestSubstitutable(4.0) -> null (nothing >= 4.0)</li>
     * </ul>
     *
     * @param ver  the minimum version requirement
     *
     * @return the lowest version that is substitutable for the requested version, or null if none
     */
    @Nullable
    public Version findLowestSubstitutable(@NotNull Version ver) {
        Objects.requireNonNull(ver);
        return stream()
                .filter(v -> v.isSubstitutableFor(ver))
                .findFirst()
                .orElse(null);
    }

    /**
     * Store the specified value for the specified version.
     *
     * @param ver    the version
     * @param value  the value to store for that version
     */
    public void put(@NotNull Version ver, @NotNull V value) {
        var node = ensureNode(Objects.requireNonNull(ver));
        node.version = ver;
        node.value = Objects.requireNonNull(value);
    }

    /**
     * Convenience method to store a value for a version specified as a string.
     *
     * @param ver    the version string
     * @param value  the value to store for that version
     */
    public void put(@NotNull String ver, @NotNull V value) {
        put(Version.of(ver), value);
    }

    /**
     * Copy all the data from that tree into this tree.
     *
     * @param that  another version tree with the same associated value type
     */
    public void putAll(@NotNull VersionTree<V> that) {
        Objects.requireNonNull(that).forEach(this::put);
    }

    /**
     * Remove the specified version and its associated value from this tree.
     *
     * @param ver  the version to remove
     */
    public void remove(@NotNull Version ver) {
        findNode(Objects.requireNonNull(ver)).filter(Node::isPresent).ifPresent(Node::remove);
    }

    /**
     * Remove all the specified versions from this tree.
     *
     * @param versions  the versions to remove
     */
    public void removeAll(@NotNull Version... versions) {
        Stream.of(versions).forEach(this::remove);
    }

    /**
     * Remove all the specified versions (as strings) from this tree.
     *
     * @param versions  the version strings to remove
     */
    public void removeAll(@NotNull String... versions) {
        Stream.of(versions).map(Version::of).forEach(this::remove);
    }

    /**
     * Clear the tree entirely.
     */
    public void clear() {
        root = new Node<>(null, 0);
    }

    /**
     * Obtain just the portion of the tree starting with the specified version on down.
     *
     * @param ver  the "root" of the nodes to include in the new tree
     *
     * @return a new VersionTree, which is not affected by changes to this tree, nor vice versa
     */
    @NotNull
    public VersionTree<V> subTree(@NotNull Version ver) {
        var result = new VersionTree<V>();
        findNode(Objects.requireNonNull(ver)).ifPresent(node -> node.copyTo(result));
        return result;
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        return o == this ||
               o instanceof VersionTree<?> that &&
               size() == that.size() &&
               entryStream().allMatch(e -> Objects.equals(e.getValue(), that.get(e.getKey())));
    }

    @Override
    public int hashCode() {
        return entryStream().mapToInt(e -> Objects.hash(e.getKey(), e.getValue())).reduce(1, (h, v) -> 31 * h + v);
    }

    @Override
    public String toString() {
        return root.render(new StringBuilder("VersionTree"), "", "").toString();
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Find the node corresponding to the specified version.
     *
     * @param ver  the version to search for in the tree
     *
     * @return the node if it exists in the tree (whether it is "present"), otherwise empty
     */
    private Optional<Node<V>> findNode(Version ver) {
        var node = root;
        for (int part : ver.asList()) {
            node = node.getChild(part);
            if (node == null) {
                return Optional.empty();
            }
        }
        return Optional.of(node);
    }

    /**
     * Find the node corresponding to the specified version, creating it if necessary.
     *
     * @param ver  the version to search for in the tree
     *
     * @return the specified node; never null
     */
    @NotNull
    private Node<V> ensureNode(Version ver) {
        var node = root;
        for (int part : ver.asList()) {
            node = node.ensureChild(part);
        }
        return node;
    }

    // ----- inner class: Node ---------------------------------------------------------------------

    /**
     * Represents a node within the version tree. All nodes are used for building the hierarchical
     * organization of tree, but only a node that has a value is considered to be "present" in the
     * tree.
     */
    private static class Node<V> {
        /**
         * The parent of this node; all nodes have a parent, except for the root node.
         */
        @Nullable
        private final Node<V> parent;

        /**
         * The version part that this node represents. The root node does not have a version part.
         */
        private final int part;

        /**
         * The child nodes of this node. Children are kept in sorted order by their version part.
         * Unlike the original implementation which used a sparse array, this uses a List that
         * grows/shrinks as needed.
         */
        @NotNull
        private final List<Node<V>> kids = new ArrayList<>();

        /**
         * The cached version (the key) of this node. The root node does not have a version.
         * This is lazily computed from the chain of parent nodes if not explicitly set.
         */
        @Nullable
        private Version version;

        /**
         * The value that this node holds, if the version for this node is associated with a value.
         * The root node does not have a value. Nodes without values are used for hierarchical
         * organization of the tree, but are not considered to be "present" in the tree.
         */
        @Nullable
        private V value;

        Node(@Nullable Node<V> parent, int part) {
            this.parent = parent;
            this.part = part;
        }

        /**
         * Obtain the exact version of this node.
         *
         * @return the version that this node represents
         */
        @NotNull
        Version getVersion() {
            if (version == null) {
                var parts = Stream.iterate(this, n -> n.parent != null, n -> n.parent)
                        .map(n -> n.part)
                        .toList()
                        .reversed();
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
        boolean isPresent() {
            return value != null;
        }

        /**
         * @return this node, if it is present, otherwise the first contained node that is present
         */
        @NotNull
        Node<V> firstPresent() {
            if (isPresent()) {
                return this;
            }
            if (kids.isEmpty()) {
                throw new IllegalStateException(toString());
            }
            return kids.getFirst().firstPresent();
        }

        /**
         * @return the next sibling of this node
         */
        @Nullable
        Node<V> nextSibling() {
            if (parent == null) {
                return null;
            }
            int index = parent.kids.indexOf(this);
            return index >= 0 && index < parent.kids.size() - 1 ? parent.kids.get(index + 1) : null;
        }

        /**
         * Find the child with the specified version part, if it exists.
         *
         * @param part  the version part
         *
         * @return the child, iff it exists, otherwise null
         */
        @Nullable
        Node<V> getChild(int part) {
            return kids.stream().filter(k -> k.part == part).findFirst().orElse(null);
        }

        /**
         * Create a child with the specified version part, if one does not already exist.
         *
         * @param part  the version part
         *
         * @return the child node
         */
        @NotNull
        Node<V> ensureChild(int part) {
            return kids.stream().filter(k -> k.part == part).findFirst().orElseGet(() -> {
                var node = new Node<>(this, part);
                int index = (int) kids.stream().takeWhile(k -> k.part < part).count();
                kids.add(index, node);
                return node;
            });
        }

        /**
         * Find a node (from this point down in the tree) that represents the "closest derivative"
         * of the specified version.
         *
         * @param ver    the version being searched for
         * @param iPart  the index of the part that potentially corresponds to a child of this node
         *
         * @return the node that most closely derives from the specified version, or null if none
         */
        @Nullable
        Node<V> findClosestNode(Version ver, int iPart) {
            int     nPart    = iPart >= ver.size() ? 0 : ver.getPart(iPart);
            Node<V> fallback = isPresent() && nPart >= 0 ? this : null;

            return Optional.ofNullable(getChild(nPart))
                    .map(kid -> kid.findClosestNode(ver, iPart + 1))
                    .or(() -> kids.reversed().stream()
                            .filter(k -> k.part < nPart && k.isPresent())
                            .findFirst())
                    .orElse(fallback);
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
         * @param ver    the version being searched for
         * @param iPart  the index of the part that potentially corresponds to a child of this node
         *
         * @return the node that represents the highest version, using the rules defined by this
         *         method, or null if no node is present from this point down in the tree
         */
        @Nullable
        Node<V> findHighestNode(Version ver, int iPart) {
            int     cParts = ver.size();
            int     cMatch = cParts - 1;
            boolean fGA    = !(cParts >= 1 && ver.getPart(cParts - 1) < 0 ||
                              cParts >= 2 && ver.getPart(cParts - 2) < 0);
            if (!fGA) {
                cMatch -= ver.getPart(cParts - 1) < 0 ? 1 : 2;
            }

            if (iPart < cMatch) {
                int nPart = ver.getPart(iPart);
                return Optional.ofNullable(getChild(nPart))
                        .map(kid -> kid.findHighestNode(ver, iPart + 1))
                        .orElseGet(() -> nPart == 0 && isPresent() &&
                                ver.asList().subList(iPart + 1, cParts).stream().allMatch(p -> p == 0) ? this : null);
            }

            if (iPart < cParts) {
                int nPart = ver.getPart(iPart);
                var best = kids.reversed().stream()
                        .takeWhile(kid -> kid.part >= nPart)
                        .map(kid -> kid.part == nPart ? kid.findHighestNode(ver, iPart + 1) : kid.findHighestNode())
                        .filter(Objects::nonNull)
                        .reduce((acc, node) ->
                                acc.getVersion().isGARelease() ? acc :
                                node.getVersion().isGARelease() ? node :
                                node.getVersion().getReleaseCategory().isMoreStableThan(acc.getVersion().getReleaseCategory()) ? node : acc)
                        .orElse(null);

                // GA from children wins; otherwise prefer this if GA, substitutable, and no better candidate
                return best != null && best.getVersion().isGARelease() ? best :
                       isPresent() && (best == null || getVersion().isGARelease()) && getVersion().isSubstitutableFor(ver)
                        ? this : best;
            }

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
        @Nullable
        Node<V> findHighestNode() {
            // Find first GA among children, or keep first non-GA as fallback
            var best = kids.reversed().stream()
                    .map(Node::findHighestNode)
                    .filter(Objects::nonNull)
                    .reduce((first, next) ->
                            first.getVersion().isGARelease() ? first :
                            next.getVersion().isGARelease() ? next : first)
                    .orElse(null);

            // GA from children wins; otherwise prefer this if GA or no candidate
            return best != null && best.getVersion().isGARelease() ? best :
                   isPresent() && (best == null || getVersion().isGARelease()) ? this : best;
        }

        /**
         * Get rid of this node.
         */
        void remove() {
            value = null;
            version = null;
            if (kids.isEmpty() && parent != null) {
                parent.kids.remove(this);
                if (parent.kids.isEmpty() && parent.value == null) {
                    parent.remove();
                }
            }
        }

        /**
         * Recursively copy this node and its children to the specified tree.
         */
        void copyTo(VersionTree<V> tree) {
            Optional.ofNullable(value).ifPresent(v -> tree.put(getVersion(), v));
            kids.forEach(kid -> kid.copyTo(tree));
        }

        @Override
        public String toString() {
            return parent == null ? "root" : "%s=%s".formatted(getVersion(), value);
        }

        StringBuilder render(StringBuilder sb, String indentFirst, String indent) {
            if (parent != null) {
                sb.append('\n').append(indentFirst).append(part);
                if (isPresent()) {
                    sb.append(":  ").append(this);
                }
            }
            if (!kids.isEmpty()) {
                var nextIndent = indent + "|- ";
                var kidsIndent = indent + "|  ";
                var lastIndent = indent + "   ";
                var lastKid = kids.getLast();
                kids.forEach(kid -> kid.render(sb, nextIndent, kid == lastKid ? lastIndent : kidsIndent));
            }
            return sb;
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The root node of the tree. The root itself never holds a value; it is only a container
     * for the top-level version nodes.
     */
    @NotNull
    private Node<V> root = new Node<>(null, 0);
}
