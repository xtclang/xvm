/**
 * A [Stringable] implementation for [Collection] implementations.
 */
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

        return size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        Boolean curly = this.is(Set);
        return join(buf, pre=$"{&this.actualClass}{curly ? '{' : '['}", post=curly ? "}" : "]");
        }
    }
