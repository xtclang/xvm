/**
 * The ObjectOutput interface represents the ability to write an object (or multiple objects) into
 * a _serialized_ form; the ObjectOutput interface represents the act of _serialization_.
 */
interface ObjectOutput
        extends Closeable
    {
    /**
     * @param value  the object to write to the stream
     */
    <ObjectType> void write(ObjectType value);
    }