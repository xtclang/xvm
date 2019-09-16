/**
 * The ObjectInput interface represents the ability to read an object (or multiple objects) from
 * some _serialized_ data source; the ObjectInput interface represents the act of _deserialization_.
 * ObjectInput may represent a stream of objects, or it may be used to represent the ability to read
 * a single object.
 */
interface ObjectInput
        extends Closeable
    {
    /**
     * @return  an object from the stream
     */
    <ObjectType> ObjectType read<ObjectType>();
    }