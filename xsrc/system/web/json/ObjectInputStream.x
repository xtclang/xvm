import collections.HashMap;
import collections.ListMap;

import io.Reader;
import io.ObjectInput;

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
        construct ObjectInputStream(schema, new Parser(lexer));
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
    protected/private Map<String, Object> pointers
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


    // ----- ObjectInput implementation -----------------------------------------------------------

    @Override
    <ObjectType> ObjectType read<ObjectType>()
        {
        return ensureElementInput().read<ObjectType>();
        }

    @Override
    void close()
        {
        root    = Null;
        current = Null;
        closed  = True;
        &pointers.reset();
        }


    // ----- DocInputStream -----------------------------------------------------------------------

    /**
     * The ObjectInputStream uses three specific JSON stream implementations internally to dissect
     * a stream of JSON tokens into the desired corresponding Ecstasy types, values, and structures.
     */
    typedef ElementInputStream | ArrayInputStream | FieldInputStream AnyStream;

    /**
     * Base virtual child implementation for the various DocInput / ElementInput / FieldInput
     * implementations.
     */
    class DocInputStream<ParentInput extends AnyStream?>
            implements DocInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id, Token[]? tokens)
            {
            this.parent = parent;
            this.id     = id;
            this.parser = new Parser(tokens?.iterator()) : parent?.parser : this.ObjectInputStream.parser;
            }
        finally
            {
            assert loadNext(first = True);
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
        public/protected Boolean canRead = True;

        @Override
        public/private ParentInput parent;

        /**
         * If the DocInput is inside a JSON object, then it has a field name.
         * If the DocInput is inside a JSON array, then it has an array index.
         */
        protected/private (String | Int)? id;

        /**
         * The underlying JSON parser, which uses a look-ahead of exactly one token.
         */
        protected/private Parser parser;

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
            assert schema.enablePointers;
            if (Object value := pointers.get(pointer))
                {
                if (value.is(Serializable))
                    {
                    return value;
                    }

                throw new IllegalJSON($"Type mismatch for JSON pointer=\"{pointer}\"; required type={Serializable}, actual type={&value.actualType}");
                }
            throw new IllegalJSON($"Missing value for JSON pointer=\"{pointer}\"; required type={Serializable}");
            }

        @Override
        ParentInput close()
            {
            if (canRead)
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
            if (child.&parser == this.&parser)
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
            return first;
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
            current = parent?.as(DocInputStream<>?) : Null;
            parent?.childClosed(this);
            return parent;
            }
        }


    // ----- ElementInputStream -------------------------------------------------------------------

    /**
     * The ElementInputStream is an implementation of [ElementInput] that represents the reading
     * of a single value (which in turn may be an array or object).
     */
    class ElementInputStream<ParentInput extends AnyStream?>
            extends DocInputStream<ParentInput>
            implements ElementInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id = Null, Token[]? tokens = Null)
            {
            construct DocInputStream(parent, id, tokens);
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
            return new @CloseCap ArrayInputStream(this);
            }

        @Override
        FieldInputStream<ElementInputStream> openObject()
            {
            prepareRead();
            canRead = False;
            return new @CloseCap FieldInputStream(this);
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
            construct ElementInputStream<Nullable>(Null);  // TODO GG: infer <Nullable>
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
        Nullable close() // TODO GG: ParentInput close() doesn't compile
            {
            super();
            root = Null;
            return Null;
            }
        }


    // ----- ArrayInputStream ---------------------------------------------------------------------

    /**
     * The ArrayInputStream is an implementation of [ElementInput] that represents the reading of
     * a sequence of values into a JSON array (each of which may in turn be a single JSON value, or
     * an array, or an object).
     */
    class ArrayInputStream<ParentInput extends AnyStream?>
            extends DocInputStream<ParentInput>
            implements ElementInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id = Null, Token[]? tokens = Null)
            {
            construct DocInputStream(parent, id, tokens);
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


    // ----- FieldInputStream ---------------------------------------------------------------------

    /**
     * The FieldInputStream is an implementation of [FieldInput] that represents the reading of
     * a sequence of name/value pairs into a JSON object (each value of which may in turn be a
     * single JSON value, or an array, or an object).
     */
    class FieldInputStream<ParentInput extends AnyStream?>
            extends DocInputStream<ParentInput>
            implements FieldInput<ParentInput>
        {
        construct(ParentInput parent, (String|Int)? id = Null, Token[]? tokens = Null)
            {
            construct DocInputStream(parent, id, tokens);
            parser.expect(ObjectEnter);
            }

        /**
         * The next loaded key name of the next key-value pair.
         */
        String? name;

        /**
         * A lazily constructed map of the key-value pairs that were skipped over. (The values are
         * stored as an array of their raw tokens.)
         */
        ListMap<String, Token[]>? skipped;

        /**
         * Take the `Token[]` value of the entry identified by the `String` key, removing the entry
         * in the process.
         */
        private static Token[] takeTokens(Map<String, Token[]>.Entry entry)
            {
            val result = entry.value;
            entry.remove();
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
                return new @CloseCap ElementInputStream(this, name, tokens);
                }

            throw new IllegalJSON($"Missing field {name}");
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
        Doc metadataFor(String attribute)
            {
            TODO
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
        conditional Boolean contains(String name)
            {
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
                    assert Doc doc := new Parser(tokens.iterator()).next();
                    remainder.put(name, doc);
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
                    return new Parser(tokens.iterator()).parseDoc();
                    }
                }

            return defaultValue;
            }

        @Override
        protected Boolean loadNext(Boolean first = False)
            {
            if (first)
                {
                if (parser.match(ObjectExit))
                    {
                    name    = Null;
                    canRead = False;
                    return False;
                    }
                }
            else if (!parser.match(Comma))
                {
                canRead = False;
                parser.expect(ObjectExit);
                return False;
                }

            name = parser.expect(StrVal).value.as(String);
            parser.expect(Colon);
            return True;
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

            // if skpping ahead is allowed, then proceed until the name is found or the end reached
            if (skip)
                {
                Boolean collect = schema.randomAccess;
                for (String? current = this.name; current != Null; current = this.name)
                    {
                    if (current == name)
                        {
                        return True, Null;
                        }

                    if (collect)
                        {
                        Token[] tokens = new Token[];
                        parser.skip(tokens);
                        if (skipped == Null)
                            {
                            skipped = new ListMap<String, Token[]>();
                            }
                        skipped?.put(current, tokens);
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
        }
    }
