/**
 * A [Stringable] implementation for [Map.Entry] implementations.
 *
 * This mixin can be used as part of an Entry implementation as follows:
 *
 *     incorporates conditional EntryStringer<Key extends Stringable, Value extends Stringable>
 */
mixin EntryStringer<MapKey extends Stringable, MapValue extends Stringable>
        into Map<MapKey, MapValue>.Entry
        implements Stringable
    {
    @Override
    Int estimateStringLength()
        {
        return key.estimateStringLength() + 1 + value.estimateStringLength();
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        key.appendTo(buf);
        buf.add('=');
        value.appendTo(buf);
        return buf;
        }
    }
