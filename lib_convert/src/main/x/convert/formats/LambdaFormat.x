/**
 * The LambdaFormat is an implementation of Format that delegates to the provided functions for
 * decoding and (optionally) encoding.
 *
 * @param decoder  a function that decodes a `String` into a `Value`
 * @param encoder  a function that encodes a `Value` into a `String`; if none is provided, then
 *                 the `toString()` method (and the `Stringable` interface, if available) is used
 */
const LambdaFormat<Value>(Decoder decoder, Encoder? encoder=Null)
        implements Format<Value>
        incorporates conditional StringableFormat<Value extends Stringable> {
    /**
     * An `Encoder` encapsulates the behavior of the [encode] method.
     */
    typedef function String(Value) as Encoder;

    /**
     * An `Encoder` encapsulates the behavior of the [decode] method.
     */
    typedef function Value(String) as Decoder;


    // ----- Format interface ----------------------------------------------------------------------

    @Override
    String name = Value.toString();

    @Override
    Value decode(String text) {
        return decoder(text);
    }

    @Override
    String encode(Value value) {
        return encoder?(value) : value.toString();
    }


    // ----- StringableFormat mixin ----------------------------------------------------------------

    /**
     * An extension to the `BasicFormat` that takes advantage of the `Stringable` interface when it
     * is available.
     */
    static mixin StringableFormat<Value extends Stringable>
            into LambdaFormat<Value> {
        @Override
        void write(Value value, Appender<Char> stream) {
            if (encoder == Null) {
                value.appendTo(stream);
            } else {
                super(value, stream);
            }
        }
    }
}