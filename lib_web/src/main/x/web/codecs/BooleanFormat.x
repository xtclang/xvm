/**
 * The BooleanFormat is a String-to-Boolean converter.
 */
static const BooleanFormat
        implements Format<Boolean> {
    // ----- Format interface ----------------------------------------------------------------------

    @Override
    String name = "Boolean";

    @Override
    Value decode(String text) {
        return text == "true";
    }

    @Override
    String encode(Value value) {
        return value ? "true" : "false";
    }
}