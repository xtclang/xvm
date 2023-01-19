/**
 * A `Writer` that streams the information to a `Console`.
 */
class ConsoleAppender(Console console, Boolean flushAlways = False)
        implements Writer
    {
    /**
     * Construct a ConsoleAppender.
     *
     * @param console      the Console to print to
     * @param flushAlways  (optional) True indicates that even partial lines are printed; False
     *                     indicates that the ConsoleAppender will wait for a newline to flush to
     *                     the Console; defaults to False
     */
    construct(Console console, Boolean flushAlways = False)
        {
        this.console     = console;
        this.flushAlways = flushAlways;
        if (!flushAlways)
            {
            buf = new Char[];
            }
        }

    /**
     * The console to print to.
     */
    protected Console console;

    /**
     * True means that data is always printed to the console, even without a newline.
     */
    protected Boolean flushAlways;

    /**
     * The underlying representation of a StringBuffer is a mutable array of characters.
     */
    protected @Unassigned Array<Char> buf;


    // ----- Appender methods ----------------------------------------------------------------------

    @Override
    ConsoleAppender add(Char v)
        {
        if (flushAlways)
            {
            console.print(v, suppressNewline=True);
            }
        else
            {
            buf += v;

            if (v.isLineTerminator())
                {
                console.print(new String(buf.freeze(True)), suppressNewline=True);
                buf = new Char[];
                }
            }

        return this;
        }

    @Override
    ConsoleAppender addAll(Iterable<Char> chars)
        {
        if (flushAlways)
            {
            console.print(toStr(chars), suppressNewline=True);
            return this;
            }

        if (chars.is(Char[]))
            {
            // scan backwards through the array for a line terminator
            Loop: for (Int i = chars.size-1; i >= 0; --i)
                {
                if (chars[i].isLineTerminator())
                    {
                    // new line indicates that a flush is required; there are four possible scenarios:
                    //
                    //    \  array | entire | partial |
                    // buf \       |--------|---------|
                    //   empty     |   x    |   x     |
                    //   not empty |   x    |   x     |
                    // ------------|--------|---------|
                    switch (buf.empty, Loop.first)
                        {
                        case (False, False):
                            // buffer and part of the array to print; part of the array to retain
                            buf += chars[0 ..< i];
                            console.print(new String(buf), suppressNewline=True);
                            buf.clear().addAll(chars[i+1..chars.size-1]);
                            break;

                        case (False, True):
                            // buffer and all of the array to print; nothing to retain
                            buf += chars;
                            console.print(new String(buf), suppressNewline=True);
                            buf.clear();
                            break;

                        case (True, False):
                            // no buffer; part of the array to print; part of the array to retain
                            console.print(toStr(chars[0 ..< i]), suppressNewline=True);
                            buf.addAll(chars[i+1..chars.size-1]);
                            break;

                        case (True, True):
                            // no buffer to print; print the whole array; nothing to clear
                            console.print(toStr(chars), suppressNewline=True);
                            break;
                        }

                    return this;
                    }
                }
            }

        return super(chars);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @param chars  an `Iterable` of `Char`
     *
     * @return a `String`
     */
    protected String toStr(Iterable<Char> chars)
        {
        if (chars.is(String))
            {
            return chars;
            }

        if (chars.is(Char[]))
            {
            return new String(chars);
            }

        return new String(chars.toArray(Constant));
        }
    }
