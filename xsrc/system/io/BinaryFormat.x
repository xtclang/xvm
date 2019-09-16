/**
 * A BinaryFormat represents the ability to convert between binary data (a stream of bytes) and
 * objects. The act of creating binary data from an object is called _serialization_. The act of
 * creating an object from binary data is called _deserialization_. The purpose of serialization is
 * to allow the information from an object to be stored (e.g. on disk) or transported (e.g. over a
 * network). The purpose of deserialization is to allow that information to be converted back into
 * an object that is largely indistinguishable from the original object from which that information
 * was extracted.
 *
 * @see TextFormat
 */
interface BinaryFormat
    {
    /**
     * A BinaryFormat is typically identified by a name, such as "Thrift", "Avro", "protobuf", or
     * "ASN.1".
     */
    @RO String name;

    /**
     * Create an ObjectInput for the purpose of deserialization, that will use binary data from the
     * provided InputStream in order to create objects.
     *
     * @param in  a source of binary data in the format of this BinaryFormat
     *
     * @return an ObjectInput that reads objects from the binary data provided
     */
    ObjectInput createObjectInput(InputStream in);

    /**
     * Create an ObjectOutput for the purpose of serialization, that will emit binary data to the
     * provided OutputStream.
     *
     * @param out  the destination to write the binary data resulting from object serialization
     *
     * @return an ObjectOutput that serializes objects into binary data
     */
    ObjectOutput createObjectOutput(OutputStream out);
    }
