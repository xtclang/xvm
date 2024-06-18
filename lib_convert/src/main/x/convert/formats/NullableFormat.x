/**
 * The `NullableFormat` handles empty `String` values by treating them as a `Null`.
 */
const NullableFormat<NonNullableValue>(Format<NonNullableValue> notNull)
        implements Format<Nullable|NonNullableValue> {
    // ----- Format interface ----------------------------------------------------------------------

    @Override
    String name.get() {
        return notNull.name + '?';
    }

    @Override
    <OtherValue> conditional Format<OtherValue> forType(Type<OtherValue> type, Registry registry) {
        Type otherNotNullType = type - Nullable;
        if (val otherNotNull := notNull.forType(otherNotNullType, registry)) {
            TODO CP think this through
//            return Null.isA(type)
//                    ? otherNotNull
//                    : new NullableFormat<type>(otherNotNull);
        } else {
            return False;
        }
    }

    @Override
    Value read(Iterator<Char> stream) {
        if (stream.knownEmpty()) {
            return Null;
        }

        if (Int size := stream.knownSize()) {
            return size == 0
                    ? Null
                    : notNull.read(stream);
        }

        // unfortunately we need to buffer the stream
        return super(stream);
    }

    @Override
    Value decode(String text) {
        return text.size == 0
                ? Null
                : notNull.decode(text);
    }

    @Override
    String encode(Value value) {
        return value == Null
                ? ""
                : notNull.encode(value);
    }

    @Override
    void write(Value value, Appender<Char> stream) {
        if (value == Null) {
            return;
        }

        notNull.write(value, stream);
    }
}