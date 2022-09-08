/**
 * The BasicFormat is a String-to-some-other-type converter that uses `toString()` -- and/or
 * `Stringable` if it is available -- and a one-parameter constructor that takes a `String`.
 */
const BasicFormat<Value extends FromString>
        implements Format<Value>
        incorporates conditional StringableFormat<Value extends Stringable>
    {
    // ----- FromString interface ------------------------------------------------------------------

    /**
     * Any type that can be constructed from a String.
     */
    static interface FromString
        {
        construct(String text);
        }


    // ----- Format interface ----------------------------------------------------------------------

    @Override
    String name = Value.toString();

    @Override
    Value decode(String text)
        {
        return Value.new(text);
        }

    @Override
    String encode(Value value)
        {
        return value.toString();
        }


    // ----- StringableFormat mixin ----------------------------------------------------------------

    /**
     * An extension to the `BasicFormat` that takes advantage of the `Stringable` interface when it
     * is available.
     */
    static mixin StringableFormat<Value extends FromString+Stringable>
            into BasicFormat<Value>
        {
        @Override
        void write(Value value, Appender<Char> stream)
            {
            value.appendTo(stream);
            }
        }
    }
