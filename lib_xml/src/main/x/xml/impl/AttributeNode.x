/**
 * An implementation of the [Attribute] interface using the [Node] framework. `Attribute`s occur
 * inside of [Element]s; each Element can have 0 or more named attributes, with the names being
 * unique **and case-sensitive**. Since there are likely to be a great number of `Attribute`
 * instances, the design optimizes for space.
 *
 * When there are no child nodes to hold the data, the `Attribute` maintains its value in a field,
 * but it can lazily instantiate a [Part] to represent the value if and when a caller requests the
 * child `Part`s.
 */
class AttributeNode
        implements Attribute
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an [AttributeNode] with the specified name and optional value.
     *
     * @param parent  the [Attribute]'s parent [Node], or `Null`
     * @param name    the [Attribute]'s name
     * @param value   the [Attribute]'s value
     */
    construct(ElementNode? parent, String name, String value) {
        assert:arg isValidName(name);
        this.parent_ = parent;
        this.name    = name;
        this.value   = value;
    }

    /**
     * Construct a new mutable `AttributeNode`, copying the content of the passed `AttributeNode`.
     *
     * @param that  the `AttributeNode` to copy
     */
    @Override
    construct(AttributeNode that) {
        this.name        = that.name;
        this.value       = that.cachedMods_ == that.mods_ || that.oneDataChild() ? that.value : "";
        this.cachedMods_ = 0;
        if (!that.oneDataChild()) {
            Node? prev = Null;
            for (Part part : that.parts) {
                Node node = makeNode(part);
                if (prev == Null) {
                    child_ = node;
                } else {
                    prev.next_ = node;
                }
                prev = node;
            }
        }
    } finally {
        // finish the adoption
        for (Node? node = child_; node != Null; node = node.next_) {
            node.parent_ = this;
        }
    }

    /**
     * Construct a new `AttributeNode`, copying the content of the passed `Attribute`.
     *
     * @param that  the `Attribute` to copy
     */
    construct(Attribute that) {
        if (that.is(AttributeNode)) {
            construct AttributeNode(that);
        } else {
            Node? prev = Null;
            EachPart: for (Part part : that.parts) {
                Node node = makeNode(part);
                if (prev == Null) {
                    child_ = node;
                } else {
                    prev.next_ = node;
                }
                prev = node;
            }
        }
    } finally {
        if (!that.is(AttributeNode)) {
            // finish the adoption
            for (Node? node = child_; node != Null; node = node.next_) {
                node.parent_ = this;
            }
        }
    }

    // ----- Attribute API --------------------------------------------------------------------------

    @Override
    @RO ElementNode? parent.get() = parent_.as(ElementNode?);

    @Override
    String name.set(String newName) {
        String oldName = name;
        if (newName != oldName) {
            assert:arg isValidName(newName);
            // make sure no other sibling attribute has the same name
            assert !parent?.attributeByName(newName) as $"An Attribute with the name {newName.quoted()} already exists";
            mod();
            super(newName);
        }
    }

    @Override
    String value {
        @Override
        String get() {
            if (child_ == Null || cachedMods_ == mods_) {
                return super();
            }

            // have to rebuild the text value from the child content node(s)
            String text;
            if (Data node := oneDataChild()) {
                text = node.text;
            } else {
                StringBuffer buf = new StringBuffer();
                for (Node? node = child_; node != Null; node = node.next_) {
                    node.as(ContentNode).text.appendTo(buf);
                }
// TODO GG to discuss (compiler error attempting to access protected property on Node)
//                ContentNode? node = child_.as(ContentNode);
//                while (node != Null) {
//                    node.text.appendTo(buf);
//                    node = node.next_?.as(ContentNode) : Null;
//                }
                text = buf.toString();
            }
            if (!this.is(immutable)) {
                set(text, cacheOnly=True);
            }
            return text;
        }

        @Override
        void set(String newValue, Boolean cacheOnly = False) {
            String oldValue = this.value;
            if (newValue != oldValue) {
                if (!cacheOnly) {
                    // remove all previous parts (or if the first one is a data part, rewrite it)
                    Node? prev = Null;
                    Node? node = child_;
                    if (node.is(Data)) {
                        prev = node;
                        node = node.next_;
                    }
                    while (node != Null) {
                        Node? next = node.next_;
                        unlink_(prev, node);
                        node = next;
                    }

                    // use one data part to hold the new text value
// TODO CP this is just for testing; we only need the "else" part
                    if (child_ == Null) {
                        link_(Null, new @ContentNode(this) Data(newValue));
//                        link(Null, new @ContentNode Data(newValue));
                    } else {
                        child_.as(Data).text = newValue;
                    }
                    mod();
                }
                super(value);
                cachedMods_ = mods_;
            }
        }
    }

    @Override
    conditional Int knownSize() = (child_?.next_ : Null) != Null ? False : (True, 1);

    @Override
    @RO Int size.get() = super().notLessThan(1);    // always has at least one Content child

    @Override
    @RO Boolean empty.get() = False;                // always has at least one Content child

    @Override
    conditional Part first() {
        syncParts();
        return super();
    }

    @Override
    conditional Part last() {
        syncParts();
        return super();
    }

    @Override
    Iterator<Part> iterator() {
        syncParts();
        return super();
    }

    @Override
    Cursor cursor(Int index = 0) {
        syncParts();
        return super(index);
    }

    @Override
    @Op("[]") Part getElement(Index index) {
        syncParts();
        return super(index);
    }

    @Override
    @Op("[]=") void setElement(Index index, Part value) {
        syncParts();
        super(index, value);
    }

    @Override
    Node clear() {
        assert as "An Attribute cannot clear its contents; doing so would delete its Data";
    }

    @Override
    @RO List<Content> contents.get() = new ContentList(parts);

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The mod count when the cached value was created.
     */
    protected UInt32 cachedMods_ = MaxValue;        // init to "cache is invalid"

    /**
     * Lazily create a [Data] child [Node] if necessary.
     */
    protected void syncParts() {
        if (child_ == Null) {
            link_(Null, new @ContentNode Data(value));
        }
    }

    /**
     * Determine if there is only a single child [Part] and that it is a [Data] instance.
     *
     * @return `True` iff there is only a single child [Part] and that it is a [Data] instance
     * @return (optional) the one [Data] child instance
     */
    conditional Data oneDataChild() {
        if (Data node := child_.is(Data), node.next_ == Null) {
            return True, node;
        }
        return False;
    }

    @Override
    protected conditional Node allowsChild(Part part) = part.is(Content) ? super(part) : False;

    @Override
    protected (Node!? cur, UInt32 mods) replaceNode(Int index, Node!? prev, Node!? cur, Part part) {
        syncParts();
        return super(index, prev, cur, part);
    }

    @Override
    protected (Node!? cur, UInt32 mods) insertNode(Int index, Node!? prev, Node!? cur, Part part) {
        syncParts();
        return super(index, prev, cur, part);
    }

    @Override
    protected (Node!? cur, UInt32 mods) deleteNode(Int index, Node!? prev, Node!? cur) {
        syncParts();
        return super(index, prev, cur);
    }

    @Override
    (Boolean found, Int index, Node!? prev, Node!? node) findNode(Int index) {
        syncParts();
        return super(index);
    }

    @Override
    (Boolean found, Int index, Node!? prev, Node!? node) findNode(Part part, Int startAt = 0) {
        syncParts();
        return super(part, startAt);
    }

    @Override
    protected class CursorImpl {
        @Override
        void checkMod() {
            syncParts();
            super();
        }
    }

    // ----- Content List implementation -----------------------------------------------------------

    /**
     * A custom router from the `List<Content>` to the `List<Part>`. [Attribute]s can only contain
     * [Content] children, so the `Content` `List` can delegate to the underling `Part` `List`.
     */
    protected static class ContentList(List<Part> partList)
            implements List<Content> {
        @Override
        @RO Boolean indexed.get() = partList.indexed;

        @Override
        conditional Int knownSize() = partList.knownSize();

        @Override
        @RO Int size.get() = partList.size;

        @Override
        @RO Boolean empty.get() = partList.empty;

        @Override
        conditional Content first() {
            if (Part part := partList.first()) {
                return True, part.as(Content);
            }
            return False;
        }

        @Override
        conditional Content last() {
            if (Part part := partList.last()) {
                return True, part.as(Content);
            }
            return False;
        }

        @Override
        Iterator<Content> iterator() {
            Iterator<Part> iter = partList.iterator();
            return new Iterator<Content>() {
                @Override
                conditional Content next() {
                    if (Part part := iter.next()) {
                        return True, part.as(Content);
                    }
                    return False;
                }
            };
        }

        @Override
        Cursor cursor(Int index = 0) {
            List<Part>.Cursor partCursor = partList.cursor(index);
            return new Cursor() {
                @Override
                @RO Boolean bidirectional.get() = partCursor.bidirectional;

                @Override
                Int index {
                    @Override
                    Int get() = partCursor.index;

                    @Override
                    void set(Int newIndex) {
                        partCursor.index = newIndex;
                    }
                }

                @Override
                @RO Boolean exists.get() = partCursor.exists;

                @Override
                Content value {
                    @Override
                    Content get() = partCursor.value.as(Content);

                    @Override
                    void set(Content newValue) {
                        partCursor.value = newValue;
                    }
                }

                @Override
                Boolean advance() = partCursor.advance();

                @Override
                void insert(Content value) = partCursor.insert(value);

                @Override
                void delete() = partCursor.delete();
            };
        }

        @Override
        @Op("[]") Content getElement(Index index) = partList.getElement(index).as(Content);

        @Override
        @Op("[]=") void setElement(Index index, Content value) = partList.setElement(index, value);

        @Override
        Boolean contains(Content value) = partList.contains(value);

        @Override
        conditional Int indexOf(Content value, Int startAt = 0) = partList.indexOf(value, startAt);

        @Override
        ContentList add(Content value) {
            partList.add(value);
            return this;
        }

        @Override
        ContentList insert(Int index, Content value) {
            partList.insert(index, value);
            return this;
        }

        @Override
        @Op("-") ContentList remove(Content value) {
            partList.remove(value);
            return this;
        }

        @Override
        @Op("-") ContentList removeAll(Iterable<Content> values) {
            partList.removeAll(values);
            return this;
        }

        @Override
        conditional ContentList removeIfPresent(Content value) {
            if (partList.removeIfPresent(value)) {
                return True, this;
            }
            return False;
        }

        @Override
        ContentList delete(Int index) {
            partList.delete(index);
            return this;
        }

        @Override
        ContentList clear() {
            partList.clear();
            return this;
        }
    }
}