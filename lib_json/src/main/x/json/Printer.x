/**
 * A "printer" for JSON objects.
 */
const Printer(Boolean showNulls = False, Boolean pretty = False)
    {
    // ----- pre-configured instances --------------------------------------------------------------

    /**
     * The default renderer for JSON documents that prints a compact form.
     */
    static Printer DEFAULT = new Printer();

    /**
     * A renderer for JSON documents that prints a human-readable form.
     */
    static Printer PRETTY  = new Printer(pretty = True);

    /**
     * A renderer for JSON documents that prints a human-readable form useful for debugging.
     */
    static Printer DEBUG   = new Printer(showNulls = True, pretty = True);


    // ----- printer API ---------------------------------------------------------------------------

    /**
     * Render a JSON document as a String as it would be printed by this `Printer`.
     *
     * @param doc  the JSON document to render
     *
     * @return the document rendered as a `String`
     */
    String render(Doc doc)
        {
        StringBuffer buf = new StringBuffer(estimatePrintLength(doc));
        print(doc, buf);
        return buf.toString();
        }

    /**
     * Calculate the String length of the specified JSON document as it would be printed by this
     * `Printer`.
     *
     * @return an estimated number of characters necessary to hold the resulting rendered document
     */
    Int estimatePrintLength(Doc doc)
        {
        return estimateInternal(doc, showNulls=showNulls, pretty=pretty);
        }

    /**
     * Print the specified JSON document to the provided [Appender].
     *
     * @param doc  the JSON document to print
     * @param buf  the [Appender] to render the character data into
     */
    void print(Doc doc, Appender<Char> buf)
        {
        printInternal(doc, buf, alreadyIndented=True, showNulls=showNulls, pretty=pretty);
        }


    // ----- rendering -----------------------------------------------------------------------------

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
    protected Int estimateInternal(Doc     doc,
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
            Int total = 2 + doc.size;
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
                    total += estimateInternal(value, indent=indent, alreadyIndented=True,
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
                total += estimateInternal(value, indent=indent, pretty=pretty);
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
                    total += estimateInternal(name , indent=deeper, showNulls=showNulls, pretty=pretty)
                           + estimateInternal(value, indent=deeper, showNulls=showNulls, pretty=pretty);
                    }
                }
            return total;
            }
        }

    /**
     * Render the specified JSON document into an `Appender`.
     *
     * @param doc              the document to render
     * @param buf              the Appender to render the character data into
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
    protected void printInternal(Doc            doc,
                                 Appender<Char> buf,
                                 Int            indent          = 0,
                                 Boolean        alreadyIndented = False,
                                 Boolean        showNulls       = False,
                                 Boolean        pretty          = False)
        {
        if (doc.is(Enum))
            {
            (switch (doc)
                {
                case Null: "null";
                case True: "true";
                case False: "false";
                default: assert;
                }).appendTo(buf);
            }
        else if (doc.is(String))
            {
            printString(doc, buf);
            }
        else if (doc.is(IntLiteral) || doc.is(FPLiteral))
            {
            doc.appendTo(buf);
            }
        else if (doc.is(Array))
            {
            Doc[] array = doc.as(Doc[]);
            if (pretty && containsObject(array))
                {
                printMultiLineArray(array, buf, indent, alreadyIndented, showNulls);
                }
            else
                {
                printSingleLineArray(array, buf, pretty);
                }
            }
        else
            {
            Map<String, Doc> map = doc.as(Map<String, Doc>);
            if (map.empty || !showNulls && containsOnlyNulls(map))
                {
                "{}".appendTo(buf);
                }
            else if (pretty)
                {
                printPrettyObject(map, buf, indent, alreadyIndented, showNulls);
                }
            else
                {
                printUglyObject(map, buf, showNulls);
                }
            }
        }

    protected void printSingleLineArray(Doc[] values, Appender<Char> buf, Boolean pretty)
        {
        buf.add('[');
        Loop: for (Doc value : values)
            {
            if (!Loop.first)
                {
                buf.add(',');
                if (pretty)
                    {
                    buf.add(' ');
                    }
                }
            printInternal(value, buf, pretty=pretty, showNulls=True);
            }
        buf.add(']');
        }

    protected void printMultiLineArray(Doc[]          values,
                                       Appender<Char> buf,
                                       Int            indent,
                                       Boolean        alreadyIndented,
                                       Boolean        showNulls)
        {
        if (!alreadyIndented)
            {
            indentLine(buf, indent);
            }
        buf.add('[');

        Loop: for (Doc value : values)
            {
            if (!Loop.first)
                {
                buf.add(',');
                }

            indentLine(buf, indent);
            printInternal(value, buf, indent=indent, alreadyIndented=True, pretty=True, showNulls=showNulls);
            }

        indentLine(buf, indent);
        buf.add(']');
        }

    protected void printUglyObject(Map<String, Doc> map, Appender<Char> buf, Boolean showNulls)
        {
        buf.add('{');
        Boolean comma = False;
        for ((String name, Doc value) : map)
            {
            if (value != Null || showNulls)
                {
                if (comma)
                    {
                    buf.add(',');
                    }
                else
                    {
                    comma = True;
                    }

                printString(name, buf);
                buf.add(':');
                printInternal(value, buf, showNulls=showNulls);
                }
            }
        buf.add('}');
        }

    protected void printPrettyObject(Map<String, Doc> map,
                                     Appender<Char>   buf,
                                     Int              indent,
                                     Boolean          alreadyIndented,
                                     Boolean          showNulls)
        {
        if (!alreadyIndented)
            {
            indentLine(buf, indent);
            }
        buf.add('{');

        Boolean comma = False;
        for ((String name, Doc value) : map)
            {
            if (value != Null || showNulls)
                {
                if (comma)
                    {
                    buf.add(',');
                    }
                else
                    {
                    comma = True;
                    }

                indentLine(buf, indent);
                printString(name, buf);
                ": ".appendTo(buf);
                printInternal(value, buf, indent=indent+2, pretty=True, showNulls=showNulls);
                }
            }

        indentLine(buf, indent);
        buf.add('}');
        }

    protected static void indentLine(Appender<Char> buf, Int indent)
        {
        buf.add('\n');
        for (Int i = 0; i < indent; ++i)
            {
            buf.add(' ');
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

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

    static void printString(String value, Appender<Char> buf)
        {
        buf.add('"');
        for (Char ch : value)
            {
            if (String s := escaped(ch))
                {
                s.appendTo(buf);
                }
            else
                {
                buf.add(ch);
                }
            }
        buf.add('"');
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