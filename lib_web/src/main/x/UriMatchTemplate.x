import ecstasy.text.RegEx;
import ecstasy.text.Match;

/**
 * An extension of UriTemplate that also implements UriMatcher adding
 * the ability to match a URI to this template.
 */
const UriMatchTemplate(String             template,
                       List<PathSegment>  segments,
                       Int                variableCount,
                       Int                rawLength,
                       RegEx              matchRegEx,
                       UriMatchVariable[] variables)
        extends UriTemplate(template, segments, variableCount, rawLength)
        implements UriMatcher
    {
    // ----- properties ----------------------------------------------------------------------------

    @Lazy Boolean isRoot.calc()
        {
        return template.size == 0 || (template.size == 1 && template[0] == '/');
        }

    // ----- factory methods -----------------------------------------------------------------------

    public static UriMatchTemplate from(String template)
        {
        return new UriMatchTemplateParser(template).parse();
        }

    // ----- UriMatcher methods --------------------------------------------------------------------

    @Override
    conditional UriMatchInfo match(String uri)
        {
        // remove any trailing / if URI is more than /
        Int length = uri.size;
        if (length > 1 && uri[length - 1] == '/')
            {
            uri = uri[0..length - 2];
            }

        if (isRoot && (length == 0 || (length == 1 && uri[0] == '/')))
            {
            // URI is just a /
            return True, new DefaultUriMatchInfo(uri, Map<String, Object>:[], this.variables);
            }

        //Remove any url parameters before matching
        if (Int parameterIndex := uri.indexOf('?'))
            {
            uri = uri[0..parameterIndex);
            }

        // remove any trailing /
        if (uri[uri.size - 1] == '/')
            {
            uri = uri.size > 1 ? uri[0..uri.size - 2] : "";
            }

        if (Match match := matchRegEx.match(uri))
            {
            if (variables.empty)
                {
                return True, new DefaultUriMatchInfo(uri, Map<String, Object>:[], variables);
                }

            Int                     count       = match.groupCount;
            ListMap<String, String> variableMap = new ListMap();
            for (Int j = 0; j < this.variables.size; j++)
                {
                Int index = (j * 2) + 2;
                if (index > count)
                    {
                    break;
                    }
                UriMatchVariable variable = variables[j];
                String?          value    = match[index];
                if (value != Null)
                    {
                    variableMap.put(variable.name, value);
                    }
                }
            variableMap.freeze(true);
            return True, new DefaultUriMatchInfo(uri, variableMap, variables);
            }
        return False;
        }

    // ----- inner class: UriMatchSegmentParser ---------------------------------------------------

    /**
     * A URI match template parser that parses a template string into a UriMatchTemplate.
     *
     * @param template   the URI template string to parse
     * @param arguments  the optional parser arguments
     */
    protected static class UriMatchTemplateParser(String template, Object[] arguments = [])
            extends UriTemplateParser(template, arguments)
        {
        private UriMatchTemplate? matchTemplate = Null;

        protected StringBuffer? pattern = Null;

        protected UriMatchVariable[] variables = new Array<UriMatchVariable>();

        @Override
        protected UriMatchTemplate parse()
            {
            UriMatchTemplate? matchTemplate = matchTemplate;
            if (matchTemplate != Null)
                {
                return matchTemplate;
                }

            ParserResult result  = parse(template);
            StringBuffer pattern = this.pattern? : assert;
            RegEx        regex   = new RegEx(pattern.toString());

            matchTemplate = new UriMatchTemplate(result.template,
                                                 result.segments,
                                                 result.variableCount,
                                                 result.rawLength,
                                                 regex,
                                                 variables);
            this.matchTemplate = matchTemplate;
            return matchTemplate;
            }

        @Override
        protected UriMatchSegmentParser createSegmentParser(String template, Object[] arguments = [])
            {
            pattern = new StringBuffer();
            return new UriMatchSegmentParser(template, this);
            }
        }

    // ----- inner class: UriMatchSegmentParser ---------------------------------------------------

    /**
     * An extended version of UriTemplate.SegmentParser that builds a regular expression to
     * match a path.
     *
     * Note: Fragment (#) and query (?) parts of the URI are ignored for the purposes of matching.
     */
    static class UriMatchSegmentParser(String templateText, UriMatchTemplateParser templateParser)
            extends SegmentParser(templateText)
        {
        @Override
        protected void addRawContentSegment(List<UriTemplate.PathSegment> segments,
                                            String                        value,
                                            Boolean                       isQuerySegment)
            {
            StringBuffer pattern = templateParser.pattern? : assert;
            pattern.append(RegEx.toLiteral(value));
            super(segments, value, isQuerySegment);
            }

        @Override
        protected void addVariableSegment(List<UriTemplate.PathSegment> segments,
                                       String variable,
                                       String? prefix,
                                       String? delimiter,
                                       Boolean encode,
                                       Boolean repeatPrefix,
                                       String modifierStr,
                                       Char modifierChar,
                                       Char operator,
                                       String? previousDelimiter,
                                       Boolean isQuerySegment)
            {
            templateParser.variables.add(new UriMatchVariable(variable, modifierChar, operator));

            Int     modLen             = modifierStr.size;
            Boolean hasModifier        = modifierChar == ':' && modLen > 0;
            String  operatorPrefix     = "";
            String  operatorQuantifier = "";
            String  variableQuantifier = "+?)";
            String  variableRegEx      = getVariableRegEx(variable, operator);

            if (hasModifier)
                {
                Char firstChar = modifierStr[0];
                if (firstChar == '?')
                    {
                    operatorQuantifier = "";
                    }
                else if (modifierStr.iterator().whileEach(c -> c.asciiDigit()))
                    {
                    variableQuantifier = "{1," + modifierStr + "})";
                    }
                else
                    {
                    Char lastChar = modifierStr[modLen - 1];
                    if (lastChar == '*' || (modLen > 1 && lastChar == '?' && (modifierStr[modLen - 2] == '*' || modifierStr[modLen - 2] == '+')))
                        {
                        operatorQuantifier = "?";
                        }
                    if (operator == '/' || operator == '.')
                        {
                        variableRegEx = "(" + ((firstChar == '^') ? modifierStr.substring(1) : modifierStr) + ")";
                        }
                    else
                        {
                        operatorPrefix = "(";
                        variableRegEx = ((firstChar == '^') ? modifierStr.substring(1) : modifierStr) + ")";
                        }
                    variableQuantifier = "";
                    }
                }

            StringBuffer pattern          = templateParser.pattern? : assert;
            Boolean      operatorAppended = False;

            switch (operator)
                {
                case '.':
                case '/':
                    pattern.append("(")
                           .append(operatorPrefix)
                           .append("\\")
                           .append(operator)
                           .append(operatorQuantifier);
                    operatorAppended = True;
                    continue;
                case '+':
                case '0': // no active operator
                    if (!operatorAppended)
                        {
                        pattern.append("(").append(operatorPrefix);
                        }
                    pattern.append(variableRegEx)
                           .append(variableQuantifier)
                           .append(")");
                    break;
                default:
                    // no-op
                    break;
                }

            if (operator == '/' || modifierStr.equals("?"))
                {
                pattern.append("?");
                }

            // TODO JK: "pattern" is not used
            super(segments, variable, prefix, delimiter, encode, repeatPrefix, modifierStr,
                    modifierChar, operator, previousDelimiter, isQuerySegment);
            }

        /**
         * @param variable The variable
         * @param operator The operator
         * @return The variable match pattern
         */
        String getVariableRegEx(String variable, Char operator) {
            if (operator == '+')
                {
                // Allow reserved characters. See https://tools.ietf.org/html/rfc6570#section-3.2.3
                return "([\\S]";
                }
            else
                {
                 // the reg-ex used to match variables in a uri.
                return "([^\\/\\?#&;\\+]";
                }
            }
        }

    // ----- inner class: DefaultUriMatchInfo ------------------------------------------------------

    /**
     * The default UriMatchInfo implementation.
     */
    static const DefaultUriMatchInfo
            implements UriMatchInfo
            implements Orderable
        {
        /**
         * Create a DefaultUriMatchInfo.
         *
         * @param uri            The URI
         * @param variableValues The map of variable names with values
         * @param variables      The variables
         */
        construct(String uri, Map<String, Object> variableValues, UriMatchVariable[] variables)
            {
            this.uri            = uri;
            this.variableValues = variableValues;
            this.variables      = variables;
            this.variableMap    = new ListMap();
            for (UriMatchVariable variable : variables)
                {
                variableMap.put(variable.name, variable);
                }
            }

        // ----- properties ------------------------------------------------------------------------

        @Override
        public/private String uri;

        @Override
        public/private Map<String, Object> variableValues;

        @Override
        public/private UriMatchVariable[] variables;

        @Override
        public/private Map<String, UriMatchVariable> variableMap;

        // ----- Stringable methods ----------------------------------------------------------------

        @Override
        public Int estimateStringLength()
            {
            return uri.size;
            }

        @Override
        public Appender<Char> appendTo(Appender<Char> buf)
            {
            return uri.appendTo(buf);
            }

        // ----- Comparable ------------------------------------------------------------------------

        @Override
        static <CompileType extends UriMatchInfo> Boolean equals(CompileType value1, CompileType value2)
            {
            return value1.uri == value2.uri && value1.variables == value2.variables;
            }
        }
    }