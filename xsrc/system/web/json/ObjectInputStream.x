import io.Reader;
import io.ObjectInput;


/**
 * The ObjectInput implementation for JSON deserialization.
 */
class ObjectInputStream(Schema schema, Reader reader)
        implements ObjectInput
    {
    /**
     * The JSON Schema.
     */
    public/private Schema schema;

    /**
     * The underlying writer to read the JSON data from.
     */
    public/private Reader reader;

    @Lazy
    public/private Lexer lexer.calc()
        {
        return new Lexer(reader);
        }

    @Lazy
    public/private Parser parser.calc()
        {
        return new Parser(lexer);
        }

    @RO ElementInput<Nullable> root.get()
        {
        TODO
        }

    @RO FieldInput<ElementInput<Nullable>> object.get()
        {
        TODO return elementInput.enterObject();
        }

    @Override
    <ObjectType> ObjectType read<ObjectType>()
        {
        if (Mapping<ObjectType> mapping := schema.getMapping(ObjectType))
            {
            TODO return mapping.read(this);
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

        if (Mapping mapping := schema.getMapping(doc), mapping.Serializable.is(ObjectType))
            {
            TODO return mapping.read(this).as(ObjectType);
            }

        return doc.as(ObjectType); // or throw
        }

    @Override
    void close()
        {
        }
    }
