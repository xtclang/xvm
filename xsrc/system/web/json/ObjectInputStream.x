import collections.HashMap;

import io.Reader;
import io.ObjectInput;

import Lexer.Token;


/**
 * An [ObjectInput] implementation for JSON de-serialization that reads from a [Reader] or from a
 * stream of JSON tokens.
 */
class ObjectInputStream(Schema schema, Iterator<Token> lexer)
        implements ObjectInput
    {
    /**
     * Construct an ObjectInputStream from a [Reader].
     *
     * @param schema  the JSON `Schema` to use
     * @param reader  the `Reader` to read from
     */
    construct(Schema schema, Reader reader)
        {
        construct ObjectInputStream(schema, new Lexer(reader));
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON [Schema].
     */
    public/private Schema schema;

    /**
     * The underlying stream of JSON tokens.
     */
    protected/private Iterator<Token> lexer;

    /**
     * The root element.
     */
    protected/private ElementInputStream? root;

    /**
     * The current [DocInput] "node" (an element, array, or field) that is being read from.
     */
    protected/private DocInputStream<>? current;

    /**
     * Set to True after the stream is closed.
     */
    protected/private Boolean closed;

    /**
     * A cache of all of the previously deserialized objects indexed by their JSON pointers.
     */
    @Lazy
    protected/private Map<String, Object> pointers.calc()
        {
        return new HashMap();
        }


    // ----- ObjectInput implementation -----------------------------------------------------------

    @Override
    <ObjectType> ObjectType read<ObjectType>()
        {
        assert !closed;
        assert root == Null;

        try (ElementInputStream in = new ElementInputStream(Null))
            {
            root    = in;
            current = in;

            return in.read<ObjectType>();
            }
        finally
            {
            root    = Null;
            current = Null;
            }
        }

    @Override
    void close()
        {
        root?.close();
        root    = Null;
        current = Null;
        closed  = True;
        }


    // ----- DocInputStream -----------------------------------------------------------------------

    /**
     * Base virtual child implementation for the various DocInput / ElementInput / FieldInput
     * implementations.
     */
    class DocInputStream<ParentInput extends (ElementInputStream | ArrayInputStream | FieldInputStream)?>
            implements DocInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id = Null)
            {
            this.parent = parent;
            this.id     = id;
            }

        @Override
        @RO Schema schema.get()
            {
            return this.ObjectInputStream.schema;
            }

        @Override
        Version? version;

        @Override
        Doc metadataFor(String attribute)
            {
            TODO
            }

        @Override
        public/protected Boolean canRead = True;

        @Override
        public/private ParentInput parent;

        /**
         * If the DocInput is inside a JSON object, then it has a field name.
         * If the DocInput is inside a JSON array, then it has an array index.
         */
        protected/private (String | Int)? id;

        @Override
        conditional ElementInputStream<ParentInput> insideElement()
            {
            return False;
            }

        @Override
        conditional ArrayInputStream<ParentInput> insideArray()
            {
            return False;
            }

        @Override
        conditional FieldInputStream<ParentInput> insideObject()
            {
            return False;
            }

        @Override
        String pointer.get()
            {
            return buildPointer(0).toString();
            }

        @Override
        <Serializable> Serializable dereference<Serializable>(String pointer)
            {
            TODO
            }

        @Override
        ParentInput close()
            {
            assert &this == &current;

            // close this node, and make the parent node into the current node
            canRead = False;
            ParentInput parent = this.parent;
            current = parent;
            return parent;
            }

        // ----- internal ----------------------------------------------------------------------

        /**
         * Determine if the specified `DocInput` is a "parent" (or grandparent etc.) of this
         * `DocInput`.
         *
         * @param stream  the `DocInputStream` from which this may be a descendant
         *
         * @return `True` iff this is a descendant of the specified `DocInputStream`
         */
        Boolean hasParent(DocInputStream!<> stream)
            {
            ParentInput parent = this.parent;
            if (&stream == &parent)
                {
                return True;
                }

            return parent != Null && parent.hasParent(stream);
            }

        /**
         * If this is not the "current" `DocInputStream`, then automatically close any
         * descendant `DocInputStream` instances until this is the current one.
         *
         * @throws IllegalState if this `DocInputStream` is not the current one, nor could
         *         become the current one by closing any number of other `DocInputStream`
         *         instances
         */
        protected void ensureActive()
            {
            if (&this == &current)
                {
                return;
                }

            DocInputStream!<>? current = this.ObjectInputStream.current;
            assert current != Null;
            assert current.hasParent(this);

            do
                {
                current = current.close();
                assert current != Null;
                }
            while (&this != &current);
            }

        protected void prepareRead()
            {
            assert canRead;
            ensureActive();
            }

        /**
         * Invoked when a child is reading.
         */
        protected void childReading()
            {
            }

        /**
         * If the DocInput is inside a JSON object, then it has a field name.
         */
        protected conditional String named()
            {
            (String | Int)? id = this.id;
            return id.is(String) ? (True, id) : False;
            }

        /**
         * If the DocInput is inside a JSON array, then it has an array index.
         */
        protected conditional Int indexed()
            {
            (String | Int)? id = this.id;
            return id.is(Int) ? (True, id) : False;
            }

        /**
         * Build the JSON pointer that represents the entire path to this node from the root object.
         *
         * @see [RFC 6901 §5](https://tools.ietf.org/html/rfc6901#section-5)
         */
        protected StringBuffer buildPointer(Int length)
            {
            Stringable? token  = id;
            Boolean     escape = False;
            if (token != Null)
                {
                length += 1 + token.estimateStringLength();
                if (token.is(String))
                    {
                    for (Char ch : token)
                        {
                        if (ch == '~' || ch == '/')
                            {
                            ++length;
                            escape = True;
                            }
                        }
                    }
                }

            StringBuffer buf = parent?.buildPointer(length) : new StringBuffer(length);
            if (token != Null)
                {
                buf.add('/');
                if (escape)
                    {
                    // "~" and "/" need to be converted to "~0" and "~1" respectively
                    for (Char ch : token.as(String))
                        {
                        if (ch == '~')
                            {
                            "~0".appendTo(buf);
                            }
                        else if (ch == '/')
                            {
                            "~1".appendTo(buf);
                            }
                        else
                            {
                            buf.add(ch);
                            }
                        }
                    }
                else
                    {
                    token.appendTo(buf);
                    }
                }
            return buf;
            }
        }

    /**
     * Virtual child mixin "cap" required for all ElementInput / FieldInput implementations.
     */
    mixin CloseCap
            into DocInputStream
        {
        construct()
            {
            }
        finally
            {
            current = this;
            }

        @Override
        ParentInput close()
            {
            if (&this != &current)
                {
                if (!current?.hasParent(this) : True)
                    {
                    // this has already been closed
                    return parent;
                    }

                // close the children of this until this becomes the active node
                ensureActive();
                }

            return super();
            }
        }


    // ----- ElementInputStream -------------------------------------------------------------------

    /**
     * The ElementInputStream is an implementation of [ElementInput] that represents the reading
     * of a single value (which in turn may be an array or object).
     */
    class ElementInputStream<ParentInput extends (ElementInputStream | ArrayInputStream | FieldInputStream)?>
            extends DocInputStream<ParentInput>
            implements ElementInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id = Null)
            {
            construct DocInputStream(parent, id);
            assert token := lexer.next();
            }

        /**
         * The initial token of the element.
         */
        protected/private Token token;

        @Override
        conditional ElementInputStream insideElement()
            {
            return True, this;
            }

        @Override
        ArrayInputStream<ElementInputStream> openArray()
            {
            prepareRead();
            if (token.id != ArrayEnter)
                {
                throw new IllegalJSON($"Illegal token for start of array: {token}");
                }

            canRead = False;
            return new @CloseCap ArrayInputStream(this);
            }

        @Override
        FieldInputStream<ElementInputStream> openObject()
            {
            prepareRead();
            if (token.id != ObjectEnter)
                {
                throw new IllegalJSON($"Illegal token for start of object: {token}");
                }

            canRead = False;
            return new @CloseCap FieldInputStream(this);
            }

        @Override
        Boolean isNull()
            {
            return token.id == NoVal;
            }

        @Override
        Doc readDoc()
            {
            prepareRead();
            canRead = False;

            switch (token.id)
                {
                case NoVal:
                case BoolVal:
                case IntVal:
                case FPVal:
                case StrVal:
                    return token.value;

                case ArrayEnter:
                case ObjectEnter:
                    assert Doc doc := new Parser(lexer, token).next();
                    return doc;

                default:
                    throw new IllegalJSON($"Illegal token for start of value: {token}");
                }
            }

        @Override
        ParentInput close()
            {
            // if nothing was read, then exhaust the contents of the element
            if (canRead)
                {
                switch (token.id)
                    {
                    case NoVal:
                    case BoolVal:
                    case IntVal:
                    case FPVal:
                    case StrVal:
                        // nothing to expurgate
                        break;

                    case ArrayEnter:
                    case ObjectEnter:
                        new Parser(lexer, token).skip();
                        break;

                    default:
                        throw new IllegalJSON($"Illegal token for start of value: {token}");
                    }

                canRead = False;
                }

            return super();
            }
        }


    // ----- ArrayInputStream ---------------------------------------------------------------------

    /**
     * The ArrayInputStream is an implementation of [ElementInput] that represents the reading of
     * a sequence of values into a JSON array (each of which may in turn be a single JSON value, or
     * an array, or an object).
     */
    class ArrayInputStream<ParentInput extends (ElementInputStream | ArrayInputStream | FieldInputStream)?>
            extends DocInputStream<ParentInput>
            implements ElementInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id = Null)
            {
            construct DocInputStream(parent, id);
            }
        finally
            {
//            prepareRead();
//            reader.add('[');
            }

        /**
         * Count of how many elements have been added to the JSON array.
         */
        protected Int count;

