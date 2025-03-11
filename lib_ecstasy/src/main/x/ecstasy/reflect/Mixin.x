/**
 * A Mixin represents the information about a mixin class
 */
const Mixin(Class mixinClass) {
    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = 6 + mixinClass.displayName.size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return buf.addAll("mixin ")
                  .addAll(mixinClass.displayName);
    }
}