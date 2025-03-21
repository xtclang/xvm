module TestSimple {
    @Inject Console console;

    void run() {
        StringWriter w = new StringWriter(17);
        w.offset = 5; // this must blow at runtime (used to be allowed)
    }

    interface TextPosition
            extends immutable Hashable {
        @RO Int offset;
    }

    class StringWriter(Int offset)
            implements TextPosition {

        @Override
        String toString() = $"Writer at {offset=}";

        static <CompileType extends StringWriter> Int64 hashCode(CompileType value) {
            return value.offset.toInt64();
        }

    }
}