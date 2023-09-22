import Uri.Position;
import Uri.Section;

/**
 * An implementation of the URI Template specification.
 *
 * @see https://tools.ietf.org/html/rfc6570
 */
const UriTemplate {
    /**
     * A map containing the result of matching a UriTemplate against a request's Uri.
     */
    typedef immutable Map<String, Value> as UriParameters;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a URI template.
     *
     * @param the template string, as defined by RFC 6570
     */
    construct(String template) {
        assert (parts, implicitSection, vars) := parse(template)
                as $"Failed to parse URI template: {template.quoted()}";
    }

    /**
     * Construct a trivial "root" URI template.
     */
    private construct() {
        implicitSection = [];
        vars            = [];
        parts           = vars;
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

    /**
     * The literal prefix part for this UriTemplate.
     */
     String literalPrefix.get() {
        if (parts.size > 0, String prefix := parts[0].is(String)) {
            return prefix;
        }
        return "";
    }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Test if the specified URI matches this template.
     *
     * @param uri  the `Uri` to test to see if it matches this `UriTemplate`
     *
     * @return a map from variable name to value
     */
    conditional UriParameters matches(Uri|String uri) {
        // convert a String URI to a real Uri object if necessary
        if (uri.is(String), !(uri := Uri.fromString(uri))) {
            return False;
        }

        // peel off the first literal, if there is one, so that the cadence is always "match
        // expression(s) followed by a literal (or perhaps no literal, at the end)"
        (String|Expression)[] parts = this.parts;
        Int                   count = parts.size;

        if (count == 0) {
            // UriTemplate.ROOT only matches the root path
            return uri.path == "/" ? (True, []) : False;
        }

        Position position = Start;
        Int      next     = 0;

        if (String literal := parts[next].is(String)) {
            if (position := uri.matches(literal, position)) {
                ++next;
            } else {
                return False;
            }
        }

        Map<String, Value> bindings = [];

        Int       nextLiteral  = -1;    // forces a search for the next literal
        Position? foundLiteral = Null;  // the start of the literal
        Position? afterLiteral = Null;  // after the literal
        while (next < count) {
            switch (next <=> nextLiteral) {
            case Lesser:
                // the current part is an Expression; match it
                assert Expression expression := parts[next].is(Expression);

                Char? nextPrefix = Null;
                if (next+1 < count, Expression nextExpression := parts[next].is(Expression)) {
                    nextPrefix = nextExpression.prefix;
                }

                if ((position, bindings) := expression.matches(
                        uri, position, foundLiteral, nextPrefix, bindings)) {
                    ++next;
                } else {
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
                while (True) {
                    Expression|String part = parts[index];
                    if (part.is(String)) {
                        if ((foundLiteral, afterLiteral) := uri.find(part, searchFrom)) {
                            nextLiteral = index;
                            break;
                        } else {
                            return False;
                        }
                    } else if (Section section ?= implicitSection[index], section > searchFrom.section) {
                        // REVIEW CP in case "{/path}/" (use Expression.prefix)
                        searchFrom = section.start;
                    }

                    if (++index >= count) {
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

        // do not allow only a portion of the path to be matched
        switch (position.section) {
        case Scheme:
        case Authority:
            return False;

        case Path:
            if (position.offset < uri.path.toString().size) {
                return False;
            }
            continue;
        default:
            return True, bindings.makeImmutable();
        }
    }

    /**
     * Expand the URI template, using the provided values.
     *
     * @param values  a mapping from variable name to value; missing variables are treated as
     *                `undefined`
     *
     * @return the formatted URI
     */
    String format(Lookup|Map<String, Value> values) {
        if (values.is(Map<String, Value>)) {
            values = values.get;
        }

        StringBuffer buf = new StringBuffer();
        for (String|Expression part : parts) {
            if (part.is(String)) {
                part.appendTo(buf); // REVIEW - check RFC for what literal has to encode if any
            } else {
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
     */
    typedef String | List<String> | Map<String, String> as Value;

    /**
     * When expanding a URL template, the variable values are provided as a function that can be
     * called for each variable name. Note that this function conveniently (and purposefully) has
     * the same signature as `Map<String, Value>.get(String)`.
     */
    typedef function conditional Value(String) as Lookup;

    static const Variable(String name, Int? maxLength = Null, Boolean explode = False) {
        @Override
        Int estimateStringLength() {
            return name.size + (explode ? 1 : maxLength?.estimateStringLength() + 1 : 0);
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            name.appendTo(buf);

            if (explode) {
                return buf.add('*');
            }

            if (Int max ?= maxLength) {
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
    static @Abstract const Expression(Variable[] vars) {
        /**
         * Some expression types are only valid within a specific Section. If so, then this property
         * will specify that Section.
         *
         * An expression that is valid within any section will have a value of Null for this
         * property.
         */
        @RO Section? onlyWithin.get() {
            return Null;
        }

        /**
         * This is the character (`+`, `#`, `.`, `/`, `;`, ;;?`, `&`) that indicates the form of the
         * expression, or `Null` in the case of a simple string expression.
         */
        @RO Char? prefix;

        /**
         * Given the specified value lookup for the variables in the expression, expand the
         * expression following the rules defined in the URI Template specification.
         *
         * @param buf     the buffer to append the expanded expression to
         * @param values  the means of looking up the values for variables within the expression
         *
         * @return the buffer
         */
        @Abstract Appender<Char> expand(Appender<Char> buf, Lookup values);

        /**
         * Match this expression against the specified range within the Uri.
         *
         * @param uri         the URI to match against
         * @param from        the starting position (inclusive) within the URI which this expression
         *                    is permitted to match against
         * @param to          the ending position (exclusive) within the URI which this expression
         *                    is permitted to match against
         * @param nextPrefix  the character that begins the next expression (if this expression is
         *                    followed by another expression)
         * @param bindings    the previously bound variables (from matching preceding expressions)
         *
         * @return `True` iff the expression successfully matched against the URI
         * @return (conditional) the position immediately following the matched region of the URI
         * @return (conditional) the potentially updated map of variable bindings
         */
        @Abstract conditional (Position after, Map<String, Value> bindings) matches(Uri uri,
                Position from, Position? to, Char? nextPrefix, Map<String, Value> bindings);

        @Override
        Int estimateStringLength() {
            return 2 + (prefix==Null ? 0 : 1) + vars.size - 1
                     + vars.map(Variable.estimateStringLength)
                           .reduce(0, (s1, s2) -> s1 + s2);
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
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
            extends Expression(vars) {
        @Override
        @RO Char? prefix.get() {
            return Null;
        }

        @Override
        Appender<Char> expand(Appender<Char> buf, Lookup values) {
            @Volatile Boolean first = True;
            function void() checkComma = () -> {
                // multiple defined variables are comma delimited (i.e. ignore undefined)
                if (first) {
                    first = False;
                } else {
                    buf.add(',');
                }
            };

            for (Variable var : vars) {
                if (Value value := values(var.name)) {
                    switch (value.is(_)) {
                    case String:
                        checkComma();
                        render(buf, (value[0 ..< var.maxLength?] : value));
                        break;

                    case List<String>:
                        // same rendering regardless of "explode"
                        for (String item : value) {
                            checkComma();
                            render(buf, item);
                        }
                        break;

                    case Map<String, String>:
                        Char delim = var.explode ? '=' : ',';
                        for ((String key, String val) : value) {
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
        conditional (Position after, Map<String, Value> bindings) matches(Uri uri,
                Position from, Position? to, Char? nextPrefix, Map<String, Value> bindings) {
            if (nextPrefix? != prefix) {
                to := uri.find(nextPrefix.toString(), from, to);
            }
            to ?:= uri.endPosition;

            String text = uri.canonicalSlice(from, to);
            if (vars.size == 1 && !vars[0].explode) {
                String name = vars[0].name;
                Value? prev = bindings[name];
                if (text.size == 0) {
                    if (prev != Null) {
                        return False;
                    }
                } else if (prev == Null) {
                    bindings = (bindings.empty ? new HashMap<String, Value>() : bindings).put(name, text);
                } else if (!(prev.is(String) && prev == text)) {
                    return False;
                }

                return True, to, bindings;
            } else {
                TODO("delineation of non-delineated data");
            }
        }
    }

    /**
     * Base class for several section-specific ExpressionSegment implementations.
     */
    @Abstract
    static const SectionSpecificExpression(Variable[] vars)
            extends Expression(vars) {
        @Override
        @RO Section onlyWithin;

        @Override
        @RO Char prefix;

        @Override
        Appender<Char> expand(Appender<Char> buf, Lookup values) {
            for (Variable var : vars) {
                if (Value value := values(var.name)) {
                    buf.add(prefix);
                    switch (value.is(_)) {
                    case String:
                        render(buf, (value[0 ..< var.maxLength?] : value));
                        break;

                    case List<String>:
                        Char delim = var.explode ? prefix : ',';
                        Loop: for (String item : value) {
                            if (!Loop.first) {
                                buf.add(delim);
                            }
                            render(buf, item);
                        }
                        break;

                    case Map<String, String>:
                        (Char kvDelim, Char entryDelim) = var.explode ? ('=',prefix) : (',',',');
                        Loop: for ((String key, String val) : value) {
                            if (!Loop.first) {
                                buf.add(entryDelim);
                            }
                            render(buf, key);
                            buf.add(kvDelim);
                            render(buf, val);
                        }
                        break;
                    }
                }
            }
            return buf;
        }

        @Override
        conditional (Position after, Map<String, Value> bindings) matches(Uri uri,
                Position from, Position? to, Char? nextPrefix, Map<String, Value> bindings) {
            String sectionText   = uri.sectionText(onlyWithin);
            Int    sectionLength = sectionText.size;
            if (sectionLength == 0) {
                // no text, no match (because even the prefix is absent)
                return False;
            }

            // we can only match if we are inside the correct section
            if (!(from := uri.positionAt(from, onlyWithin))) {
                return False;
            }
            Int fromOffset = from.offset;

            Int toOffset = sectionLength;
            to := uri.positionAt(to?, onlyWithin);
            switch (to?.section <=> onlyWithin) {
            case Lesser:
                return False;

            case Equal:
                toOffset = to.offset.notGreaterThan(sectionLength);
                if (fromOffset > toOffset) {
                    return False;
                }
                break;

            case Greater:
                // just go to the end of the section
                break;
            }

            if (nextPrefix? != prefix, Int foundAt := sectionText.indexOf(nextPrefix), foundAt < toOffset) {
                toOffset = foundAt;
            }

            if (fromOffset == toOffset) {
                // 0-length means that we can't match a value for the segment, but if it's at the
                // very end, it matches as "null" (i.e. nothing placed in the bindings)
                return fromOffset == sectionLength
                        ? (True, from, bindings)
                        : False;
            }

            String text = sectionText[fromOffset ..< toOffset];
            if (text[0] != prefix) {
                return False;
            }

            Int offset = 0;
            Int length = text.size;
            Loop: for (Variable var : vars) {
                if (offset >= length || text[offset] != prefix) { // TODO secondary prefix
                    break;
                }

                ++offset;

                Int     nextDelim = length;
                Boolean explode   = Loop.last && var.explode;
                if (!explode) {
                    nextDelim := text.indexOf(prefix, offset);
                }

                String segment = text[offset ..< nextDelim];
                Value  value   = explode ? segment.split(prefix) : segment;

                bindings = (bindings.empty ? new HashMap<String, Value>() : bindings).put(var.name, value);
                offset   = nextDelim;
            }

            return True, new Position(onlyWithin, fromOffset + offset), bindings;
        }
    }

    /**
     * Implements "Path Segment Expansion".
     *
     * See section 3.2.6 of RFC 6570.
     */
    static const PathSegment(Variable[] vars)
            extends SectionSpecificExpression(vars) {
        @Override
        Section onlyWithin.get() {
            return Path;
        }

        @Override
        Char prefix.get() {
            return '/';
        }
    }

    /**
     * Implements "Path-Style Parameter Expansion".
     *
     * See section 3.2.7 of RFC 6570.
     */
    static const PathParamSegment(Variable[] vars)
            extends SectionSpecificExpression(vars) {
        @Override
        Section onlyWithin.get() {
            return Path;
        }

        @Override
        Char prefix.get() {
            return ';';
        }
    }

    /**
     * Implements "Form-Style Query Expansion".
     *
     * See section 3.2.8 of RFC 6570.
     */
    static const FormStyleQuery(Variable[] vars)
            extends SectionSpecificExpression(vars) {

        @Override
        Section onlyWithin.get() {
            return Query;
        }

        @Override
        Char prefix.get() {
            return '?';
        }
    }

    /**
     * Implements "Form-Style Query Continuation".
     *
     * See section 3.2.9 of RFC 6570.
     */
    static const FormStyleQueryContinuation(Variable[] vars)
            extends SectionSpecificExpression(vars) {

        @Override
        Section onlyWithin.get() {
            return Query;
        }

        @Override
        Char prefix.get() {
            return '&';
        }
    }

    /**
     * Implements "Fragment Expansion".
     *
     * See section 3.2.4 of RFC 6570.
     */
    static const FragmentSegment(Variable[] vars)
            extends SectionSpecificExpression(vars) {

        @Override
        Section onlyWithin.get() {
            return Fragment;
        }

        @Override
        Char prefix.get() {
            return '#';
        }
    }


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
            parse(String template) {
        (String|Expression)[] parts           = new (String|Expression)[];
        Section?[]            implicitSection = new Section?[];
        String[]              vars            = new String[];

        Int offset = 0;
        Int length = template.size;
        while (offset < length) {
            if (template[offset] == '{') {
                if ((Expression expr, offset) := parseExpression(template, offset, length)) {
                    parts += expr;
                    implicitSection += Null; // TODO
                    expr.vars.forEach(var -> vars.addIfAbsent(var.name));
                } else {
                    return False;
                }
            } else {
                if ((String lit, offset) := parseLiteral(template, offset, length)) {
                    parts += lit;
                    implicitSection += Null; // TODO
                } else {
                    return False;
                }
            }
        }

        return parts.size > 0, parts, implicitSection, vars;

        /**
         * Parse an expression in the form "`{` ... `}`".
         */
        static conditional (Expression expr, Int offset) parseExpression(String template, Int offset, Int length) {
            Variable[] vars = new Variable[];

            // skip opening curly
            ++offset;

            // check for operator
            Char operator = ' ';        // space means "no operator" (aka Simple String Expansion)
            if (offset < length) {
                switch (Char ch = template[offset]) {
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

            NextVar: while (True) {
                Variable var;
                if (!((var, offset) := parseVar(template, offset, length))) {
                    return False;
                }

                vars += var;

                Boolean suffix = False;
                while (offset < length) {
                    switch (template[offset]) {
                    case ',':       // variable list separator
                        ++offset;
                        continue NextVar;

                    case '}':       // end of variable list
                        Expression expr = switch (operator) {
                            case '+': TODO ReservedString
                            case '#': new FragmentSegment(vars);
                            case '.': TODO LabelSegment
                            case '/': new PathSegment(vars);
                            case ';': new PathParamSegment(vars);
                            case '?': new FormStyleQuery(vars);
                            case '&': new FormStyleQueryContinuation(vars);
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
        static conditional (Variable var, Int offset) parseVar(String template, Int offset, Int length) {
            if ((String name, offset) := parseVarName(template, offset, length), offset < length) {
                switch (template[offset]) {
                case ':':       // prefix
                    ++offset;

                    // parse up to 4 digits
                    Int max = 0;
                    for (Int remain = 4; remain > 0; --remain) {
                        if (Int digit := template[offset].asciiDigit()) {
                            max = max * 10 + digit;
                            ++offset;
                        } else if (max == 0) {
                            return False;
                        } else {
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
        static conditional (String name, Int offset) parseVarName(String template, Int offset, Int length) {
            Int     start        = offset;
            Boolean needsVarChar = True;
            NextChar: while (offset < length) {
                switch (template[offset]) {
                case 'A'..'Z', 'a'..'z', '0'..'9', '_':     // varchar
                    needsVarChar = False;
                    ++offset;
                    continue NextChar;

                case '.':
                    if (needsVarChar) {
                        return False;
                    }
                    needsVarChar = True;
                    ++offset;
                    continue NextChar;

                case '%':                                   // pct-encoded varchar
                    // must be followed by 2 hex digits
                    if (offset + 2 < length
                            && template[offset+1].asciiHexit()
                            && template[offset+1].asciiHexit()) {
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
                    : (True, template[start ..< offset], offset);
        }

        /**
         * Parse a non-expression (literal text).
         */
        static conditional (String lit, Int offset) parseLiteral(String template, Int offset, Int length) {
            Int start = offset;
            NextChar: while (offset < length) {
                switch (template[offset]) {
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
                            && template[offset+1].asciiHexit()) {
                        offset += 3;
                        continue NextChar;
                    }
                    return False;

                default:
                    return False;
                }
            }

            return True, template[start ..< offset], offset;
        }
    }


    // ----- formatting ----------------------------------------------------------------------------

    /**
     * Render the passed string into the passed buffer.
     *
     * @param buf            the buffer
     * @param value          the string value to render into the buffer
     * @param allowReserved  pass `False` to only allow `unreserved` characters (the rest will
     *                       be percent encoded); pass `True` to also allow `reserved` and
     *                       `pct-encoded` characters (i.e. do not percent encode them)
     *
     * @return the buffer
     */
    static Appender<Char> render(Appender<Char> buf, String? value, Boolean allowReserved=False) {
        if (value?.size > 0) {
            function void(Appender<Char>, Char) emit = allowReserved
                    ? emitReserved
                    : emitUnreserved;

            EachChar: for (Char ch : value) {
                // detect a '%' that needs to be explicitly encoded because it is *NOT* already a
                // pct-encoded string
                if (ch == '%' && allowReserved && (EachChar.count + 2 >= value.size
                        || !value[EachChar.count+1].asciiHexit()
                        || !value[EachChar.count+2].asciiHexit())) {
                    pctEncode(buf, '%');
                } else {
                    emit(buf, ch);
                }
            }
        }
        return buf;

    }

    /**
     * Render the passed character into the passed buffer, escaping everything but `unreserved`.
     *
     * @param buf   the buffer
     * @param char  the character value to render into the buffer
     *
     * @return the buffer
     */
    static Appender<Char> emitUnreserved(Appender<Char> buf, Char char) {
        switch (char) {
        case 'A'..'Z', 'a'..'z', '0'..'9', '-', '.', '_', '~':  // unreserved
            return buf.add(char);

        case '\0'..'\d':
            // it's an ASCII byte (0..127 aka "DEL")
            return pctEncode(buf, char);

        default:
            if (char.codepoint <= Byte.MaxValue) {
                return pctEncode(buf, char);
            }

            // UTF-8 encode and try again
            for (Byte byte : char.utf8()) {
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
     * @param buf   the buffer
     * @param char  the character value to render into the buffer
     *
     * @return the buffer
     */
    static Appender<Char> emitReserved(Appender<Char> buf, Char char) {
        switch (char) {
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
            if (char.codepoint <= Byte.MaxValue) {
                return pctEncode(buf, char);
            }

            // UTF-8 encode and try again
            for (Byte byte : char.utf8()) {
                emitReserved(buf, byte.toChar());
            }
            return buf;
        }
    }

    /**
     * Render the passed character into the passed buffer, by percent encoding.
     *
     * @param buf   the buffer
     * @param char  the character value in the range `[0..255]` to render into the buffer
     *
     * @return the buffer
     */
    static Appender<Char> pctEncode(Appender<Char> buf, Char char) {
        Byte byte = char.toByte();
        return buf.add('%')
                  .add(byte.highNibble.toChar())
                  .add(byte.lowNibble.toChar());
    }

    @Override
    Int estimateStringLength() {
        return parts.map(Stringable.estimateStringLength)
                    .reduce(0, (s1, s2) -> s1 + s2);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        parts.forEach(p -> p.appendTo(buf));
        return buf;
    }

    static UriTemplate ROOT = new UriTemplate();
}