//        @Override
//        void childReading() // TODO CP make sure that null elements are not omitted
//            {
//            if (count++ > 0)
//                {
//                reader.add(',');
//                }
//            }

        @Override
        conditional ArrayInputStream insideArray()
            {
            return True, this;
            }

        @Override
        ArrayInputStream!<ArrayInputStream> openArray()
            {
            ensureActive();
            return new @CloseCap ArrayInputStream(this, count);
// TODO
//            return schema.enablePointers
//                    ? new @CloseCap @PointerAwareElementInput ArrayInputStream(this, count)
//                    : new @CloseCap ArrayInputStream(this, count);
            }

        @Override
        FieldInputStream<ArrayInputStream> openObject()
            {
            ensureActive();
            return new @CloseCap FieldInputStream(this, count);
// TODO
//            return schema.enablePointers
//                    ? new @CloseCap @PointerAwareFieldInput FieldInputStream(this, count)
//                    : new @CloseCap FieldInputStream(this, count);
            }

        @Override
        Boolean isNull()
            {
            return False; // TODO
            }

        @Override
        Doc readDoc()
            {
            return Null; // TODO
            }

//        @Override
//        ArrayInputStream add(Doc value)
//            {
//            ensureActive();
//
//            // this is analogous to opening a nested ElementInputStream and having it read, except
//            // the ArrayInputStream incorporates that functionality at this level
//            childReading();
//            Printer.DEFAULT.print(value, reader);
//
//            return this;
//            }

        @Override
        ParentInput close()
            {
//            reader.add(']');
            return super();
            }
        }


    // ----- FieldInputStream ---------------------------------------------------------------------

    /**
     * The FieldInputStream is an implementation of [FieldInput] that represents the reading of
     * a sequence of name/value pairs into a JSON object (each value of which may in turn be a
     * single JSON value, or an array, or an object).
     */
    class FieldInputStream<ParentInput extends (ElementInputStream | ArrayInputStream | FieldInputStream)?>
            extends DocInputStream<ParentInput>
            implements FieldInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id = Null)
            {
            construct DocInputStream(parent, id);
            }
        finally
            {
//            prepareRead();
//            reader.add('{');
            }

