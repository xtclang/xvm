/**
 * A [Stringable] implementation for [Collection] implementations.
 *
 * This mixin can be used as part of a Map implementation as follows:
 *
 *     incorporates conditional MapStringer<Key extends Stringable, Value extends Stringable>
 */
mixin MapStringer<Key extends Stringable, Value extends Stringable>
        into Map<Key, Value>
        implements Stringable
    {
    @Override
    Int estimateStringLength()
        {
        Int total = 3 * size;
        for ((Key key, Value value) : this)
            {
            total += key.estimateStringLength() + value.estimateStringLength();
            }
        return total;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        buf.add('[');

        Loop: for ((Key key, Value value) : this)
            {
            if (!Loop.first)
                {
                ", ".appendTo(buf);
                }
            key.appendTo(buf);
            buf.add('=');
            value.appendTo(buf);
            }

        return buf.add(']');
        }
    }
