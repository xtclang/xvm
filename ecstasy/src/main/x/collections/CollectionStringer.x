/**
 * A [Stringable] implementation for [Collection] implementations.
 */
@Concurrent
mixin CollectionStringer
        into Collection<Stringable>
        implements Stringable
    {
    @Override
    Int estimateStringLength()
        {
        Int count = &this.actualClass.name.size + 2 + 2 * size;
        for (Element e : this)
            {
            count += e.estimateStringLength();
            }

        return count;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return appendTo(buf, ", ", Null, Null, Null, "...", Null);
        }
    }
