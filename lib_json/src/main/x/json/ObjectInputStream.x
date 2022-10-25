import ecstasy.io.Reader;
import ecstasy.io.ObjectInput;

import Lexer.Token;


/**
 * An [ObjectInput] implementation for JSON de-serialization that reads from a [Reader], or from a
 * stream of JSON tokens, or from a JSON parser.
 *
 * @param schema  the JSON `Schema` to use
 * @param parser  the `Parser` to use to parse JSON documents
 */
class ObjectInputStream(Schema schema, Parser parser)
        implements ObjectInput
    {
    /**
     * Construct an ObjectInputStream from a [Reader].
     *
     * @param schema  the JSON `Schema` to use
     * @param reader  the `Reader` to read JSON text from
     */
    construct(Schema schema, Reader reader)
        {
        construct ObjectInputStream(schema, new Lexer(reader));
        }

    /**
     * Construct an ObjectInputStream from a [Lexer].
     *
     * @param schema  the JSON `Schema` to use
     * @param lexer  the `Lexer` to obtain JSON tokens from
     */
    construct(Schema schema, Iterator<Token> lexer)
        {
        construct ObjectInputStream(schema, new Parser(lexer.ensureMarkable()));
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON [Schema].
     */
    public/private Schema schema;

    /**
     * The underlying JSON parser, which uses a look-ahead of exactly one token.
     */
    protected/private Parser parser;

    /**
     * The root element.
     */
    protected/private RootInputStream? root;

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
    protected Map<String, Object> pointers
        {
        @Override
        Map<String, Object> calc()
            {
            return new HashMap();
            }

        /**
         * Clear any pointer information that has been collected.
         */
        void reset()
            {
            if (assigned)
                {
                get().clear();
                }
            }
        }

    /**
     * (Temporary method)
     *
     * Clear any pointer information that has been collected.
     */
    protected void resetPointers()
        {
        if (&pointers.assigned)
            {
            pointers.clear();
            }
        }

    /**
     * Create the root ElementInput to read JSON data from the ObjectInputStream.
     *
     * @return the root ElementInput
     */
    RootInputStream ensureElementInput()
        {
        assert !closed;
        return root ?: new @CloseCap RootInputStream();
        }


    // ----- ObjectInput implementation ------------------------------------------------------------

    @Override
    <ObjectType> ObjectType read<ObjectType>()
        {
        return ensureElementInput().readObject<ObjectType>();
        }

    @Override
    void close(Exception? cause = Null)
        {
        root    = Null;
        current = Null;
        closed  = True;
        &pointers.reset();
        }


    // ----- DocInputStream ------------------------------------------------------------------------

    /**
     * The ObjectInputStream uses three specific JSON stream implementations internally to dissect
     * a stream of JSON tokens into the desired corresponding Ecstasy types, values, and structures.
     */
    typedef (ElementInputStream | ArrayInputStream | FieldInputStream) as AnyStream;

    /**
     * Base virtual child implementation for the various DocInput / ElementInput / FieldInput
     * implementations.
     */
    class DocInputStream<ParentInput extends AnyStream?>
            implements DocInput<ParentInput>
        {
        construct(ParentInput parent, String? id, Token[]? tokens)
            {
            this.parent = parent;
            this.id     = id;
            this.parser = new Parser(tokens?.iterator()) : parent?.parser : this.ObjectInputStream.parser;
            }
        finally
            {
            loadNext(first = True);
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
            return parent?.metadataFor(attribute) : Null;
            }

        @Override
        Doc peekMetadata(String attribute)
            {
            return Null;
            }

        @Override
        public/protected Boolean canRead = True;

        @Override
        public/private ParentInput parent;

        /**
         * If the DocInput is inside a JSON object, then it has a field name.
         * If the DocInput is inside a JSON array, then it has an array index.
         */
        protected/private String? id;

        /**
         * The underlying JSON parser, which uses a look-ahead of exactly one token.
         */
        protected/private Parser parser;

        /**
         * True iff this stream is being used to peek ahead for metadata.
         *
         * When peek-ahead is specified, the current ElementInput opens the element as a JSON
         * object, as if by openObject(), iff it appears that there is actually a JSON object
         * in the element. Since this is a relatively expensive operation, the resulting FieldInput
         * (which exists to read and provides the requested metadata) is held onto, but if the
         * caller then takes a different path (one that does not immediately create the FieldInput),
         * then the input stream must appear to have had no state changes whatsoever made to it by
         * the original call to "peekMetadata()", i.e. we must fully restore the previous state of
         * the input stream. On the other hand, if the caller does request the FieldInput, then the
         * previously created one is ready to go.
         */
        protected Boolean peekingAhead
            {
            @Override
            Boolean get()
                {
                return False;
                }

            @Override
            void set(Boolean newValue)
                {
                assert;
                }
            }

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
        <Serializable> Serializable dereference(String pointer)
            {
            assert schema.enablePointers;

            if (pointer.size == 0)
                {
                throw new IllegalJSON("Root document pointer (\"\") from node"
                        + $" \"{this.pointer}\" is not supported");
                }

            if (Int steps := pointer[0].asciiDigit())
                {
                // this is a relative pointer format
                Int    cur  = 1;
                Int    last = pointer.size - 1;
                String suffix;

                Loop: while (True)
                    {
                    if (cur > last)
                        {
                        // the pointer points to a node containing this node, which is not supported
                        throw new IllegalJSON($"Parent document pointer (\"{pointer}\") from node"
                                + $" \"{this.pointer}\" is not supported");
                        }

                    Char ch = pointer[cur];
                    if (Int n := ch.asciiDigit())
                        {
                        steps = steps * 10 + n;
                        cur++;
                        }
                    else
                        {
                        switch (ch)
                            {
                            case '/':
                                suffix = pointer[cur..last];
                                break Loop;

                            case '#':
                                throw new IllegalJSON("Index position / member name pointer"
                                        + $" \"{pointer}\" from node \"{this.pointer}\" is not supported");

                            default:
                                throw new IllegalJSON(
                                        $"Illegal pointer \"{pointer}\" from node \"{this.pointer}\"");
                            }
                        }
                    }

                DocInputStream!<> node = this;
                for (Int i = 0; i < steps; ++i)
                    {
                    node = node.parent ?: throw new IllegalJSON(
                            $"Illegal relative pointer \"{pointer}\" from node \"{this.pointer}\"");
                    }

                pointer = node.pointer + suffix;
                }

            if (Object value := pointers.get(pointer))
                {
                if (value.is(Serializable))
                    {
                    return value;
                    }

                throw new IllegalJSON(
                        $"Type mismatch for JSON pointer \"{pointer}\" from node \"{this.pointer}\""
                        + $"; required type={Serializable}, actual type={&value.actualType}");
                }

            throw new IllegalJSON($"Missing value for JSON pointer \"{pointer}\" from node"
                    + $" \"{this.pointer}\"; required type={Serializable}");
            }

        @Override
        ParentInput close(Exception? cause = Null)
            {
            if (canRead && !peekingAhead)
                {
                do
                    {
                    parser.skip();
                    }
                while (loadNext());
                }

            // close this node
            canRead = False;
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
            DocInputStream!<>? current = this.ObjectInputStream.current;
            if (&this == &current)
                {
                return;
                }

            assert current != Null;
            assert current.hasParent(this);
            do
                {
                current = current.close();
                assert current != Null;
                }
            while (&this != &current);
            }

        /**
         * Make sure that this DocInput can be read from, and is ready to read from.
         */
        protected void prepareRead()
            {
            assert canRead;
            ensureActive();
            }

        /**
         * Invoked when a child has closed.
         *
         * @param child  the child that closed
         */
        protected void childClosed(DocInputStream! child)
            {
            if (child.&parser == this.&parser && !child.peekingAhead)
                {
                loadNext();
                }
            }

        /**
         * Set up the first token of the "next" value.
         *
         * For an array, load the first token of the value of the next array element. If there are
         * no more elements, then set `canRead` to `False`.
         *
         * For an object, load the name and first token of the value of the next key-value pair. If
         * there are no more key-value pairs, then set `canRead` to `False`.
         *
         * Note: The assumption is that prepareRead() (or equivalent) has already been invoked.
         *
         * @return True iff the stream appears to contain a next element
         */
        protected Boolean loadNext(Boolean first = False)
            {
            if (!first)
                {
                canRead = False;
                }
            return first;
            }

        /**
         * If the DocInput is inside a JSON array, then it has an array index.
         */
        protected conditional Int indexed()
            {
            return False;
            }

        /**
         * Build the JSON pointer that represents the entire path to this node from the root object.
         *
         * @see [RFC 6901 §5](https://tools.ietf.org/html/rfc6901#section-5)
         */
        protected StringBuffer buildPointer(Int length)
            {
            String? name   = id;
            Boolean escape = False;
            if (name != Null)
                {
                length += 1 + name.estimateStringLength();
                if (name.is(String))
                    {
                    for (Char ch : name)
                        {
                        if (ch == '~' || ch == '/')
                            {
                            ++length;
                            escape = True;
                            }
                        }
                    }
                }

            Int? index = Null;
            if (index := indexed())
                {
                length += 1 + index.estimateStringLength();
                }

            StringBuffer buf = parent?.buildPointer(length) : new StringBuffer(length);

            if (name != Null)
                {
                buf.add('/');
                if (escape)
                    {
                    // "~" and "/" need to be converted to "~0" and "~1" respectively
                    for (Char ch : name.as(String))
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
                    name.appendTo(buf);
                    }
                }

            if (index /*TODO GG != Null*/ .is(Int))
                {
                buf.add('/');
                index.appendTo(buf);
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
        ParentInput close(Exception? cause = Null)
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

            ParentInput parent = super(cause);
            current = parent?.as(DocInputStream!<>?) : Null;
            parent?.childClosed(this);
            return parent;
            }
        }


    // ----- ElementInputStream --------------------------------------------------------------------

    /**
     * The ElementInputStream is an implementation of [ElementInput] that represents the reading
     * of a single value (which in turn may be an array or object).
     */
    @PeekAhead
    class ElementInputStream<ParentInput extends AnyStream?>
            extends DocInputStream<ParentInput>
            implements ElementInput<ParentInput>
        {
        construct(ParentInput parent, String? id = Null, Token[]? tokens = Null)
            {
            super(parent, id, tokens);
            }

        @Override
        conditional ElementInputStream insideElement()
            {
            return True, this;
            }

        @Override
        ArrayInputStream<ElementInputStream> openArray()
            {
            prepareRead();
            canRead = False;
            return schema.enablePointers
                    ? new @CloseCap @PointerAware ArrayInputStream(this)
                    : new @CloseCap ArrayInputStream(this);
            }

        @Override
        FieldInputStream<ElementInputStream> openObject(Boolean peekAhead=False)
            {
            prepareRead();
            canRead = False;
            return new @CloseCap FieldInputStream(this, peekingAhead=peekAhead);
            }

        @Override
        Boolean isNull()
            {
            return parser.peek().id == NoVal;
            }

        @Override
        Doc readDoc()
            {
            prepareRead();
            canRead = False;
            return parser.parseDoc();
            }

        @Override
        protected void childClosed(DocInputStream! child)
            {
            if (child.peekingAhead)
                {
                canRead = True;
                }

            super(child);
            }
        }


    // ----- RootInputStream -----------------------------------------------------------------------

    /**
     * The RootInputStream is an implementation of [ElementInput] that represents reading an entire
     * JSON document, but unlike ElementInputStream, documents may be arranged in sequence.
     */
    class RootInputStream
            extends ElementInputStream<Nullable>
        {
        construct()
            {
            assert root == Null;
            super(Null);
            }
        finally
            {
            root = this;
            }

        @Override
        Boolean canRead.get()
            {
            return !closed && !parser.eof;
            }

        @Override
        void prepareRead()
            {
            super();
            &pointers.reset();
            }

        @Override
        ParentInput close(Exception? cause = Null)
            {
            super(cause);
            root = Null;
            return Null;
            }
        }


    // ----- ArrayInputStream ----------------------------------------------------------------------

    /**
     * The ArrayInputStream is an implementation of [ElementInput] that represents the reading of
     * a sequence of values into a JSON array (each of which may in turn be a single JSON value, or
     * an array, or an object).
     */
    @PeekAhead
    class ArrayInputStream<ParentInput extends AnyStream?>
            extends DocInputStream<ParentInput>
            implements ElementInput<ParentInput>
        {
        construct(ParentInput parent, String? id = Null, Token[]? tokens = Null)
            {
            super(parent, id, tokens);
            parser.expect(ArrayEnter);
            }

        /**
         * Count of how many elements have been read from the JSON array.
         */
        protected Int count;

        @Override
        conditional ArrayInputStream insideArray()
            {
            return True, this;
            }

        @Override
        protected conditional Int indexed()
            {
            return True, count;
            }

        @Override
        ArrayInputStream!<ArrayInputStream> openArray()
            {
            prepareRead();
            return schema.enablePointers
                    ? new @CloseCap @PointerAware ArrayInputStream(this)
                    : new @CloseCap ArrayInputStream(this);
            }

        @Override
        FieldInputStream<ArrayInputStream> openObject(Boolean peekAhead=False)
            {
            prepareRead();
            return new @CloseCap FieldInputStream(this, peekingAhead=peekAhead);
            }

        @Override
        Boolean isNull()
            {
            return !canRead || parser.peek().id == NoVal;
            }

        @Override
        Doc readDoc()
            {
            prepareRead();
            Doc doc = parser.parseDoc();
            loadNext();
            return doc;
            }

        @Override
        protected Boolean loadNext(Boolean first = False)
            {
            if (first)
                {
                if (parser.match(ArrayExit))
                    {
                    canRead = False;
                    return False;
                    }
                }
            else
                {
                ++count;
                if (!parser.match(Comma))
                    {
                    canRead = False;
                    parser.expect(ArrayExit);
                    return False;
                    }
                }

            return True;
            }
        }


    // ----- FieldInputStream ----------------------------------------------------------------------

    /**
     * The FieldInputStream is an implementation of [FieldInput] that represents the reading of
     * a sequence of name/value pairs into a JSON object (each value of which may in turn be a
     * single JSON value, or an array, or an object).
     */
    class FieldInputStream<ParentInput extends AnyStream?>
            extends DocInputStream<ParentInput>
            implements FieldInput<ParentInput>
        {
        construct(ParentInput parent, String? id = Null, Token[]? tokens = Null, Boolean peekingAhead = False)
            {
            super(parent, id, tokens);

            if (peekingAhead)
                {
                undo = parser.mark();
                }

            parser.expect(ObjectEnter);
            }

        /**
         * A singleton used to indicate that there is no peek-ahead undo marker.
         */
        private enum FakeMark {NotPeeking}

        /**
         * This is the parser mark to undo any impact that this stream has, if this stream is used
         * for peeking ahead and has to erase any signs of its work.
         */
        private immutable Object undo = FakeMark.NotPeeking;

        @Override
        protected Boolean peekingAhead
            {
            @Override
            Boolean get()
                {
                return undo != FakeMark.NotPeeking;
                }

            @Override
            void set(Boolean newValue)
                {
                if (newValue != get())
                    {
                    assert !newValue;
                    undo = FakeMark.NotPeeking;
                    }
                }
            }

        /**
         * The next loaded key name of the next key-value pair.
         */
        protected String? name;

        /**
         * A lazily constructed map of the name/value pairs that were skipped over. (The values are
         * stored as an array of their raw tokens.)
         */
        protected ListMap<String, Token[]>? skipped;

        /**
         * Take the `Token[]` value of the entry identified by the `String` key, removing the entry
         * in the process.
         */
        private static Token[] takeTokens(Map<String, Token[]>.Entry entry)
            {
            val result = entry.value;
            entry.delete();
            return result;
            }

        @Override
        conditional FieldInputStream insideObject()
            {
            return True, this;
            }

        @Override
        ElementInputStream<FieldInputStream> openField(String name)
            {
            prepareRead();

            if (Token[]? tokens := seek(name, take=True))
                {
                return schema.enablePointers
                        ? new @CloseCap @PointerAware ElementInputStream(this, name, tokens)
                        : new @CloseCap ElementInputStream(this, name, tokens);
                }

            throw new IllegalJSON($"Missing field \"{name}\" at \"{pointer}\"");
            }

        @Override
        ArrayInputStream<FieldInputStream> openArray(String name)
            {
            prepareRead();
            return schema.enablePointers
                    ? new @CloseCap @PointerAware ArrayInputStream(this, name)
                    : new @CloseCap ArrayInputStream(this, name);
            }

        @Override
        FieldInputStream!<FieldInputStream> openObject(String name)
            {
            prepareRead();
            return new @CloseCap FieldInputStream(this, name);
            }

        @Override
        Doc metadataFor(String attribute)
            {
            if (schema.enableMetadata && schema.isMetadata(attribute))
                {
                if (Token[]? tokens := seek(attribute, skip=schema.randomAccess && !peekingAhead))
                    {
                    if (tokens == Null)
                        {
                        // the attribute name was found, but it has not yet been read
                        tokens = skipAndStore(attribute);
                        }

                    return docFromTokens(tokens);
                    }
                }

            return Null;
            }

        @Override
        Doc peekMetadata(String attribute)
            {
            return metadataFor(attribute);
            }

        @Override
        conditional String nextName()
            {
            prepareRead();
            String? name = this.name;
            return name == Null
                    ? False
                    : (True, name);
            }

        @Override
        conditional Boolean contains(String name)
            {
            prepareRead();
            if (name == this.name?)
                {
                return True, parser.peek().id != NoVal;
                }

            if (schema.randomAccess, Token[]? tokens := seek(name))
                {
                return True, (tokens == Null
                        ? parser.peek().id != NoVal
                        : !(tokens.size == 1 && tokens[0].id == NoVal));
                }

            return False;
            }

        @Override
        Map<String, Doc>? takeRemainder()
            {
            if (!schema.storeRemainders)
                {
                return Null;
                }

            Map<String, Doc> remainder = new ListMap();

            if (schema.randomAccess)
                {
                for ((String name, Token[] tokens) : skipped?)
                    {
                    remainder.put(name, docFromTokens(tokens));
                    }
                skipped?.clear();
                }

            if (canRead)
                {
                prepareRead();

                for (String? current = this.name; current != Null; current = this.name)
                    {
                    remainder.put(current, readDoc(current));
                    }
                }

            return remainder;
            }

        @Override
        Doc readDoc(String name, Doc defaultValue = Null)
            {
            prepareRead();
            if (Token[]? tokens := seek(name, take=True))
                {
                if (tokens == Null)
                    {
                    Doc doc = parser.parseDoc();
                    loadNext();
                    return doc;
                    }
                else
                    {
                    return docFromTokens(tokens);
                    }
                }

            return defaultValue;
            }

        @Override
        ParentInput close(Exception? cause = Null)
            {
            ParentInput parent = super(cause);

            if (peekingAhead)
                {
                // discard this peek-ahead stream, erasing whatever progress it made in the parser
                parser.restore(undo, unmark=True);
                }

            return parent;
            }

        @Override
        protected void prepareRead()
            {
            assert canRead || schema.randomAccess;
            ensureActive();
            }

        @Override
        protected Boolean loadNext(Boolean first = False)
            {
            Boolean collectMetadata = False;
            while (True)
                {
                if (first)
                    {
                    if (parser.match(ObjectExit))
                        {
                        name    = Null;
                        canRead = False;
                        return False;
                        }

                    collectMetadata = schema.enableMetadata;
                    first           = False;
                    }
                else if (!parser.match(Comma))
                    {
                    name    = Null;
                    canRead = False;
                    parser.expect(ObjectExit);
                    return False;
                    }

                String current = parser.expect(StrVal).value.as(String);
                parser.expect(Colon);
                if (collectMetadata && schema.isMetadata(current))
                    {
                    skipAndStore(current);
                    }
                else
                    {
                    name = current;
                    return True;
                    }
                }
            }

        /**
         * Find the specified name in the document
         *
         * @param name  the name to find
         * @param skip  pass True to skip ahead if necessary
         * @param take  pass True to remove the tokens from the previously skipped name/value pairs
         *              if the name was previously skipped
         *
         * @return True iff the name was found
         * @return (conditional) a `Token[]` of the tokens making up the corresponding value iff the
         *         name was previously skipped; otherwise Null iff the name matches the next
         *         name/value pair
         */
        protected conditional Token[]? seek(String name, Boolean skip = True, Boolean take = False)
            {
            // check the next name
            if (name == this.name?)
                {
                return True, Null;
                }

            // check the "remainder" (skipped names)
            if (Token[] tokens := take ? skipped?.processIfPresent(name, takeTokens) : skipped?.get(name))
                {
                return True, tokens;
                }

            // if skipping ahead is allowed, then proceed until the name is found or the end reached
            if (skip)
                {
                Boolean collect = schema.randomAccess;
                Loop: for (String? current = this.name; current != Null; current = this.name)
                    {
                    if (current == name)
                        {
                        return True, Null;
                        }

                    if (Loop.first)
                        {
                        prepareRead();
                        }

                    if (collect)
                        {
                        skipAndStore(current);
                        }
                    else
                        {
                        parser.skip();
                        }

                    loadNext();
                    }
                }

            return False;
            }

        private Token[] skipAndStore(String current)
            {
            Token[] tokens = parser.skip(new Token[]);
            if (skipped == Null)
                {
                skipped = new ListMap<String, Token[]>();
                }
            skipped?.put(current, tokens);
            return tokens;
            }

        private Doc docFromTokens(Token[] tokens)
            {
            if (tokens.size == 1)
                {
                return tokens[0].value;
                }

            assert Doc doc := new Parser(tokens.iterator()).next();
            return doc;
            }
        }


    // ----- PeekAhead mixin -----------------------------------------------------------------------

    /**
     * Adds peek-ahead support for metadata to the ElementInput implementations.
     */
    mixin PeekAhead
            into (ElementInputStream | ArrayInputStream)
        {
        @Override
        FieldInputStream<PeekAhead> openObject(Boolean peekAhead=False)
            {
            DocInputStream<>? current = this.ObjectInputStream.current;
            if (current.is(FieldInputStream)
                    && current.peekingAhead
                    && &this == current.&parent)
                {
                if (!peekAhead)
                    {
                    current.peekingAhead = False;
                    }

                return current.as(FieldInputStream<PeekAhead>);
                }

            return super(peekAhead);
            }

        @Override
        Doc peekMetadata(String attribute)
            {
            if (schema.enableMetadata)
                {
                DocInputStream<>? current = this.ObjectInputStream.current;
                if (&current == &this)
                    {
                    if (parser.peek().id == ObjectEnter)
                        {
                        using (val in = openObject(peekAhead=True))
                            {
                            return in.metadataFor(attribute);
                            }
                        }
                    }
                else if (current.is(FieldInputStream)
                        && current.peekingAhead
                        && &this == current.&parent)
                    {
                    return current.metadataFor(attribute);
                    }
                }

            return super(attribute);
            }
        }


    // ----- PointerAware mixin --------------------------------------------------------------------

    /**
     * Adds pointer-peeking and de-referencing to the object-read operations on the [ElementInput]
     * implementations.
     */
    mixin PointerAware
            into (ElementInputStream | ArrayInputStream)
        {
        /**
         * To avoid multiple peek-aheads for a pointer on a single read, this flag is used to track
         * whether the current read-in-progress has already peeked ahead for a pointer.
         */
        protected Boolean readInProgress = False;

        @Override
        <Serializable> Serializable readObject(Serializable? defaultValue = Null)
            {
            Boolean nestedRead = readInProgress;
            try
                {
                if (!nestedRead, Serializable value := findValueByPointer())
                    {
                    return value;
                    }

                return registerPointer(nestedRead ? Null : pointer, super(defaultValue));
                }
            finally
                {
                readInProgress = nestedRead;
                }
            }

        @Override
        <Serializable> Serializable readUsing(
                Mapping<Serializable> mapping,
                Serializable?         defaultValue = Null)
            {
            Boolean nestedRead = readInProgress;
            try
                {
                if (!nestedRead, Serializable value := findValueByPointer())
                    {
                    return value;
                    }

                return registerPointer(nestedRead ? Null : pointer, super(mapping, defaultValue));
                }
            finally
                {
                readInProgress = nestedRead;
                }
            }

        /**
         * Check to see if the next value is a JSON pointer, which is encoded in JSON as an object
         * containing a "$ref" name/value pair.
         *
         * @return True iff the next value is a JSON pointer
         * @return (conditional) the value pointed to
         */
        <Serializable> conditional Serializable findValueByPointer()
            {
            if (!readInProgress && !parser.eof && parser.peek().id == ObjectEnter)
                {
                Doc pointer = peekMetadata(schema.pointerKey);
                if (pointer.is(String))
                    {
                    /*TODO GG*/this.as(DocInputStream). parser.skip();
                    /*TODO GG*/this.as(DocInputStream). loadNext();
                    return True, dereference(pointer);
                    }
                else if (pointer != Null)
                    {
                    throw new IllegalJSON($"Pointer type {&pointer.actualType} from node"
                            + $" \"{this.pointer}\"; String expected");
                    }
                }

            readInProgress = True;
            return False;
            }

        /**
         * Register the specified value to be associated with this node's [pointer].
         *
         * @param pointer  pass a non-`Null` pointer to register the pointer/value pair
         * @param value    the value to register with this node's pointer
         *
         * @return the value
         */
        <Serializable> Serializable registerPointer(String? pointer, Serializable value)
            {
            pointers.put(pointer?, value);
            return value;
            }
        }
    }