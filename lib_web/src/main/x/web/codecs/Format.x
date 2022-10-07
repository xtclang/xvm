/**
 * Represents a `String` format that can be converted from and to another type.
 *
 * Implementations of this interface should be `const`, immutable, [Freezable], or a service.
 */
interface Format<Value>
    {
    /**
     * The `Format` name.
     *
     * Using this information, a `Format` can be specified by its name. For a `Format` that is
     * registered, this name is used by the [Registry] to look up the `Format` by its name, such as
     * when "XML" or "JSON" is specified as a format for a user type such as a `Cart` or a `Person`.
     *
     * The name is also obviously useful for debugging and log output.
     */
    @RO String name;

    /**
     * Obtain a derivative `Format` of this `Format` for the specified type, if such a derivative
     * `Format` is possible.
     *
     * @param type  a `Type` for which this `Format` may be able to supply a derivative `Format` for
     */
    <OtherValue> conditional Format!<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        // if this format is capable of translating to and from another more specific type, then
        // this method should return a Format instance that can translate to and from the specified
        // type; this is common for a generic serialization Format, like JSON or XML, in that it
        // can support more specific types, including application specific types like a Cart or a
        // Person
        return False;
        }

    /**
     * Convert from a character stream to a value.
     *
     * @param stream  the stream of characters to convert to a value
     *
     * @return the resulting value
     */
    Value read(Iterator<Char> stream)
        {
        // default implementation is to suck the contents stream into a String and just delegate
        // to the decode() method; this will cause a stack overflow if at least one of these two
        // methods is not overridden
        return decode(new String(stream.toArray(Constant)));
        }

    /**
     * Convert from a `String` to a value.
     *
     * @param text  the `String` to convert to a value
     *
     * @return the resulting value
     */
    Value decode(String text)
        {
        // default implementation is to turn the String into a stream and just delegate to the
        // read() method; this will cause a stack overflow if at least one of these two methods
        // is not overridden
        return read(text.iterator());
        }

    /**
     * Render a value into the provided stream.
     *
     * @param value   the value to convert to text
     * @param stream  the stream to write the text into
     */
    void write(Value value, Appender<Char> stream)
        {
        // default implementation is to just delegate to the encode() method; this will cause a
        // stack overflow if neither of these two methods is overridden
        encode(value).appendTo(stream);
        }

    /**
     * Render a value as a `String`.
     *
     * @param value  the value to convert to a `String`
     *
     * @return the resulting `String`
     */
    String encode(Value value)
        {
        // default implementation is to just delegate to the write() method; this will cause a
        // stack overflow if neither of these two methods is overridden
        StringBuffer buf = new StringBuffer();
        write(value, buf);
        return buf.toString();
        }
    }