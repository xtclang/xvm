/**
 * An implementation of the [ValueHolder] interface using the [Node] framework. `ValueHolder`s are
 * either [Attribute]s or [Element]s, and occur within `Element`s (or within a [Document], for the
 * root `Element` itself). The value in the `ValueHolder` can be composed of multiple [Content]
 * [Part]s, which together can be represented as a `String` value. The `String` value is optional
 * within an `Element`, and required (but may be 0 length) within an `Attribute`.
 *
 * When there are no child `Content` nodes to hold the data, the `ValueHolderNode` stores its value
 * in a `String` property, but it can lazily instantiate a [Data] `Content` `Part` to represent the
 * value if and when a caller examines the child `Part`s of the `ValueHolderNode`.
 */
@Abstract class ValueHolderNode
        implements ValueHolder
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an [ValueHolderNode] with the specified name and optional value.
     *
     * @param parent  the [ValueHolder]'s parent [Node], or `Null`
     * @param name    the [ValueHolder]'s name
     * @param value   the [ValueHolder]'s value
     */
    construct((DocumentNode|ElementNode)? parent, String name, String? value) {
        assert:arg isValidName(name);
        this.parent_ = parent;
        this.name    = name;
        this.value   = value;
    }

    /**
     * Construct a new mutable `ValueHolderNode`, copying the content of the passed `ValueHolderNode`.
     *
     * @param that  the `ValueHolderNode` to copy
     */
    @Override
    construct(ValueHolderNode that) {
        this.name        = that.name;
        this.value       = that.cachedMods_ == that.mods_ || that.contentCount <= 1 ? that.value : "";
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
     * Construct a new `ValueHolderNode`, copying the content of the passed `ValueHolder`.
     *
     * @param that  the `ValueHolder` to copy
     */
    construct(ValueHolder that) {
        if (that.is(ValueHolderNode)) {
            construct ValueHolderNode(that);
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
        if (!that.is(ValueHolderNode)) {
            // finish the adoption
            for (Node? node = child_; node != Null; node = node.next_) {
                node.parent_ = this;
            }
        }
    }

    // ----- ValueHolder API --------------------------------------------------------------------------

    @Override
    String name;

    @Override
    String? value {
        @Override
        String? get() {
            String? text;
            Int     contentCount = this.contentCount;
            if (contentCount == 0 || cachedMods_ == mods_) {
                text = super();
            } else if (Data node := oneDataChild()) {
                text = node.text;
            } else {
                // rebuild the text value from the child content nodes
                StringBuffer buf = new StringBuffer();
                for (Node? node = child_; node != Null; node = node.next_) {
                    node.is(ContentNode)?.text.appendTo(buf);
                }
// TODO CP: the access is fixed; do we still need this code?
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
        void set(String? newValue, Boolean cacheOnly = False) {
            Boolean update;
            if (cacheOnly) {
                update = True;
            } else {
                String? oldValue = this.value;
                if (newValue == oldValue) {
                    update = False;
                } else {
                    // remove all previous parts (or if the first one is a data part, rewrite it)
                    Int remain = contentCount;
                    (Node? prev, Node? node) = firstContent();
                    if (newValue != Null && node.is(Data)) {
                        node.text = newValue;
                        prev = node;
                        node = node.next_;
                    }
                    while (node != Null) {
                        Node? next = node.next_;
                        unlink_(prev, node);
                        --remain;
                        node = next;
                    }
                    contentCount = remain;
                    mod();
                    update = True;
                }
            }

            if (update) {
                super(newValue);
                cachedMods_ = mods_;
            }
        }
    }

    @Override
    conditional Int knownSize() = (child_?.next_ : Null) != Null ? False : (True, 1);

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

// TODO
//    @Override
//    Node clear() {
//        ...
//    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The mod count when the cached value was created.
     */
    protected UInt32 cachedMods_ = MaxValue;        // init to "cache is invalid"

    /**
     * Lazily create a [Data] child [Node] if necessary.
     */
    protected void syncParts() {
        if (contentCount == 0) {
            if (String value ?= value) {
                Node? prev = firstContent();
                link_(prev, new @ContentNode Data(value));
                contentCount = 1;
            }
        }
    }

    /**
     * The number of [Content] child [Node]s this `ValueHolderNode` contains.
     */
    @Abstract protected Int contentCount;

    /**
     * Find the first [Content] [Node] child of this `ValueHolderNode`.
     *
     * @return prev   the [Node] located immediately before the first [Content] `Node`, or `Null` if
     *                the first `Content` `Node` is the first `Node` or does not exist
     * @return node   the [Node] that is the first [Content] `Node`; otherwise, `Null`
     */
    @Abstract protected (Node? prev, ContentNode? node) firstContent();

    /**
     * Determine if there is only a single child [Part] and that it is a [Data] instance.
     *
     * @return `True` iff there is only a single child [Part] and that it is a [Data] instance
     * @return (optional) the one [Data] child instance
     */
    protected conditional Data oneDataChild() {
        if (contentCount == 1) {
            (_, Node? node) = firstContent();
            if (node.is(Data)) {
                return True, node;
            }
        }
        return False;
    }

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
     *
     * Unfortunately, this class must be a static child class, because otherwise its type parameter
     * name `Element` of type `Content` would conflict with the same type parameter name on the
     * containing [Node] class with the type `Part`.
     */
    protected static class ContentList(ValueHolderNode partList)
            implements List<Content> {
        @Override
        @RO Boolean indexed.get() = False;

        @Override
        @Abstract conditional Int knownSize();

        @Override
        @Abstract @RO Int size;

        @Override
        @RO Boolean empty.get() = partList.contentCount == 0;

        @Override
        conditional Content first() {
            (_, ContentNode? node) = partList.firstContent();
            return node == Null ? False : (True, node);
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