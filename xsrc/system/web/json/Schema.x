import io.Reader;
import io.Writer;
import io.ObjectInput;
import io.ObjectOutput;

const Schema
        implements io.TextFormat
    {
    /**
     * Construct a schema for a specified set of Mappings.
     */
    construct(Mapping[] mappings = [])
        {
        // TODO this.mappings = addDefaultMappings(mappings);
        }


    // ----- Mapping interface ---------------------------------------------------------------------

    static interface Mapping<Serializable>
        {
        @RO Class class_;

        Boolean handles(Doc doc);

        Serializable read(ObjectInputStream in);

        void write(ObjectOutputStream out, Serializable value);
        }


    // ----- properties ----------------------------------------------------------------------------

    static Schema DEFAULT = new Schema([]);

    // TODO
    Map<Type, Mapping> mappings;


    // ----- helpers

    <Serializable> conditional Mapping<Serializable> getMapping(Type<Serializable> type)
        {
        TODO
        }

    conditional Mapping getMapping(Doc doc)
        {
        TODO
        }


    // ----- TextFormat implementation --------------------------------------------------------------

    @Override
    @RO String name.get()
        {
        return "JSON";
        }

    @Override
    ObjectInput createObjectInput(Reader reader)
        {
        return new ObjectInputStream(reader);
        }

    @Override
    ObjectOutput createObjectOutput(Writer writer)
        {
        return new ObjectOutputStream(writer);
        }


    // ----- input/output stream implementations ---------------------------------------------------

    class ObjectInputStream(Reader reader)
            implements ObjectInput
        {
        public/private Reader reader;

        @Override
        <ObjectType> ObjectType read<ObjectType>()
            {
            if (Mapping<ObjectType> mapping := this.Schema.getMapping(ObjectType))
                {
                return mapping.read(this);
                }

            Doc doc = new Parser(reader).next();
            if (doc.is(Primitive | Array<Primitive>))
                {
                if (ObjectType != Object)
                    {
                    TODO add conversions, e.g. IntLiteral -> Int, FPLiteral -> Dec, etc.
                    }

                if (doc.is(ObjectType))
                    {
                    return doc;
                    }

                TODO how should this report errors?
                }

            if (Mapping mapping := this.Schema.getMapping(doc), mapping.Serializable.is(ObjectType))
                {
                return mapping.read(this).as(ObjectType);
                }

            return doc.as(ObjectType); // or throw
            }

        @Override
        void close()
            {
            }
        }

    class ObjectOutputStream(Writer writer)
            implements ObjectOutput
        {
        public/private Writer writer;

        @Override
        <ObjectType> void write<ObjectType>(ObjectType value)
            {
            TODO
            }

        @Override
        void close()
            {
            }
        }
    }