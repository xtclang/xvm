module TestMixins.xqiz.it
    {
    import Ecstasy.collections.HashMap;
    import Ecstasy.collections.ListMap;
    import Ecstasy.io.ByteArrayInputStream;
    import Ecstasy.io.CharArrayReader;
    import Ecstasy.io.DataInputStream;
    import Ecstasy.io.InputStream;
    import Ecstasy.io.JavaDataInput;
    import Ecstasy.io.ObjectOutput;
    import Ecstasy.io.Reader;
    import Ecstasy.io.Writer;
    import Ecstasy.io.UTF8Reader;
    import Ecstasy.web.json.Doc;

    @Inject Console console;

    void run()
        {
        console.println("It compiles!");
        }

    // ---------------------------------------------------------------------------------------------

    interface DocOutput<ParentOutput extends (ElementOutput | FieldOutput)?>
            extends Closeable
        {
        @RO Boolean canWrite;
        @RO ParentOutput parent;
        @Override ParentOutput close();
        }

    interface ElementOutput<ParentOutput extends (ElementOutput | FieldOutput)?>
            extends DocOutput<ParentOutput>
        {
        ElementOutput!<ElementOutput> openArray();
        FieldOutput<ElementOutput> openObject();

        ElementOutput add(Doc value);
        }

    interface FieldOutput<ParentOutput extends (ElementOutput | FieldOutput)?>
            extends DocOutput<ParentOutput>
        {
        ElementOutput<FieldOutput> openField(String name);
        ElementOutput<FieldOutput> openArray(String name);
        FieldOutput!<FieldOutput> openObject(String name);

        FieldOutput add(String name, Doc value)
            {
            using (val field = openField("name"))
                {
                field.add(value);
                }
            return this;
            }
        }

    class ObjectOutputStream
            implements ObjectOutput
        {
        protected/private ElementOutputStream? root;
        protected/private DocOutputStream<>? current;
        protected/private Boolean closed;

        @Override
        <ObjectType> void write(ObjectType value)
            {
            assert !closed;
            assert root == Null;

            try (ElementOutputStream out = new ElementOutputStream(Null))
                {
                root    = out;
                current = out;
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

        class DocOutputStream<ParentOutput extends (ElementOutputStream | ArrayOutputStream | FieldOutputStream)?>
                implements DocOutput<ParentOutput>
            {
            construct(ParentOutput parent, (String|Int)? id = Null)
                {
                this.parent = parent;
                this.id     = id;
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
            ParentOutput close()
                {
                assert &this == &current;

                // close this node, and make the parent node into the current node
                canWrite = False;
                ParentOutput parent = this.parent;
                current = parent;
                return parent;
                }

            Boolean hasParent(DocOutputStream!<> stream)
                {
                ParentOutput parent = this.parent;
                if (&stream == &parent)
                    {
                    return True;
                    }

                return parent != Null && parent.hasParent(stream);
                }

            protected void ensureActive()
                {
                if (&this == &current)
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
                }
            }

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

        class ElementOutputStream<ParentOutput extends (ElementOutputStream | ArrayOutputStream | FieldOutputStream)?>
                extends DocOutputStream<ParentOutput>
                implements ElementOutput<ParentOutput>
            {
            construct(ParentOutput parent, (String|Int)? id = Null)
                {
                construct DocOutputStream(parent, id);
                }

            @Override
            ArrayOutputStream<ElementOutputStream> openArray()
                {
                prepareWrite();
                canWrite = False;
                return new @CloseCap @PointerAwareElementOutput ArrayOutputStream(this);
                }

            @Override
            FieldOutputStream<ElementOutputStream> openObject()
                {
                prepareWrite();
                canWrite = False;
                return new @CloseCap @PointerAwareFieldOutput FieldOutputStream(this);
                }

            @Override
            ElementOutputStream add(Doc value)
                {
                ParentOutput parent = this.parent;
                if (value != Null && parent.is(ElementOutputStream!))
                    {
                    prepareWrite();
                    }

                canWrite = False;
                return this;
                }

            @Override
            ParentOutput close()
                {
                if (canWrite)
                    {
                    add(Null);
                    }

                return super();
                }
            }

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
                }

            @Override
            ArrayOutputStream!<ArrayOutputStream> openArray()
                {
                ensureActive();
                return new @CloseCap @PointerAwareElementOutput ArrayOutputStream(this);
                }

            @Override
            FieldOutputStream<ArrayOutputStream> openObject()
                {
                ensureActive();
                return new @CloseCap @PointerAwareFieldOutput FieldOutputStream(this);
                }

            @Override
            ArrayOutputStream add(Doc value)
                {
                ensureActive();
                return this;
                }

            @Override
            ParentOutput close()
                {
                return super();
                }
            }

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
                }

            @Override
            ElementOutputStream<FieldOutputStream> openField(String name)
                {
                ensureActive();
                return new @CloseCap @PointerAwareElementOutput ElementOutputStream(this, name);
                }

            @Override
            ArrayOutputStream<FieldOutputStream> openArray(String name)
                {
                ensureActive();
                return new @CloseCap @PointerAwareElementOutput ArrayOutputStream(this, name);
                }

            @Override
            FieldOutputStream!<FieldOutputStream> openObject(String name)
                {
                ensureActive();
                return new @CloseCap @PointerAwareFieldOutput FieldOutputStream(this, name);
                }

            @Override
            ParentOutput close()
                {
                return super();
                }
            }

        mixin PointerAwareDocOutput
                into DocOutputStream
            {
            protected PointerAwareDocOutput
                    writePointerOrValue(Object value,
                                        function void(String) writePointer,
                                        function void() writeValue)
                {
                return this;
                }
            }

        /**
         * TODO GG get rid of type parameters on this declaration
         */
        mixin PointerAwareElementOutput<ParentOutput extends (ElementOutputStream | ArrayOutputStream | FieldOutputStream)?>
                into (ElementOutputStream<ParentOutput> | ArrayOutputStream<ParentOutput>)
                extends PointerAwareDocOutput<ParentOutput>
            {
            @Override
            PointerAwareElementOutput add(Doc value)
                {
                return writePointerOrValue(value, &addPointerReference(_), &super(value));
                }

            protected void addPointerReference(String pointer)
                {
                using (val out = openObject())
                    {
                    out.add("$ref", pointer);
                    }
                }
            }

        mixin PointerAwareFieldOutput
                into FieldOutputStream
                extends PointerAwareDocOutput
            {
            @Override
            PointerAwareFieldOutput add(String name, Doc value)
                {
                return this;
                }
            }
        }
    }