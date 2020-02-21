import collections.HashMap;
import collections.ListMap;

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
        construct(ParentInput parent, (String|Int)? id, Token[]? tokens)
            {
            this.parent = parent;
            this.id     = id;
            this.lexer  = tokens?.iterator() : parent?.lexer : this.ObjectInputStream.lexer;
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

        /**
         * The stream of JSON tokens for this DocInputStream.
         */
        protected/private Iterator<Token> lexer;

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

        /**
         * Make sure that this DocInput can be read from, and is ready to read from.
         */
        protected void prepareRead()
            {
            assert canRead;
            ensureActive();
            }

        /**
         * Take the next token.
         */
        protected Token takeToken()
            {
            if (Token token := lexer.next())
                {
                return token;
                }

            canRead = False;
            throw new IllegalJSON($"Unexpected EOF");
            }

        /**
         * Take the next token.
         */
        protected Token expectToken(Lexer.Id id)
            {
            Token token = takeToken();
            if (token.id == id)
                {
                return token;
                }

            canRead = False;
            throw new IllegalJSON($"Expected {id}; found {token}.");
            }

        /**
         * Invoked when a child has closed.
         */
        protected void childClosed(DocInputStream! child)
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

            ParentInput parent = super();
            parent?.childClosed(this);
            return parent;
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
        construct(ParentInput parent, (String|Int)? id = Null, Token[]? tokens = Null)
            {
            construct DocInputStream(parent, id, tokens);
            }
        finally
            {
            token = takeToken();
            }

        /**
         * The initial token of the element.
         */
        @Unassigned
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
                canRead = False;
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
                canRead = False;
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
                    canRead = False;
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
                        canRead = False;
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
        construct(ParentInput parent, (String|Int)? id = Null, Token[]? tokens = Null)
            {
            construct DocInputStream(parent, id, tokens);
            }
        finally
            {
            loadNext(first = True);
            }

        /**
         * The initial token of the element.
         */
        @Unassigned
        protected/private Token token;

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
        ArrayInputStream!<ArrayInputStream> openArray()
            {
            prepareRead();
            return new @CloseCap ArrayInputStream(this, count);
            }

        @Override
        FieldInputStream<ArrayInputStream> openObject()
            {
            prepareRead();
            return new @CloseCap FieldInputStream(this, count);
            }

        @Override
        Boolean isNull()
            {
            return !canRead || token.id == NoVal;
            }

        @Override
        Doc readDoc()
            {
            prepareRead();

            switch (token.id)
                {
                case NoVal:
                case BoolVal:
                case IntVal:
                case FPVal:
                case StrVal:
                    ++count;
                    return token.value;

                case ArrayEnter:
                case ObjectEnter:
                    assert Doc doc := new Parser(lexer, token).next();
                    loadNext();
                    return doc;

                default:
                    canRead = False;
                    throw new IllegalJSON($"Illegal token for start of value: {token}");
                }
            }

        @Override
        ParentInput close()
            {
            // if nothing was read, then exhaust the contents of the element
            if (canRead)
                {
                do
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
                            canRead = False;
                            throw new IllegalJSON($"Illegal token for start of value: {token}");
                        }
                    }
                while (loadNext());
                }

            return super();
            }

        @Override
        void childClosed(DocInputStream! child)
            {
            if (child.lexer == this.lexer)
                {
                loadNext();
                }
            super(child);
            }

        /**
         * Load the token of the value of the next array element. If there are no more elements,
         * then set `canRead` to `False`.
         *
         * Note: The assumption is that prepareRead() (or equivalent) has already been invoked.
         *
         * @return True iff the stream appears to contain a next element
         */
        protected Boolean loadNext(Boolean first = False)
            {
            if (!first)
                {
                ++count;
                Token trailing = takeToken();
                switch (trailing.id)
                    {
                    case Comma:
                        break;

                    case ArrayExit:
                        canRead = False;
                        return False;

                    default:
                        canRead = False;
                        throw new IllegalJSON($"Expected ',' or ']'; found {trailing}.");
                    }
                }

            token = takeToken();
            if (first && token.id == ArrayExit)
                {
                canRead = False;
                return False;
                }

            return True;
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
        construct(ParentInput parent, (String|Int)? id = Null, Token[]? tokens = Null)
            {
            construct DocInputStream(parent, id, tokens);
            }
        finally
            {
            loadNext(first = True);
            }

        /**
         * The next loaded key name of the next key-value pair.
         */
        String? name;

        /**
         * The first token of the value of the next key-value pair.
         */
        @Unassigned
        Token token;

        /**
         * A lazily constructed map of the key-value pairs that were skipped over. (The values are
         * stored as an array of their raw tokens.)
         */
        ListMap<String, Token[]>? skipped;

        @Override
        conditional FieldInputStream insideObject()
            {
            return True, this;
            }

        @Override
        ElementInputStream<FieldInputStream> openField(String name)
            {
            prepareRead();

            // check if the next key/value pair is the one that we're looking for
            if (this.name? == name)
                {
                return new @CloseCap ElementInputStream(this, name);
                }

            // check if we've already skipped over the name that we're looking for
            if (schema.randomAccess, Token[] tokens := skipped?.get(name))
                {
                return new @CloseCap ElementInputStream(this, name, tokens); // TODO
                }

            // advance through the key/value pairs until the specified name is found
            TODO
            }

        @Override
        ArrayInputStream<FieldInputStream> openArray(String name)
            {
            prepareRead();
            return new @CloseCap ArrayInputStream(this, name);
            }

        @Override
        FieldInputStream!<FieldInputStream> openObject(String name)
            {
            prepareRead();
            return new @CloseCap FieldInputStream(this, name);
            }

        @Override
        conditional String nextName()
            {
            String? name = this.name;
            return name == Null
                    ? False
                    : (True, name);
            }

        @Override
        Boolean contains(String name)
            {
            // check the next name
            if (name == this.name?)
                {
                return True;
                }

            // check the "remainder" (skipped names)
            if (skipped?.contains(name))
                {
                return True;
                }

            // if randomAccess is enabled, then peek ahead to find the name
            if (schema.randomAccess)
                {
                TODO implement random access feature
                }

            return False;
            }

        @Override
        Map<String, Doc>? takeRemainder()
            {
            if (schema.storeRemainders)
                {
                Map<String, Doc> remainder = new ListMap();

                for ((String name, Token[] tokens) : skipped?)
                    {
                    assert Doc doc := new Parser(tokens.iterator()).next();
                    remainder.put(name, doc);
                    }

                while (canRead)
                    {
                    String name = this.name? : assert;
                    remainder.put(name, readDoc(name));
                    }

                return remainder;
                }
            else
                {
                return Null;
                }
            }

        @Override
        Boolean isNull(String name)
            {
            // first, quick-check the current key/value
            if (name == this.name?)
                {
                return token.id == NoVal;
                }

            // check the skipped key/value pairs (if any)
            if (Token[] value := skipped?.get(name))
                {
                return value.size == 1 && value[0].id == NoVal;
                }

            if (schema.randomAccess)
                {
                TODO read in the rest of the document and answer the question
                }

            return True;
            }

        @Override
        Doc readDoc(String name, Doc defaultValue = Null)
            {
            TODO
            }

        @Override
        ParentInput close()
            {
            // if nothing was read, then exhaust the contents of the element
            if (canRead)
                {
                do
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
                            canRead = False;
                            throw new IllegalJSON($"Illegal token for start of value: {token}");
                        }
                    }
                while (loadNext());
                }

            return super();
            }

        @Override
        void childClosed(DocInputStream! child)
            {
            if (child.lexer == this.lexer)
                {
                loadNext();
                }
            super(child);
            }

        /**
         * Load the name and first token of the value of the next key-value pair. If there are no
         * more key-value pairs, then set `canRead` to `False`.
         *
         * Note: The assumption is that prepareRead() (or equivalent) has already been invoked.
         *
         * @return True iff the stream appears to contain a next key-value pair
         */
        protected Boolean loadNext(Boolean first = False)
            {
            if (!first)
                {
                Token trailing = takeToken();
                switch (trailing.id)
                    {
                    case Comma:
                        break;

                    case ObjectExit:
                        name    = Null;
                        canRead = False;
                        return False;

                    default:
                        canRead = False;
                        throw new IllegalJSON($"Expected ',' or '}'; found {trailing}.");
                    }
                }

            Token next = takeToken();
            switch (next.id)
                {
                case StrVal:
                    name = next.value.as(String);
                    expectToken(Colon);
                    token = takeToken();
                    return True;

                case ObjectExit:
                    if (first)
                        {
                        name    = Null;
                        canRead = False;
                        return False;
                        }
                    continue;

                default:
                    canRead = False;
                    throw new IllegalJSON($"Expected field name (a string value); found {next}.");
                }
            }
        }
    }
