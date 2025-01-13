/**
 * An implementation of the [Attribute] interface using the [Node] framework. `Attribute`s occur
 * inside of [Element]s; each Element can have 0 or more named attributes, with the names being
 * unique **and case-sensitive**. Since there are likely to be a great number of `Attribute`
 * instances, the design optimizes for space.
 *
 * When possible, the `Attribute` maintains its value in a field, but it can lazily instantiate a
 * [Part] to represent the value if and when a caller examines its parts.
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
        // TODO
        TODO
    }

    /**
     * Construct a new `AttributeNode`, copying the content of the passed `Attribute`.
     *
     * @param that  the `Attribute` to copy
     */
    construct(Attribute that) {
        Node? prev = Null;
        EachPart: for (Part part : that.parts) {
            Node node = makeNode(part);
            if (node.is(Content)) {
                // TODO should we build up the attribute value here, or lazily do it when requested
            }
            if (prev == Null) {
                child_ = node;
            } else {
                prev.next_ = node;
            }
            prev = node;
        }
    } finally {
        // finish the adoption
        for (Node? node = child_; node != Null; node = node.next_) {
            node.parent_ = this;
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
            if (ElementNode parent ?= this.parent) {
                // TODO
            }
            mod();
            super(newName);
        }
    }

    @Override
    String value {
        @Override
        String get() {
            if (child_ == Null) {
                return super();
            }

            // TODO
            TODO
        }

        @Override
        void set(String newValue) {
            String oldValue = this.value;
            if (newValue != oldValue) {
                // TODO remove all previous parts (or if the first one is a data part, rewrite it)
                // TODO add one data part
                mod();
                super(value);
            }
        }
    }

    @Override
    @RO List<Content> contents.get() = new ContentList(parts);

    // ----- internal ------------------------------------------------------------------------------

    /**
     * TODO no value vs. cached-only vs. contents-only vs. both
     */
    protected UInt32 partsMod_ = 0;

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
// TODO GG  return new Cursor() {
            return new List<Content>.Cursor() {
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