//        Boolean first = True;
//
//        @Override
//        void childReading()
//            {
//            if (first)
//                {
//                first = False;
//                }
//            else
//                {
//                reader.add(',');
//                }
//            }

        @Override
        conditional FieldInputStream insideObject()
            {
            return True, this;
            }

        @Override
        ElementInputStream<FieldInputStream> openField(String name)
            {
            ensureActive();
            return new @CloseCap ElementInputStream(this, name);
// TODO
//            return schema.enablePointers
//                    ? new @CloseCap @PointerAwareElementInput ElementInputStream(this, name)
//                    : new @CloseCap ElementInputStream(this, name);
            }

        @Override
        ArrayInputStream<FieldInputStream> openArray(String name)
            {
            ensureActive();
            return new @CloseCap ArrayInputStream(this, name);
// TODO
//            return schema.enablePointers
//                    ? new @CloseCap @PointerAwareElementInput ArrayInputStream(this, name)
//                    : new @CloseCap ArrayInputStream(this, name);
            }

        @Override
        FieldInputStream!<FieldInputStream> openObject(String name)
            {
            ensureActive();
            return new @CloseCap FieldInputStream(this, name);
// TODO
//            return schema.enablePointers
//                    ? new @CloseCap @PointerAwareFieldInput FieldInputStream(this, name)
//                    : new @CloseCap FieldInputStream(this, name);
            }

        @Override
        conditional String nextName()
            {
            TODO
            }

        @Override
        Boolean contains(String name)
            {
            TODO
            }

        @Override
        Map<String, Doc>? takeRemainder()
            {
            TODO
            }

        @Override
        Boolean isNull(String name)
            {
            TODO
            }

        @Override
        Doc readDoc(String name, Doc defaultValue = Null)
            {
            TODO
            }

        @Override
        ParentInput close()
            {
//            reader.add('}');
            return super();
            }
        }


