/**
 * A TextFormat represents the ability to convert between text data and objects. The act of creating
 * text data from an object is called _serialization_. The act of creating an object from text data
 * is called _deserialization_. The purpose of serialization is to allow the information from an
 * object to be stored (e.g. on disk) or transported (e.g. over a network). The purpose of
 * deserialization is to allow that information to be converted back into an object that is largely
 * indistinguishable from the original object from which that information was extracted.
 *
 * @see BinaryFormat
 */
interface TextFormat
    {
    /**
     * A TextFormat is typically identified by a name, such as "XML", "JSON", or "TOML".
     */
    @RO String name;

    /**
     * Create an ObjectInput for the purpose of deserialization, that will use text data from the
     * provided Reader in order to create objects.
     *
     * @param reader  a source of text data in the format of this TextFormat
     *
     * @return an ObjectInput that reads objects from the text data provided
     */
    ObjectInput createObjectInput(Reader reader);

    /**
     * Create an ObjectOutput for the purpose of serialization, that will emit text data to the
     * provided Writer.
     *
     * @param writer  the destination to write the text data resulting from object serialization
     *
     * @return an ObjectOutput that serializes objects into text data
     */
    ObjectOutput createObjectOutput(Writer writer);
    }
