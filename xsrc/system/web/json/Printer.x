import collections.ListMap;

/**
 * A "printer" for a JSON object. This implementation supports the transformation of Ecstasy data
 * structures into JSON objects, and then into character strings that can be used to transmit,
 * store, or view ("pretty print") the JSON-formatted data.
 */
class Printer
        implements Stringable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an empty JSON printer.
     */
    construct()
        {
        }

    /**
     * Construct a JSON printer around a JSON document object.
     *
     * @param doc  a JSON object
     */
    construct(Doc doc)
        {
        this.doc = doc;
        stack.add(doc);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON document, in its current form.
     */
    public/protected Doc doc = Null;

    /**
     * The "path" from the outermost JSON doc "down to" the JSON doc that is currently being added
     * to.
     */
    private Doc[] stack = new Doc[];


    // ----- builder -------------------------------------------------------------------------------

    /**
     * Add the name/value pair to the current JSON object. A JSON Printer represents any number of
     * nested JSON objects; the current JSON object is the last one to have been "entered" but not
     * "exited".
     *
     * @param name   the name for the JSON property
     * @param value  the JSON Doc value for the JSON property
     *
     * @return this Printer
     */
    Printer add(String name, Doc value)
        {
        Map<String, Doc> cur;
        if (doc == Null)
            {
            cur = new ListMap();
            doc = cur;
            stack.add(doc);
            }
        else
            {
            val top = stack[stack.size-1];
            if (top.is(Map<String, Doc>))
                {
                cur = top;
                }
            else
                {
                throw new IllegalState("invalid context from which to add");
                }
            }

        cur.put(name, value);
        return this;
        }

    /**
     * Add the name/value pair to the current JSON object. A JSON Printer represents any number of
     * nested JSON objects; the current JSON object is the last one to have been "entered" but not
     * "exited".
     *
     * @param name   the name for the JSON property
     * @param value  the integer value for the JSON property
     *
     * @return this Printer
     */
    Printer add(String name, IntNumber value)
        {
        return add(name, value.toIntLiteral());
        }

    /**
     * Add the name/value pair to the current JSON object. A JSON Printer represents any number of
     * nested JSON objects; the current JSON object is the last one to have been "entered" but not
     * "exited".
     *
     * @param name   the name for the JSON property
     * @param value  the floating-point number value for the JSON property
     *
     * @return this Printer
     */
    Printer add(String name, FPNumber value)
        {
        return add(name, value.toFPLiteral());
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of JSON values for the JSON property
     *
     * @return this Printer
     */
    Printer addArray(String name, Doc... values)
        {
        return add(name, values.toArray());
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of integer values for the JSON property
     *
     * @return this Printer
     */
    Printer addArray(String name, IntNumber... values)
        {
        return add(name, new Array<IntLiteral>(values.size, (i) -> values[i].toIntLiteral()));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of floating-point number values for the JSON property
     *
     * @return this Printer
     */
    Printer addArray(String name, FPNumber... values)
        {
        return add(name, new Array<FPLiteral>(values.size, (i) -> values[i].toFPLiteral()));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param size    the number of values to place in the array
     * @param supply  the function that will provide the value for each element of the array
     *
     * @return this Printer
     */
    Printer addArray(String name, Int size, function Doc|IntNumber|FPNumber (Int) supply)
        {
        function Doc (Int) transform = supply.is(function Doc (Int))
                ? supply
                : (i) ->
                        {
                        val v = supply(i);
                        return switch()
                            {
                            case v.is(IntNumber): v.as(IntNumber).toIntLiteral();     // TODO GG as() redundant?
                            case v.is(FPNumber) : v.as(FPNumber) .toFPLiteral();
                            default             : v.as(Doc);
                            };
                        };

        return add(name, new Array<Doc>(size, transform));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array composed of
     * JSON objects printed into this Printer via the specified function.
     *
     * @param name   the name for the JSON property
     * @param size   the number of values to place in the array
     * @param print  the function that will print JSON objects into each element of the array
     *
     * @return this Printer
     */
    Printer addArray(String name, Int size, function void (Int, Printer) print)
        {
        Doc[] array = new Array<Doc>(size);
        add(name, array);

        // use the array as a temporary "floor" in the stack to disable unbalanced calls to exit()
        // from within the lambda
        stack.add(array);
        Int popTo = stack.size;

        for (Int i = 0; i < size; ++i)
            {
            ListMap<String, Doc> value = new ListMap();
            array.add(value);
            if (stack.size > popTo)
                {
                stack.delete(stack.size-1 .. popTo);
                }
            stack.add(value);
            print(i, this);
            }

        if (stack.size >= popTo)
            {
            stack.delete(stack.size-1 .. popTo-1);
            }

        return this;
        }

    /**
     * Create a JSON object under the current document, and then "enter" that document so that it
     * becomes the new current document.
     *
     * @param name  the name for the JSON property that will contain the new JSON object
     *
     * @return this Printer
     */
    Printer enter(String name)
        {
        ListMap<String, Doc> value = new ListMap();
        add(name, value);
        stack.add(value);
        return this;
        }

    /**
     * Undo a corresponding previous call to [enter()].
     *
     * @return this Printer
     */
    Printer exit()
        {
        Int index = stack.size-1;
        if (index > 0 && !stack[index].is(Array))
            {
            stack.delete(index);
            }
        return this;
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
        StringBuffer buf = new StringBuffer(estimateStringLength(showNulls, pretty));
        appendTo(buf, showNulls, pretty);
        return buf.toString();
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
        return estimateStringLength(doc, showNulls=showNulls, pretty=pretty);
        }

    /**
     * Calculate the String length of the specified JSON document.
     *
     * @param doc              the document to render
     * @param indent           a level of indent, expressed in a number of spaces
     * @param alreadyIndented  `True` indicates that a value that requires a new line and
     *                         indentation does not have to add the new line and indentation for the
     *                         first line of its output
     * @param showNulls        pass `True` to always show null JSON values as "null", or `False` to
     *                         omit them when possible (optional; defaults to False)
     * @param pretty           pass `True` to render the JSON document in a visually hierarchical
     *                         manner designed for human eyes, or `False` to suppress white-space
     *                         wherever possible (optional; defaults to False)
     *
     * @return an estimated number of characters necessary to hold the resulting rendered document
     */
    Int estimateStringLength(Doc     doc,
                             Int     indent          = 0,
                             Boolean alreadyIndented = False,
                             Boolean showNulls       = False,
                             Boolean pretty          = False)
        {
        if (doc.is(Enum))
            {
            // "null", "true", "false"
            return doc.name.size;
            }
        else if (doc.is(String))
            {
            Int total = 2 + doc.estimateStringLength();
            for (Char ch : doc)
                {
                if (String s := escaped(ch))
                    {
                    total += s.size - 1;
                    }
                }
            return total;
            }
        else if (doc.is(IntLiteral) || doc.is(FPLiteral))
            {
            return doc.as(Stringable).estimateStringLength();
            }
        else if (doc.is(Array))
            {
            Doc[] array = doc.as(Doc[]);
            if (pretty && containsObject(array))
                {
                Int count = array.size;
                Int total = (count + (alreadyIndented ? 1 : 2)) * (1 + indent)  // newlines+indents
                            + count + 1;                                        // commas+brackets
                for (Doc value : array)
                    {
                    total += estimateStringLength(value, indent=indent, alreadyIndented=True,
                                                  pretty=True, showNulls=showNulls);
                    }
                return total;
                }

            // "[" + value + "," + value + "," ... + "]"
            // (pretty uses ", " instead of ",")
            Int total = (array.size - 1) * (pretty ? 2 : 1) + 2;
            for (Doc value : array)
                {
                // nulls must be shown in the array, because the array structure is not sparse
                total += estimateStringLength(value, indent=indent, pretty=pretty);
                }
            return total;
            }
        else
            {
            Map<String, Doc> map = doc.as(Map<String, Doc>);
            if (map.empty || !showNulls && containsOnlyNulls(map))
                {
                return 2; // "{}"
                }

            Int count  = map.size;
            Int margin = pretty ? 1 + indent : 0;               // newline + indent
            Int seps   = pretty ? 3 : 2;                        // every entry has ':' and ',' / '}'
            Int total  = 1 + count * (margin + seps)            // '{' + entries
                       + (alreadyIndented ? 1 : 2) * margin;    // newline and indent for curlies
            Int deeper = indent + 2;
            for ((String name, Doc value) : map)
                {
                if (value == Null && !showNulls)
                    {
                    // this entry will be omitted
                    total -= indent + seps;
                    }
                else
                    {
                    total += estimateStringLength(name , indent=deeper, showNulls=showNulls, pretty=pretty)
                           + estimateStringLength(value, indent=deeper, showNulls=showNulls, pretty=pretty);
                    }
                }
            return total;
            }
        }

    /**
     * @param showNulls  pass `True` to always show null JSON values as "null", or `False` to omit
     *                   them when possible (optional; defaults to False)
     * @param pretty     pass `True` to render the JSON document in a visually hierarchical manner
     *                   designed for human eyes, or `False` to suppress white-space wherever
     *                   possible (optional; defaults to False)
     */
    @Override
    void appendTo(Appender<Char> appender, Boolean showNulls = False, Boolean pretty = False)
        {
        printDoc(doc, appender, alreadyIndented=True, showNulls=showNulls, pretty=pretty);
        }

    /**
     * Render the specified JSON document into an `Appender`.
     *
     * @param doc              the document to render
     * @param appender         the appender to render the character data into
     * @param indent           a level of indent, expressed in a number of spaces
     * @param alreadyIndented  `True` indicates that a value that requires a new line and
     *                         indentation does not have to add the new line and indentation for the
     *                         first line of its output
     * @param showNulls        pass `True` to always show null JSON values as "null", or `False` to
     *                         omit them when possible (optional; defaults to False)
     * @param pretty           pass `True` to render the JSON document in a visually hierarchical
     *                         manner designed for human eyes, or `False` to suppress white-space
     *                         wherever possible (optional; defaults to False)
     */
    void printDoc(Doc            doc,
                  Appender<Char> appender,
                  Int            indent          = 0,
                  Boolean        alreadyIndented = False,
                  Boolean        showNulls       = False,
                  Boolean        pretty          = False)
        {
        if (doc.is(Enum))
            {
            (switch(doc)
                {
                case Null: "null";
                case True: "true";
                case False: "false";
                default: assert;
                }).appendTo(appender);
            }
        else if (doc.is(String))
            {
            printString(doc, appender);
            }
        else if (doc.is(IntLiteral) || doc.is(FPLiteral))
            {
            doc.appendTo(appender);
            }
        else if (doc.is(Array))
            {
            Doc[] array = doc.as(Doc[]);
            if (pretty && containsObject(array))
                {
                printMultiLineArray(array, appender, indent, alreadyIndented, showNulls);
                }
            else
                {
                printSingleLineArray(array, appender, pretty);
                }
            }
        else
            {
            Map<String, Doc> map = doc.as(Map<String, Doc>);
            if (map.empty || !showNulls && containsOnlyNulls(map))
                {
                "{}".appendTo(appender);
                }
            else if (pretty)
                {
                printPrettyObject(map, appender, indent, alreadyIndented, showNulls);
                }
            else
                {
                printUglyObject(map, appender, showNulls);
                }
            }
        }

    protected void printString(String value, Appender<Char> appender)
        {
        appender.add('"');
        for (Char ch : value)
            {
            if (String s := escaped(ch))
                {
                s.appendTo(appender);
                }
            else
                {
                appender.add(ch);
                }
            }
        appender.add('"');

        }

    protected void printSingleLineArray(Doc[] values, Appender<Char> appender, Boolean pretty)
        {
        appender.add('[');
        Loop: for (Doc value : values)
            {
            if (!Loop.first)
                {
                appender.add(',');
                if (pretty)
                    {
                    appender.add(' ');
                    }
                }
            printDoc(value, appender, pretty=pretty, showNulls=True);
            }
        appender.add(']');
        }

    protected void printMultiLineArray(Doc[] values, Appender<Char> appender, Int indent,
                             Boolean alreadyIndented, Boolean showNulls)
        {
        if (!alreadyIndented)
            {
            indentLine(appender, indent);
            }
        appender.add('[');

        Loop: for (Doc value : values)
            {
            if (!Loop.first)
                {
                appender.add(',');
                }

            indentLine(appender, indent);
            printDoc(value, appender, indent=indent, alreadyIndented=True, pretty=True, showNulls=showNulls);
            }

        indentLine(appender, indent);
        appender.add(']');
        }

    protected void printUglyObject(Map<String, Doc> map, Appender<Char> appender, Boolean showNulls)
        {
        appender.add('{');
        Boolean comma = False;
        for ((String name, Doc value) : map)
            {
            if (value != Null || showNulls)
                {
                if (comma)
                    {
                    appender.add(',');
                    }
                else
                    {
                    comma = True;
                    }

                printString(name, appender);
                appender.add(':');
                printDoc(value, appender, showNulls=showNulls);
                }
            }
        appender.add('}');
        }

    protected void printPrettyObject(Map<String, Doc> map, Appender<Char> appender, Int indent,
                                     Boolean alreadyIndented, Boolean showNulls)
        {
        if (!alreadyIndented)
            {
            indentLine(appender, indent);
            }
        appender.add('{');

        Boolean comma = False;
        for ((String name, Doc value) : map)
            {
            if (value != Null || showNulls)
                {
                if (comma)
                    {
                    appender.add(',');
                    }
                else
                    {
                    comma = True;
                    }

                indentLine(appender, indent);
                printString(name, appender);
                ": ".appendTo(appender);
                printDoc(value, appender, indent=indent+2, pretty=True, showNulls=showNulls);
                }
            }

        indentLine(appender, indent);
        appender.add('}');
        }

    protected static void indentLine(Appender<Char> appender, Int indent)
        {
        appender.add('\n');
        for (Int i = 0; i < indent; ++i)
            {
            appender.add(' ');
            }
        }

    /**
     * Determine if the JSON array contains any non-empty JSON objects.
     *
     * @param docs  a JSON array
     *
     * @return True iff there is any non-empty JSON object contained (at any nested depth) with the
     *         JSON array
     */
    static Boolean containsObject(Doc[] docs)
        {
        for (Doc doc : docs)
            {
            if (doc.is(Map) && !doc.empty)
                {
                return True;
                }

            if (doc.is(Doc[]))
                {
                return containsObject(doc);
                }
            }

        return False;
        }

    /**
     * Determine if the specified JSON object contains only null values.
     *
     * @param map  a JSON object (name/value pairs)
     *
     * @return True iff the JSON object has no name/value pairs, or if all of the names are
     *         associated with null values
     */
    static Boolean containsOnlyNulls(Map<String, Doc> map)
        {
        for (Doc doc : map.values)
            {
            if (doc != Null)
                {
                return False;
                }
            }
        return True;
        }

    /**
     * Determine if the specified character should be escaped, and if so, escape it.
     *
     * @param ch  the character
     *
     * @return True iff the character needs to be escaped in a JSON string
     * @return (conditional) the String representing the escaped character
     */
    static conditional String escaped(Char ch)
        {
        if (ch <= '\\')                             // '\'=92=0x5C
            {
            if (ch < ' ' || ch == '"')              // ' '=32=0x20, '"'=34=0x22
                {
                return True, ESCAPES[ch.toInt()];
                }
            if (ch == '\\')
                {
                return True, "\\\\";
                }
            }
        return False;
        }

    /**
     * The pre-computed JSON escape sequences for the first 34 Unicode code-points. (Note that
     * codepoint 33 is not escaped.)
     */
    static String[] ESCAPES =
        [               // hex dec
        "\\" + "u0000", //  00   0
        "\\" + "u0001", //  01   1
        "\\" + "u0002", //  02   2
        "\\" + "u0003", //  03   3
        "\\" + "u0004", //  04   4
        "\\" + "u0005", //  05   5
        "\\" + "u0006", //  06   6
        "\\" + "u0007", //  07   7
        "\\b",          //  08   8
        "\\t",          //  09   9
        "\\n",          //  0A  10
        "\\" + "u000B", //  0B  11
        "\\f",          //  0C  12
        "\\r",          //  0D  13
        "\\" + "u000E", //  0E  14
        "\\" + "u000F", //  0F  15
        "\\" + "u0010", //  10  16
        "\\" + "u0011", //  11  17
        "\\" + "u0012", //  12  18
        "\\" + "u0013", //  13  19
        "\\" + "u0014", //  14  20
        "\\" + "u0015", //  15  21
        "\\" + "u0016", //  16  22
        "\\" + "u0017", //  17  23
        "\\" + "u0018", //  18  24
        "\\" + "u0019", //  19  25
        "\\" + "u001A", //  1A  26
        "\\" + "u001B", //  1B  27
        "\\" + "u001C", //  1C  28
        "\\" + "u001D", //  1D  29
        "\\" + "u001E", //  1E  30
        "\\" + "u001F", //  1F  31
        " ",            //  20  32
        "!",            //  21  33
        "\\\""          //  22  34
        ];
    }