//    // ----- Pointer support -----------------------------------------------------------------------
//
//    /**
//     * The shared base implementation support for pointers in an ObjectInputStream.
//     */
//    mixin PointerAwareDocInput
//            into DocInputStream
//        {
//        /**
//         * Recursion indicator: Only writes from the outside are "de-dup'd" using pointers, and this
//         * flag allows the implementation to differentiate between the writes that originate from
//         * outside, versus various writes that originate internally (for example, when relying
//         * internally on the `Doc`-based write methods).
//         */
//        protected Boolean inside = False;
//
//        @Override
//        <Serializable> conditional String findPointer(Serializable object)
//            {
//            return pointers.get(&object.identity);
//            }
//
//        /**
//         * Determine if the specified object has already been written and a pointer registered for
//         * it, in which case, write the pointer String instead of the full object; otherwise, write
//         * the full object value.
//         *
//         * @param value         the value to write
//         * @param writePointer  the function to delegate to in order to write out the pointer only
//         * @param writeValue    the function to delegate to in order to write the full object value
//         *
//         * @return this
//         */
//        protected PointerAwareDocInput writePointerOrValue(Object value,
//                                                            function void(String) writePointer,
//                                                            function void() writeValue)
//            {
//            Boolean alreadyInside = inside;
//            if (alreadyInside || value.is(Primitive))
//                {
//                writeValue();
//                return this;
//                }
//
//            try
//                {
//                inside = True;
//
//                if (String pointer := pointers.get(&value.identity))
//                    {
//                    writePointer(pointer);
//                    }
//                else
//                    {
//                    registerPointer(value);
//                    writeValue();
//                    }
//                }
//            finally
//                {
//                inside = alreadyInside;
//                }
//
//            return this;
//            }
//
//        /**
//         * Given a value that will be added to this DocInput, associate the value with the pointer
//         * to this DocInput.
//         */
//        protected void registerPointer(Object value)
//            {
//            if (!value.is(Primitive))
//                {
//                pointers.putIfAbsent(&value.identity, pointer);
//                }
//            }
//        }
//
//    /**
//     * This is the pointer-aware implementation of the `ElementInput` interface.
//     */
//    mixin PointerAwareElementInput
//            into (ElementInputStream | ArrayInputStream)
//            extends PointerAwareDocInput
//        {
//        protected void addPointerReference(String pointer)
//            {
//            using (val in = openObject())
//                {
//                in.add("$ref", pointer);
//                }
//            }
//
//        @Override
//        PointerAwareElementInput add(Doc value)
//            {
//            return writePointerOrValue(value, &addPointerReference(_), &super(value));
//            }
//
//        @Override
//        <Serializable> PointerAwareElementInput addObject(Serializable value)
//            {
//            return writePointerOrValue(value, &addPointerReference(_), &super(value));
//            }
//
//        @Override
//        PointerAwareElementInput addArray(Iterable<Doc> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(_), &super(values));
//            }
//
//        @Override
//        PointerAwareElementInput addArray(Iterable<IntNumber> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(_), &super(values));
//            }
//
//        @Override
//        PointerAwareElementInput addArray(Iterable<FPNumber> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(_), &super(values));
//            }
//
//        @Override
//        <Serializable> PointerAwareElementInput addObjectArray(Iterable<Serializable> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(_), &super(values));
//            }
//        }
//
//    /**
//     * This is the pointer-aware implementation of the `FieldInput` interface.
//     */
//    mixin PointerAwareFieldInput
//            into FieldInputStream
//            extends PointerAwareDocInput
//        {
//        protected void addPointerReference(String name, String pointer)
//            {
//            using (val in = openObject(name))
//                {
//                in.add("$ref", pointer);
//                }
//            }
//
//        @Override
//        PointerAwareFieldInput add(String name, Doc value)
//            {
//            return writePointerOrValue(value, &addPointerReference(name, _), &super(name, value));
//            }
//
//        @Override
//        <Serializable> PointerAwareFieldInput addObject(String name, Serializable value)
//            {
//            return writePointerOrValue(value, &addPointerReference(name, _), &super(name, value));
//            }
//
//        @Override
//        PointerAwareFieldInput addArray(String name, Iterable<Doc> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(name, _), &super(name, values));
//            }
//
//        @Override
//        PointerAwareFieldInput addArray(String name, Iterable<IntNumber> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(name, _), &super(name, values));
//            }
//
//        @Override
//        PointerAwareFieldInput addArray(String name, Iterable<FPNumber> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(name, _), &super(name, values));
//            }
//
//        @Override
//        <Serializable> PointerAwareFieldInput addObjectArray(String name, Iterable<Serializable> values)
//            {
//            return writePointerOrValue(values, &addPointerReference(name, _), &super(name, values));
//            }
//        }
    }
