import ecstasy.io.Writer;
import ecstasy.io.ObjectOutput;


/**
 * An [ObjectOutput] implementation for JSON serialization that emits the serialized form directly
 * to an underlying [Writer].
 */
class ObjectOutputStream(Schema schema, Writer writer)
        implements ObjectOutput
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON [Schema].
     */
    public/private Schema schema;

    /**
     * The underlying [Writer] to write the JSON data to.
     */
    protected/private Writer writer;

    /**
     * The root element.
     */
    protected/private ElementOutputStream? root;

    /**
     * The current [DocOutput] "node" (an element, array, or field) that is being written.
     */
    protected/private DocOutputStream<>? current;

    /**
     * Set to True after the stream is closed.
     */
    protected/private Boolean closed;

    /**
     * A cache of all of the previously serialized objects, and their corresponding JSON pointers.
     */
    @Lazy
    protected/private Map<Ref.Identity, String> pointers.calc()
        {
        return new HashMap();
        }

    /**
     * (Temporary method)
     *
     * @return an empty ElementOutput implementation
     */
    ElementOutputStream createElementOutput()
        {
        return new @CloseCap ElementOutputStream<Nullable>(Null) ;
        }


    // ----- ObjectOutput implementation -----------------------------------------------------------

    @Override
    <ObjectType> void write(ObjectType value)
        {
        assert !closed;
        assert root == Null;

        try (ElementOutputStream out = createElementOutput())
            {
            root    = out;
            current = out;

            out.addObject(value);
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


    // ----- DocOutputStream -----------------------------------------------------------------------

    /**
     * Base virtual child implementation for the various DocOutput / ElementOutput / FieldOutput
     * implementations.
     */
    class DocOutputStream<ParentOutput extends (ElementOutputStream | ArrayOutputStream | FieldOutputStream)?>
            implements DocOutput<ParentOutput>
        {
        construct(ParentOutput parent, (String|Int)? id = Null)
            {
            this.parent = parent;
            this.id     = id;
            }

        @Override
        @RO Schema schema.get()
            {
            return this.ObjectOutputStream.schema;
            }

        @Override
        public/protected Boolean canWrite = True;

        @Override
        public/private ParentOutput parent;

        /**
         * If the DocOutput is inside a JSON object, then it has a field name.
         * If the DocOutput is inside a JSON array, then it has an array index.
         */
        protected/private (String | Int)? id;

        @Override
        conditional ElementOutputStream<ParentOutput> insideElement()
            {
            return False;
            }

        @Override
        conditional ArrayOutputStream<ParentOutput> insideArray()
            {
            return False;
            }

        @Override
        conditional FieldOutputStream<ParentOutput> insideObject()
            {
            return False;
            }

        @Override
        String pointer.get()
            {
            return buildPointer(0).toString();
            }

        @Override
        <Serializable> conditional String findPointer(Serializable object)
            {
            // this is only implemented by the PointerAware mixins
            return False;
            }

        @Override
        ParentOutput close()
            {
            assert &this == &current;

            // close this node, and make the parent node into the current node
            canWrite = False;
            ParentOutput parent = this.parent;
            current = parent;
            return parent;
            }

        // ----- internal ----------------------------------------------------------------------

        /**
         * Determine if the specified `DocOutput` is a "parent" (or grandparent etc.) of this
         * `DocOutput`.
         *
         * @param stream  the `DocOutputStream` from which this may be a descendant
         *
         * @return `True` iff this is a descendant of the specified `DocOutputStream`
         */
        Boolean hasParent(DocOutputStream!<> stream)
            {
            ParentOutput parent = this.parent;
            if (&stream == &parent)
                {
                return True;
                }

            return parent != Null && parent.hasParent(stream);
            }

        /**
         * If this is not the "current" `DocOutputStream`, then automatically close any
         * descendant `DocOutputStream` instances until this is the current one.
         *
         * @throws IllegalState if this `DocOutputStream` is not the current one, nor could
         *         become the current one by closing any number of other `DocOutputStream`
         *         instances
         */
        protected void ensureActive()
            {
            DocOutputStream<>? cur = current;
            if (&this == &cur)
                {
                return;
                }

            DocOutputStream!<>? current = this.ObjectOutputStream.current;
            assert current != Null;
            assert current.hasParent(this);

            do
                {
                current = current.close();
                assert current != Null;
                }
            while (&this != &current);
            }

        protected void prepareWrite()
            {
            assert canWrite;
            ensureActive();
            parent?.childWriting();
            if (String name := named())
                {
                Printer.printString(name, writer);
                writer.add(':');
                }
            }

        /**
         * Invoked when a child is writing.
         */
        protected void childWriting()
            {
            }

        /**
         * If the DocOutput is inside a JSON object, then it has a field name.
         */
        protected conditional String named()
            {
            (String | Int)? id = this.id;
            return id.is(String) ? (True, id) : False;
            }

        /**
         * If the DocOutput is inside a JSON array, then it has an array index.
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
            Stringable? token = id;
            (Int add, Boolean escape) = calculatePointerSegmentLength(token);
            length += add;

            StringBuffer buf = parent?.buildPointer(length) : new StringBuffer(length);
            return appendPointerSegment(buf, token, escape);
            }

        protected (Int length, Boolean escape) calculatePointerSegmentLength(Stringable? token)
            {
            Int     length = 0;
            Boolean escape = False;
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
            return length, escape;
            }

        protected StringBuffer appendPointerSegment(StringBuffer buf, Stringable? token, Boolean checkEscapes)
            {
            if (token != Null)
                {
                buf.add('/');
                if (checkEscapes)
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
     * Virtual child mixin "cap" required for all ElementOutput / FieldOutput implementations.
     */
    mixin CloseCap
            into DocOutputStream
        {
        construct()
            {
            }
        finally
            {
            current = this;
            }

        @Override
        ParentOutput close()
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


    // ----- ElementOutputStream -------------------------------------------------------------------

    /**
     * The ElementOutputStream is an implementation of [ElementOutput] that represents the writing
     * of a single value (which in turn may be an array or object).
     */
    class ElementOutputStream<ParentOutput extends (ElementOutputStream | ArrayOutputStream | FieldOutputStream)?>
            extends DocOutputStream<ParentOutput>
            implements ElementOutput<ParentOutput>
        {
        construct(ParentOutput parent, (String|Int)? id = Null)
            {
            construct DocOutputStream(parent, id);
            }

        @Override
        conditional ElementOutputStream insideElement()
            {
            return True, this;
            }

        @Override
        ArrayOutputStream<ElementOutputStream> openArray()
            {
            prepareWrite();
            canWrite = False;
            return schema.enablePointers
                    ? new @CloseCap @PointerAwareElementOutput ArrayOutputStream(this)
                    : new @CloseCap ArrayOutputStream(this);
            }

        @Override
        FieldOutputStream<ElementOutputStream> openObject()
            {
            prepareWrite();
            canWrite = False;
            return schema.enablePointers
                    ? new @CloseCap @PointerAwareFieldOutput FieldOutputStream(this)
                    : new @CloseCap FieldOutputStream(this);
            }

        @Override
        ElementOutputStream add(Doc value)
            {
            if (value != Null || schema.retainNulls || !parent.is(FieldOutputStream))
                {
                prepareWrite();
                Printer.DEFAULT.print(value, writer);
                }

            canWrite = False;
            return this;
            }

        // TODO potential optimizations
        // ElementOutputStream add(IntNumber value)
        // ElementOutputStream add(FPNumber value)
        // <Serializable> ElementOutputStream addObject(Serializable value);
        // ElementOutputStream addArray(Iterable<Doc> values)
        // ElementOutputStream addArray(Iterable<IntNumber> values)
        // ElementOutputStream addArray(Iterable<FPNumber> values)
        // <Serializable> ElementOutputStream addObjectArray(Iterable<Serializable> values);

        @Override
        ParentOutput close()
            {
            // if nothing was written, assume that the value was supposed to be `Null`
            if (canWrite)
                {
                add(Null);
                }

            return super();
            }
        }


    // ----- ArrayOutputStream ---------------------------------------------------------------------

    /**
     * The ArrayOutputStream is an implementation of [ElementOutput] that represents the writing of
     * a sequence of values into a JSON array (each of which may in turn be a single JSON value, or
     * an array, or an object).
     */
    class ArrayOutputStream<ParentOutput extends (ElementOutputStream | ArrayOutputStream | FieldOutputStream)?>
            extends DocOutputStream<ParentOutput>
            implements ElementOutput<ParentOutput>
        {
        construct(ParentOutput parent, (String|Int)? id = Null)
            {
            construct DocOutputStream(parent, id);
            }
        finally
            {
            prepareWrite();
            writer.add('[');
            }

        /**
         * Count of how many elements have been added to the JSON array.
         */
        protected Int count;

        @Override
        void childWriting() // TODO CP make sure that null elements are not omitted
            {
            if (count++ > 0)
                {
                writer.add(',');
                }
            }

        @Override
        conditional ArrayOutputStream insideArray()
            {
            return True, this;
            }

        @Override
        ArrayOutputStream!<ArrayOutputStream> openArray()
            {
            ensureActive();
            return schema.enablePointers
                    ? new @CloseCap @PointerAwareElementOutput ArrayOutputStream(this, count)
                    : new @CloseCap ArrayOutputStream(this, count);
            }

        @Override
        FieldOutputStream<ArrayOutputStream> openObject()
            {
            ensureActive();
            return schema.enablePointers
                    ? new @CloseCap @PointerAwareFieldOutput FieldOutputStream(this, count)
                    : new @CloseCap FieldOutputStream(this, count);
            }

        @Override
        ArrayOutputStream add(Doc value)
            {
            ensureActive();

            // this is analogous to opening a nested ElementOutputStream and having it write, except
            // the ArrayOutputStream incorporates that functionality at this level
            childWriting();
            Printer.DEFAULT.print(value, writer);

            return this;
            }

        // TODO potential optimizations
        // ArrayOutputStream add(IntNumber value)
        // ArrayOutputStream add(FPNumber value)
        // <Serializable> ArrayOutputStream addObject(Serializable value);
        // ArrayOutputStream addArray(Iterable<Doc> values)
        // ArrayOutputStream addArray(Iterable<IntNumber> values)
        // ArrayOutputStream addArray(Iterable<FPNumber> values)
        // <Serializable> ArrayOutputStream addObjectArray(Iterable<Serializable> values);

        @Override
        ParentOutput close()
            {
            writer.add(']');
            return super();
            }
        }


    // ----- FieldOutputStream ---------------------------------------------------------------------

    /**
     * The FieldOutputStream is an implementation of [FieldOutput] that represents the writing of
     * a sequence of name/value pairs into a JSON object (each value of which may in turn be a
     * single JSON value, or an array, or an object).
     */
    class FieldOutputStream<ParentOutput extends (ElementOutputStream | ArrayOutputStream | FieldOutputStream)?>
            extends DocOutputStream<ParentOutput>
            implements FieldOutput<ParentOutput>
        {
        construct(ParentOutput parent, (String|Int)? id = Null)
            {
            construct DocOutputStream(parent, id);
            }
        finally
            {
            prepareWrite();
            writer.add('{');
            }

        Boolean first = True;

        @Override
        void childWriting()
            {
            if (first)
                {
                first = False;
                }
            else
                {
                writer.add(',');
                }
            }

        @Override
        conditional FieldOutputStream insideObject()
            {
            return True, this;
            }

        @Override
        ElementOutputStream<FieldOutputStream> openField(String name)
            {
            ensureActive();
            return schema.enablePointers
                    ? new @CloseCap @PointerAwareElementOutput ElementOutputStream(this, name)
                    : new @CloseCap ElementOutputStream(this, name);
            }

        @Override
        ArrayOutputStream<FieldOutputStream> openArray(String name)
            {
            ensureActive();
            return schema.enablePointers
                    ? new @CloseCap @PointerAwareElementOutput ArrayOutputStream(this, name)
                    : new @CloseCap ArrayOutputStream(this, name);
            }

        @Override
        FieldOutputStream!<FieldOutputStream> openObject(String name)
            {
            ensureActive();
            return schema.enablePointers
                    ? new @CloseCap @PointerAwareFieldOutput FieldOutputStream(this, name)
                    : new @CloseCap FieldOutputStream(this, name);
            }

        // TODO potential optimizations
        // FieldOutputStream add(String name, Doc value)
        // FieldOutputStream add(String name, IntNumber value)
        // FieldOutputStream add(String name, FPNumber value)
        // <Serializable> FieldOutputStream addObject(String name, Serializable value);
        // <Serializable> FieldOutputStream addObjectArray(String name, Iterable<Serializable> values);

        @Override
        ParentOutput close()
            {
            writer.add('}');
            return super();
            }
        }


    // ----- Pointer support -----------------------------------------------------------------------

    /**
     * The shared base implementation support for pointers in an ObjectOutputStream.
     */
    mixin PointerAwareDocOutput
            into DocOutputStream
        {
        /**
         * Recursion indicator: Only writes from the outside are "de-dup'd" using pointers, and this
         * flag allows the implementation to differentiate between the writes that originate from
         * outside, versus various writes that originate internally (for example, when relying
         * internally on the `Doc`-based write methods).
         */
        protected Boolean inside = False;

        @Override
        <Serializable> conditional String findPointer(Serializable object)
            {
            return pointers.get(&object.identity);
            }

        /**
         * Determine if the specified object has already been written and a pointer registered for
         * it, in which case, write the pointer String instead of the full object; otherwise, write
         * the full object value.
         *
         * @param value         the value to write
         * @param writePointer  the function to delegate to in order to write out the pointer only
         * @param writeValue    the function to delegate to in order to write the full object value
         *
         * @return this
         */
        protected PointerAwareDocOutput writePointerOrValue((String | Int)?       id,
                                                            Object                value,
                                                            function void(String) writePointer,
                                                            function void()       writeValue)
            {
            Boolean alreadyInside = inside;
            if (alreadyInside || value.is(Primitive))
                {
                writeValue();
                return this;
                }

            try
                {
                inside = True;

                if (String pointer := findPointer(value))
                    {
                    writePointer(pointer);
                    }
                else
                    {
                    writeValue();
                    if (!value.is(Primitive))
                        {
                        (Int length, Boolean escape) = calculatePointerSegmentLength(id);
                        pointer = appendPointerSegment(buildPointer(length), id, escape).toString();
                        if (pointer.size > 0)
                            {
                            pointers.putIfAbsent(&value.identity, pointer);
                            }
                        }
                    }
                }
            finally
                {
                inside = alreadyInside;
                }

            return this;
            }
        }

    /**
     * This is the pointer-aware implementation of the `ElementOutput` interface.
     */
    mixin PointerAwareElementOutput
            into (ElementOutputStream | ArrayOutputStream)
            extends PointerAwareDocOutput
        {
        protected void addPointerReference(String pointer)
            {
            using (val out = openObject())
                {
                out.add("$ref", pointer);
                }
            }

        Int? nextId.get()
            {
            return this.is(ArrayOutputStream) ? this.count : Null;
            }

        @Override
        PointerAwareElementOutput add(Doc value)
            {
            return writePointerOrValue(nextId, value, &addPointerReference(_), &super(value));
            }

        @Override
        <Serializable> PointerAwareElementOutput addObject(Serializable value)
            {
            return writePointerOrValue(nextId, value, &addPointerReference(_), &super(value));
            }

        @Override
        PointerAwareElementOutput addArray(Iterable<Doc> values)
            {
            return writePointerOrValue(nextId, values, &addPointerReference(_), &super(values));
            }

        @Override
        PointerAwareElementOutput addArray(Iterable<IntNumber> values)
            {
            return writePointerOrValue(nextId, values, &addPointerReference(_), &super(values));
            }

        @Override
        PointerAwareElementOutput addArray(Iterable<FPNumber> values)
            {
            return writePointerOrValue(nextId, values, &addPointerReference(_), &super(values));
            }

        @Override
        <Serializable> PointerAwareElementOutput addObjectArray(Iterable<Serializable> values)
            {
            return writePointerOrValue(nextId, values, &addPointerReference(_), &super(values));
            }
        }

    /**
     * This is the pointer-aware implementation of the `FieldOutput` interface.
     */
    mixin PointerAwareFieldOutput
            into FieldOutputStream
            extends PointerAwareDocOutput
        {
        protected void addPointerReference(String name, String pointer)
            {
            using (val out = openObject(name))
                {
                out.add("$ref", pointer);
                }
            }

        @Override
        PointerAwareFieldOutput add(String name, Doc value)
            {
            return writePointerOrValue(name, value, &addPointerReference(name, _), &super(name, value));
            }

        @Override
        <Serializable> PointerAwareFieldOutput addObject(String name, Serializable value)
            {
            return writePointerOrValue(name, value, &addPointerReference(name, _), &super(name, value));
            }

        @Override
        PointerAwareFieldOutput addArray(String name, Iterable<Doc> values)
            {
            return writePointerOrValue(name, values, &addPointerReference(name, _), &super(name, values));
            }

        @Override
        PointerAwareFieldOutput addArray(String name, Iterable<IntNumber> values)
            {
            return writePointerOrValue(name, values, &addPointerReference(name, _), &super(name, values));
            }

        @Override
        PointerAwareFieldOutput addArray(String name, Iterable<FPNumber> values)
            {
            return writePointerOrValue(name, values, &addPointerReference(name, _), &super(name, values));
            }

        @Override
        <Serializable> PointerAwareFieldOutput addObjectArray(String name, Iterable<Serializable> values)
            {
            return writePointerOrValue(name, values, &addPointerReference(name, _), &super(name, values));
            }
        }
    }