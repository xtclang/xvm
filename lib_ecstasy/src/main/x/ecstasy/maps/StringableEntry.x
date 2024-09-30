/**
 * A [Stringable] implementation for [Map.Entry] implementations.
 *
 * This mixin can be used as part of an Entry implementation as follows:
 *
 *     incorporates conditional StringableEntry<Key extends Stringable, Value extends Stringable>
 */
mixin StringableEntry<Key extends Stringable, Value extends Stringable>
        into Map.Entry<Key, Value>
        implements Stringable {

    @Override
    Int estimateStringLength() {
        return key.estimateStringLength() + 1 + value.estimateStringLength();
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        key.appendTo(buf);
        buf.add('=');
        value.appendTo(buf);
        return buf;
    }
}