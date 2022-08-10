import net.URI;
import net.URI.Position;
import net.URI.Section;

/**
 * An implementation of the URI Template specification.
 *
 * @see https://tools.ietf.org/html/rfc6570
 */
const UriTemplate(String template)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a URI template.
     *
     * @param the template string, as defined by RFC 6570
     */
    construct(String template)
        {
        assert (parts, implicitSection, vars) := parse(template)
                as $"Failed to parse URI template: {template.quoted()}";
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The structure of the URI template: A sequence of parts.
     */
    (String|Expression)[] parts;

    /**
     * Each part may imply a transition to a new section of the URI.
     */
    Section?[] implicitSection;

    /**
     * The variable names parsed from the URI template.
     */
    String[] vars;


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Test if the specified URI matches this template.
     *
     * @param uri  the `URI` to test to see if it matches this `UriTemplate`
     *
     * @return a map from variable name to value
     */
    conditional Map<String, Value> matches(URI|String uri)
        {
        // convert a String URI to a real URI object if necessary
        if (uri.is(String), !(uri := URI.fromString(uri)))
            {
            return False;
            }

        Position position = START;

        // peel off the first literal, if there is one, so that the cadence is always "match
        // expression(s) followed by a literal (or perhaps no literal, at the end)"
        (String|Expression)[] parts = this.parts;
        Int                   count = parts.size;
        Int                   next  = 0;
        if (String literal := parts[next].is(String))
            {
            if (position := uri.matches(literal, position))
                {
                ++next;
                }
            else
                {
                return False;
                }
            }

        Map<String, Value> bindings = [];

        Int       nextLiteral  = -1;    // forces a search for the next literal
        Position? foundLiteral = Null;  // the start of the literal
        Position? afterLiteral = Null;  // after the literal
        while (next < count)
            {
            switch (next <=> nextLiteral)
                {
                case Lesser:
                    // the current part is an Expression; match it
                    assert Expression expression := parts[next].is(Expression);
                    if ((position, bindings) := expression.matches(uri, position, foundLiteral, bindings))
                        {
                        ++next;
                        }
                    else
                        {
                        return False;
                        }
                    break;

                case Equal:
                    // the current part is a literal that we already evaluated
                    position = afterLiteral ?: assert;
                    ++next;
                    break;

                case Greater:
                    // find the next literal
                    Position searchFrom = position;
                    Int      index      = next;
                    while (True)
                        {
                        Expression|String part = parts[index];
                        if (part.is(String))
                            {
                            if ((foundLiteral, afterLiteral) := uri.find(part, searchFrom))
                                {
                                nextLiteral = index;
                                break;
                                }
                            else
                                {
                                return False;
                                }
                            }
                        else if (Section section ?= implicitSection[index], section > searchFrom.section)
                            {
                            // REVIEW CP in case "{/path}/" (use Expression.prefix)
                            searchFrom = section.start;
                            }

                        if (++index >= count)
                            {
                            // there is no next literal; pretend that it comes after the last part
                            nextLiteral  = index;
                            foundLiteral = Null;
                            afterLiteral = Null;
                            break;
                            }
                        }
                    break;
                }
            }

        // REVIEW CP what to do with anything left over at the end of the URI?

        return True, bindings;
        }

    /**
     * Expand the URI template, using the provided values.
     *
     * @param values  a mapping from variable name to value; missing variables are treated as
     *                `undefined`
     *
     * @return the formatted URI
     */
    String format(Lookup|Map<String, Value> values)
        {
        if (values.is(Map<String, Value>))
            {
            values = values.get;
            }

        StringBuffer buf = new StringBuffer();
        for (String|Expression part : parts)
            {
            if (part.is(String))
                {
                part.appendTo(buf); // REVIEW - check RFC for what literal has to encode if any
                }
            else
                {
                part.expand(buf, values);
                }
            }
        return buf.toString();
        }


    // ----- structures ----------------------------------------------------------------------------

    /**
     * When expanding a URL template, each variable values is either a `String`, a list of `String`,
     * or a `Map` whose keys and values are Strings. A missing value is considered to be `undefined`
     * as defined by RFC 6570. An empty `String` is not undefined; however, an empty list and an
     * empty map are both considered to be undefined (as per section 2.3 in the RFC).
     * REVIEW Map<String, String> or List<Tuple<String, String>>
     */
    typedef String | List<String> | Map<String, String> as Value;

    /**
     * When expanding a URL template, the variable values are provided as a function that can be
     * called for each variable name. Note that this function conveniently (and purposefully) has
     * the same signature as `Map<String, Value>.get(String)`.
     */
    typedef function conditional Value(String) as Lookup;

    static const Variable(String name, Int? maxLength = Null, Boolean explode = False)
        {
        @Override
        Int estimateStringLength()
            {
            return name.size + (explode ? 1 : maxLength?.estimateStringLength() + 1 : 0);
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            name.appendTo(buf);

            if (explode)
                {
                return buf.add('*');
                }

            if (Int max ?= maxLength)
                {
                return max.appendTo(buf.add(':'));
                }

            return buf;
            }
        }

    /**
     * Each non-literal part of a URL template is called an "expression".  When expanding a URL
     * template, each expression is expanded by looking up the variables that compose the
     * expression, and formatting the values of those variables following the rules specific to the
     * specific form of the Expression.
     */
    static @Abstract const Expression(Variable[] vars)
        {
        @RO Char? prefix;

        @RO Section? implicitSection;

        @Abstract StringBuffer expand(StringBuffer buf, Lookup values);

        @Abstract conditional (Position after, Map<String, Value> bindings)
                matches(URI uri, Position from, Position? to, Map<String, Value> bindings);

        @Override
        Int estimateStringLength()
            {
            return 2 + (prefix==Null ? 0 : 1) + vars.size - 1
// TODO GG          + vars.map(Var.estimateStringLength).reduce(0, (s1, s2) -> s1 + s2);
                    + vars.map(v -> v.estimateStringLength()).reduce(0, (s1, s2) -> s1 + s2);
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            buf.add('{');
            buf.add(prefix?);
            vars.appendTo(buf, sep=",", pre="", post="");
            buf.add('}');
            return buf;
            }
        }

    /**
     * Implements "Simple String Expansion".
     *
     * See section 3.2.2 of RFC 6570.
     */
    static const SimpleString(Variable[] vars)
            extends Expression(vars)
        {
        @Override
        @RO Char? prefix.get()
            {
            return Null;
            }

        @Override
        StringBuffer expand(StringBuffer buf, Lookup values)
            {
            Boolean first = True;
            function void() checkComma = () ->
                {
                // multiple defined variables are comma delimited (i.e. ignore undefined)
                if (first)
                    {
                    first = False;
                    }
                else
                    {
                    buf.add(',');
                    }
                };

            for (Variable var : vars)
                {
                if (Value value := values(var.name))
                    {
                    switch (value.is(_))
                        {
                        case String:
                            checkComma();
                            render(buf, (value[0..var.maxLength?) : value));
                            break;

                        case List<String>:
                            // same rendering regardless of "explode"
                            for (String item : value)
                                {
                                checkComma();
                                render(buf, item);
                                }
                            break;

                        case Map<String, String>:
                            Char delim = var.explode ? '=' : ',';
                            for ((String key, String val) : value)
                                {
                                checkComma();
                                render(buf, key);
                                buf.add(delim);
                                render(buf, val);
                                }
                            break;
                        }
                    }
                }
            return buf;
            }

        @Override
        conditional (Position after, Map<String, Value> bindings)
                matches(URI uri, Position from, Position? to, Map<String, Value> bindings)
            {
            TODO
            }
        }

    // TODO - each specific expression type


    // ----- parsing -------------------------------------------------------------------------------

    /**
     ALPHA          =  %x41-5A / %x61-7A   ; A-Z / a-z
     DIGIT          =  %x30-39             ; 0-9
     HEXDIG         =  DIGIT / "A" / "B" / "C" / "D" / "E" / "F"
                       ; case-insensitive

     pct-encoded    =  "%" HEXDIG HEXDIG
     unreserved     =  ALPHA / DIGIT / "-" / "." / "_" / "~"
     reserved       =  gen-delims / sub-delims
     gen-delims     =  ":" / "/" / "?" / "#" / "[" / "]" / "@"
     sub-delims     =  "!" / "$" / "&" / "'" / "(" / ")"
                    /  "*" / "+" / "," / ";" / "="

     ucschar        =  %xA0-D7FF / %xF900-FDCF / %xFDF0-FFEF
                    /  %x10000-1FFFD / %x20000-2FFFD / %x30000-3FFFD
                    /  %x40000-4FFFD / %x50000-5FFFD / %x60000-6FFFD
                    /  %x70000-7FFFD / %x80000-8FFFD / %x90000-9FFFD
                    /  %xA0000-AFFFD / %xB0000-BFFFD / %xC0000-CFFFD
                    /  %xD0000-DFFFD / %xE1000-EFFFD

     iprivate       =  %xE000-F8FF / %xF0000-FFFFD / %x100000-10FFFD
    */

    /**
     * Parse the specified template string into the literal and expression parts that compose it.
     */
    static conditional ((String|Expression)[] parts, Section?[] implicitSection, String[] vars)
            parse(String template)
        {
        (String|Expression)[] parts             = new (String|Expression)[];
        Section?[]            implicitSection = new Section?[];
        String[]              vars              = new String[];

        Int offset = 0;
        Int length = template.size;
        while (offset < length)
            {
            if (template[offset] == '{')
                {
                if ((Expression expr, offset) := parseExpression(template, offset, length))
                    {
                    parts += expr;
                    implicitSection += Null; // TODO
                    expr.vars.forEach(var -> vars.addIfAbsent(var.name));
                    }
                else
                    {
                    return False;
                    }
                }
            else
                {
                if ((String lit, offset) := parseLiteral(template, offset, length))
                    {
                    parts += lit;
                    implicitSection += Null; // TODO
                    }
                else
                    {
                    return False;
                    }
                }
            }

        return parts.size > 0, parts, implicitSection, vars;

        /**
         * Parse an expression in the form "`{` ... `}`".
         */
        static conditional (Expression expr, Int offset) parseExpression(String template, Int offset, Int length)
            {
            Variable[] vars = new Variable[];

            // skip opening curly
            ++offset;

            // check for operator
            Char operator = ' ';        // space means "no operator" (aka Simple String Expansion)
            if (offset < length)
                {
                switch (Char ch = template[offset])
                    {
                    case '+':
                    case '#':
                    case '.':
                    case '/':
                    case ';':
                    case '?':
                    case '&':
                        operator = ch;
                        offset  += 1;
                        break;

                    default:
                        break;
                    }
                }

            NextVar: while (True)
                {
                Variable var;
                if (!((var, offset) := parseVar(template, offset, length)))
                    {
                    return False;
                    }

                vars += var;

                Boolean suffix = False;
                while (offset < length)
                    {
                    switch (template[offset])
                        {
                        case ',':       // variable list separator
                            ++offset;
                            continue NextVar;

                        case '}':       // end of variable list
                            Expression expr = switch (operator)
                                {
                                case '+': TODO
                                case '#': TODO
                                case '.': TODO
                                case '/': TODO
                                case ';': TODO
                                case '?': TODO
                                case '&': TODO
                                default:  new SimpleString(vars);
                                };
                            return True, expr, offset+1;

                        default:
                            return False;
                        }
                    }
                }
            }

        /**
         * Parse a variable (including suffix) inside an expression.
         */
        static conditional (Variable var, Int offset) parseVar(String template, Int offset, Int length)
            {
            if ((String name, offset) := parseVarName(template, offset, length), offset < length)
                {
                switch (template[offset])
                    {
                    case ':':       // prefix
                        ++offset;

                        // parse up to 4 digits
                        Int max = 0;
                        for (Int remain = 4; remain > 0; --remain)
                            {
                            if (Int digit := template[offset].asciiDigit())
                                {
                                max = max * 10 + digit;
                                ++offset;
                                }
                            else if (max == 0)
                                {
                                return False;
                                }
                            else
                                {
                                break;
                                }
                            }
                        return True, new Variable(name, maxLength=max), offset;

                    case '*':       // explode
                        ++offset;
                        return True, new Variable(name, explode=True), offset;

                    default:
                        return True, new Variable(name), offset;
                    }
                }

            return False;
            }

        /**
         * Parse a variable name inside an expression.
         */
        static conditional (String name, Int offset) parseVarName(String template, Int offset, Int length)
            {
            Int     start        = offset;
            Boolean needsVarChar = True;
            NextChar: while (offset < length)
                {
                switch (template[offset])
                    {
                    case 'A'..'Z', 'a'..'z', '0'..'9', '_':     // varchar
                        needsVarChar = False;
                        ++offset;
                        continue NextChar;

                    case '.':
                        if (needsVarChar)
                            {
                            return False;
                            }
                        needsVarChar = True;
                        ++offset;
                        continue NextChar;

                    case '%':                                   // pct-encoded varchar
                        // must be followed by 2 hex digits
                        if (offset + 2 < length
                                && template[offset+1].asciiHexit()
                                && template[offset+1].asciiHexit())
                            {
                            needsVarChar = False;
                            offset += 3;
                            continue NextChar;
                            }
                        return False;

                    default:
                        break NextChar;
                    }
                }

            return needsVarChar
                    ? False
                    : (True, template[start..offset), offset);
            }

        /**
         * Parse a non-expression (literal text).
         */
        static conditional (String lit, Int offset) parseLiteral(String template, Int offset, Int length)
            {
            Int start = offset;
            NextChar: while (offset < length)
                {
                switch (template[offset])
                    {
                    case '{':
                        break NextChar;

                    case 'A'..'Z', 'a'..'z', '0'..'9', '-', '.', '_', '~':  // unreserved
                    case ':', '/', '?', '#', '[', ']', '@':                 // reserved (gen-delims)
                    case '!', '$', '&', '\'', '(', ')':                     // reserved (sub-delims)
                    case '*', '+', ',', ';', '=':                           // reserved (sub-delims)
                        ++offset;
                        continue NextChar;

                    case '%':
                        // must be followed by 2 hex digits
                        if (offset + 2 < length
                                && template[offset+1].asciiHexit()
                                && template[offset+1].asciiHexit())
                            {
                            offset += 3;
                            continue NextChar;
                            }
                        return False;

                    default:
                        return False;
                    }
                }

            return True, template[start..offset), offset;
            }
        }


    // ----- formatting ----------------------------------------------------------------------------

    /**
     * Render the passed string into the passed buffer.
     *
     * @param buf            the StringBuffer
     * @param value          the string value to render into the buffer
     * @param allowReserved  pass `False` to only allow `unreserved` characters (the rest will
     *                       be percent encoded); pass `True` to also allow `reserved` and
     *                       `pct-encoded` characters (i.e. do not percent encode them)
     *
     * @return the StringBuffer
     */
    static StringBuffer render(StringBuffer buf, String? value, Boolean allowReserved=False)
        {
        if (value?.size > 0)
            {
            function void(StringBuffer, Char) emit = allowReserved
                    ? emitReserved
                    : emitUnreserved;

            EachChar: for (Char ch : value)
                {
                // detect a '%' that needs to be explicitly encoded because it is *NOT* already a
                // pct-encoded string
                if (ch == '%' && allowReserved && (EachChar.count + 2 >= value.size
                        || !value[EachChar.count+1].asciiHexit()
                        || !value[EachChar.count+2].asciiHexit()))
                    {
                    pctEncode(buf, '%');
                    }
                else
                    {
                    emit(buf, ch);
                    }
                }
            }
        return buf;

        }

    /**
     * Render the passed character into the passed buffer, escaping everything but `unreserved`.
     *
     * @param buf   the StringBuffer
     * @param char  the character value to render into the buffer
     *
     * @return the StringBuffer
     */
    static StringBuffer emitUnreserved(StringBuffer buf, Char char)
        {
        switch (char)
            {
            case 'A'..'Z', 'a'..'z', '0'..'9', '-', '.', '_', '~':  // unreserved
                return buf.add(char);

            case '\0'..'\d':
                // it's an ASCII byte (0..127 aka "DEL")
                return pctEncode(buf, char);

            default:
                if (char.codepoint <= Byte.maxvalue)
                    {
                    return pctEncode(buf, char);
                    }

                // UTF-8 encode and try again
                for (Byte byte : char.utf8())
                    {
                    emitUnreserved(buf, byte.toChar());
                    }
                return buf;
            }
        }

    /**
     * Render the passed character into the passed buffer, escaping everything but `unreserved`,
     * `reserved`, and `pct-encoded`. Note: Since this only sees one character, it assumes that
     * the caller handles the `%` character encoding for cases that are not already `pct-encoded`.
     *
     * @param buf   the StringBuffer
     * @param char  the character value to render into the buffer
     *
     * @return the StringBuffer
     */
    static StringBuffer emitReserved(StringBuffer buf, Char char)
        {
        switch (char)
            {
            case 'A'..'Z', 'a'..'z', '0'..'9', '-', '.', '_', '~':  // unreserved
            case ':', '/', '?', '#', '[', ']', '@':                 // reserved (gen-delims)
            case '!', '$', '&', '\'', '(', ')':                     // reserved (sub-delims)
            case '*', '+', ',', ';', '=':                           // reserved (sub-delims)
            case '%':                                               // assume pct-encoded
                return buf.add(char);

            case '\0'..'\d':
                // it's an ASCII byte (0..127 aka "DEL")
                return pctEncode(buf, char);

            default:
                if (char.codepoint <= Byte.maxvalue)
                    {
                    return pctEncode(buf, char);
                    }

                // UTF-8 encode and try again
                for (Byte byte : char.utf8())
                    {
                    emitReserved(buf, byte.toChar());
                    }
                return buf;
            }
        }

    /**
     * Render the passed character into the passed buffer, by percent encoding.
     *
     * @param buf   the StringBuffer
     * @param char  the character value in the range `[0..255]` to render into the buffer
     *
     * @return the StringBuffer
     */
    static StringBuffer pctEncode(StringBuffer buf, Char char)
        {
        Byte byte = char.toByte();
        return buf.add('%')
                  .add(byte.highNibble.toChar())
                  .add(byte.lowNibble.toChar());
        }
    }

