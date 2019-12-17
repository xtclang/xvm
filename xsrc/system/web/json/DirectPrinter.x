import collections.ListMap;

/**
 * A "direct printer" for JSON data that emits character data directly to a Char Appender, instead
 * of "building" a JSON `Doc`.
 *
 * This `Printer` implementation does not support construction from a JSON `Doc`, nor does it result
 * in the creation of a JSON `Doc`; its purpose is to print directly to the provided `Appender`,
 * skipping any intermediate representation.
 */
class DirectPrinter
        extends Printer
        implements Builder
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a JSON printer that will print directly to the provided `Appender`.
     *
     * @param out  a Char Appender to print directly to
     */
    construct(Appender<Char> out)
        {
        construct Printer(Null);
        this.out = out;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The destination for the JSON-formatted character data.
     */
    private Appender<Char> out;

    /**
     * The depth of the printer, either from its inception, or its depth within an array.
     */
    private Int depth = 0;

    /**
     * Set to True when the printer is currently printing inside of an array.
     */
    private Boolean insideArray = False;

    /**
     * Set to True when the "print-head" is currently sitting immediately before the first
     * name/value pair in a JSON object.
     */
    private Boolean first = False;


    // ----- builder -------------------------------------------------------------------------------

    @Override
    DirectPrinter add(String name, Doc value)
        {
        if (value != Null || insideArray && depth <= 1)
            {
            printName(name);
            printDoc(value, out);
            }

        return this;
        }

    @Override
    DirectPrinter add(String name, IntNumber value)
        {
        printName(name);
        value.appendTo(out);
        return this;
        }

    @Override
    DirectPrinter add(String name, FPNumber value)
        {
        printName(name);
        value.appendTo(out);
        return this;
        }

    @Override
    DirectPrinter addArray(String name, Doc... values)
        {
        printName(name);
        out.add('[');
        Loop: for (Doc value : values)
            {
            if (!Loop.first)
                {
                out.add(',');
                }
            value.appendTo(out);
            }
        out.add(']');
        return this;
        }

    @Override
    DirectPrinter addArray(String name, IntNumber... values)
        {
        printName(name);
        out.add('[');
        Loop: for (IntNumber value : values)
            {
            if (!Loop.first)
                {
                out.add(',');
                }
            value.appendTo(out);
            }
        out.add(']');
        return this;
        }

    @Override
    DirectPrinter addArray(String name, FPNumber... values)
        {
        printName(name);
        out.add('[');
        Loop: for (FPNumber value : values)
            {
            if (!Loop.first)
                {
                out.add(',');
                }
            value.appendTo(out);
            }
        out.add(']');
        return this;
        }

    @Override
    DirectPrinter addArray(String name, Int size, function Doc|IntNumber|FPNumber (Int) supply)
        {
        printName(name);
        out.add('[');
        Loop: for (Int i = 0; i < size; ++i)
            {
            if (!Loop.first)
                {
                out.add(',');
                }

            val value = supply(i);
            if (value.is(IntNumber))
                {
                value.appendTo(out);
                }
            else if (value.is(FPNumber))
                {
                value.appendTo(out);
                }
            else
                {
                printDoc(value.as(Doc), out);
                }
            }
        out.add(']');
        return this;
        }

    @Override
    DirectPrinter addArray(String name, Int size, function void (Int, DirectPrinter) print)
        {
        printName(name);
        out.add('[');

        Boolean wasInsideArray = insideArray;
        Int     wasDepth       = depth;

        insideArray = True;
        depth       = 0;

        Loop: for (Int i = 0; i < size; ++i)
            {
            if (!Loop.first)
                {
                out.add(',');
                }

            // print the contents of the element
            print(i, this);

            // close any JSON objects that were opened inside the "print" function
            while (depth > 0)
                {
                out.add('}');
                --depth;
                }
            }
        out.add(']');

        insideArray = wasInsideArray;
        depth       = wasDepth;
        return this;
        }

    @Override
    DirectPrinter enter(String name)
        {
        printName(name);
        out.add('{');
        ++depth;
        first = True;
        return this;
        }

    @Override
    DirectPrinter exit()
        {
        if (depth > 0)
            {
            --depth;
            out.add('}');
            }
        first = False;
        return this;
        }

    @Override
    DirectPrinter close()
        {
        assert !insideArray;
        for (Int i = 0; i < depth; ++i)
            {
            out.add('}');
            }
        depth = 0;
        return this;
        }


    // ----- internal ------------------------------------------------------------------------------

    protected void printName(String name)
        {
        if (depth == 0)
            {
            out.add('{');
            depth = 1;
            first = False;
            }
        else if (first)
            {
            first = False;
            }
        else
            {
            out.add(',');
            }

        printString(name, out);
        out.add(':');
        }


    // ----- rendering -----------------------------------------------------------------------------

    /**
     * @param showNulls  pass `True` to always show null JSON values as "null", or `False` to omit
     *                   them when possible (optional; defaults to False)
     * @param pretty     pass `True` to render the JSON document in a visually hierarchical manner
     *                   designed for human eyes, or `False` to suppress white-space wherever
     *                   possible (optional; defaults to False)
     */
    @Override
    String toString(Boolean showNulls = False, Boolean pretty = False)
        {
        return NO_DOC;
        }

    /**
     * @param showNulls  pass `True` to always show null JSON values as "null", or `False` to omit
     *                   them when possible (optional; defaults to False)
     * @param pretty     pass `True` to render the JSON document in a visually hierarchical manner
     *                   designed for human eyes, or `False` to suppress white-space wherever
     *                   possible (optional; defaults to False)
     */
    @Override
    Int estimateStringLength(Boolean showNulls = False, Boolean pretty = False)
        {
        return NO_DOC.size;
        }

    /**
     * @param showNulls  pass `True` to always show null JSON values as "null", or `False` to omit
     *                   them when possible (optional; defaults to False)
     * @param pretty     pass `True` to render the JSON document in a visually hierarchical manner
     *                   designed for human eyes, or `False` to suppress white-space wherever
     *                   possible (optional; defaults to False)
     */
    @Override
    void appendTo(Appender<Char> appender, Boolean showNulls, Boolean pretty)
        {
        NO_DOC.appendTo(appender);
        }

    private static String NO_DOC = "DirectPrinter(Appender<Char>)";
    }