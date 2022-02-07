/**
 * A representation of a Uniform Resource Identifier (URI) reference.
 * TODO GG: move to "net" module
 */
const URI(String? scheme,
          String? authority,
          String? userInfo,
          String? host,
          Int     port = -1,
          String? path,
          String? query,
          String? fragment,
          String? schemeSpecificPart)
        implements Hashable
        delegates Stringable (stringValue)
    {
    construct (String? scheme, String? schemeSpecificPart, String? fragment)
        {
        construct URI(scheme, Null, Null, Null, -1, Null, Null, fragment, schemeSpecificPart);
        }

    /**
     * @param   scheme     Scheme name
     * @param   authority  Authority
     * @param   path       Path
     * @param   query      Query
     * @param   fragment   Fragment
     */
    construct (String? scheme, String? authority, String? path, String? query, String? fragment)
        {
        construct URI(scheme, authority, Null, Null, -1, path, query, fragment, Null);
        }

    // ----- Properties ----------------------------------------------------------------------------

    /**
     * True if this URI is opaque.
     *
     * A URI is opaque iff it is absolute and its scheme-specific part
     * does not begin with a slash Character ('/').
     * An opaque URI has a scheme, a scheme-specific part, and possibly
     * a fragment; all other components are undefined.
     *
     * @return True iff this URI is opaque
     */
    Boolean opaque.get()
        {
        return path == Null;
        }

    @Lazy String stringValue.calc()
        {
        // REVIEW JK: "buf" would be a better name :)
        StringBuffer sb = new StringBuffer();

        String? scheme = this.scheme;
        if (scheme != Null)
            {
            sb.append(scheme);
            sb.append(':');
            }
        if (this.opaque)
            {
            sb.append(this.schemeSpecificPart);
            }
        else
            {
            String? host = this.host;
            if (host != Null)
                {
                sb.append("//");
                if (this.userInfo != Null)
                    {
                    sb.append(this.userInfo);
                    sb.append('@');
                    }
                Boolean needBrackets = host.size > 0 && host.indexOf(':') &&
                                       host[0] != '[' && host[host.size - 1] != ']';
                if (needBrackets)
                    {
                    sb.append('[');
                    }
                sb.append(host);
                if (needBrackets)
                    {
                    sb.append(']');
                    }
                if (this.port != -1)
                    {
                    sb.append(':');
                    sb.append(this.port);
                    }
            }
            else if (this.authority != Null)
                {
                sb.append("//");
                sb.append(this.authority);
                }

            sb.append(this.path?);

            if (this.query != Null)
                {
                sb.append('?');
                sb.append(this.query);
                }
            }
        String? fragment = this.fragment;
        if (fragment != Null)
            {
            sb.append('#');
            sb.append(fragment);
            }
        return sb.toString();
        }

    static URI create(String uri)
        {
        return new Parser(uri).parseURI();
        }

    // ----- Orderable & Hashable funky Interface implementations ----------------------------------

    static <CompileType extends URI> Int hashCode(CompileType value)
        {
        TODO
        }

    static <CompileType extends URI> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.scheme             == value2.scheme
            && value1.authority          == value2.authority
            && value1.userInfo           == value2.userInfo
            && value1.host               == value2.host
            && value1.port               == value2.port
            && value1.path               == value2.path
            && value1.query              == value2.query
            && value1.fragment           == value2.fragment
            && value1.schemeSpecificPart == value2.schemeSpecificPart;
        }

    // ----- Parser --------------------------------------------------------------------------------

    /**
     * A parser to parse a String into a URI.
     */
    static class Parser
        {
        construct (String input)
            {
            this.input = input;
            this.size  = input.size;
            }

        private String      input;
        private Int         size;
        private Int         index     = -1;
        private Boolean     parsed    = False;
        private String?     scheme    = Null;
        private String?     authority = Null;
        private String?     userInfo  = Null;
        private String?     host      = Null;
        private IntLiteral? port      = Null;
        private String?     path      = Null;
        private String?     query     = Null;
        private String?     fragment  = Null;
        private String?     ssp       = Null;
        private Boolean     opaque    = False;

        URI parseURI()
            {
            parse();
            Int p = port == Null ? -1 : port.as(IntLiteral).toInt64();
            return new URI(this.scheme, this.authority, this.userInfo, this.host, p, this.path,
                           this.query, this.fragment, this.ssp);
            }

        private Char next()
            {
            assert index < size - 1;
            return input[++index];
            }

        private Char peek()
            {
            assert index < size - 1;
            return input[index + 1];
            }

        private Char current()
            {
            assert index >= 0;
            return input[index];
            }

        private Boolean hasNext()
            {
            return index < size - 1;
            }

        private void setPosition(Int pos)
            {
            index = pos;
            }

        private void parse()
            {
            if (parsed)
                {
                return;
                }

            parsed = True;

            if (!hasNext())
                {
                // empty input so set both SSP and path to ""
                this.path = "";
                this.ssp = "";
                return;
                }

            next();
            String? comp = parseComponent(":/?#", True);

            if (hasNext())
                {
                this.ssp = input[index+1 .. size);
                }

            this.opaque = False;
            if (current() == ':')
                {
                // absolute
                if (comp == Null)
                    {
                    throw new IllegalArgument($"Expected scheme name at index {index}: '{input}'.");
                    }
                this.scheme = comp;
                if (!hasNext())
                    {
                    this.path = "";
                    this.ssp  = "";
                    return;
                    }
                Char c = next();
                if (c == '/')
                    {
                    // hierarchical
                    parseHierarchicalUri();
                    }
                else
                    {
                    // opaque
                    this.opaque = True;
                    }
                }
            else
                {
                setPosition(0);
                // relative
                if (current() == '/')
                    {
                    parseHierarchicalUri();
                    }
                else
                    {
                    parsePath();
                    }
                }

            String? fragment = this.fragment;
            String? ssp      = this.ssp;
            if (fragment.is(String) && ssp.is(String))
                {
                // there is a fragment so remove it from the SSP
                this.ssp = ssp[0 .. ssp.size - fragment.size - 2];
                }
            }

        /**
         * Parses the URI component. Parsing starts at position of the first character of
         * component and ends with position of one of the delimiters. The string and current
         * position is taken from the Iterator field.
         *
         * @param delimiters String with delimiters which terminates the component.
         * @param mayEnd     True if component might be the last part of the URI.
         * @param isIp       True if the component might contain IPv6 address.
         *
         * @return the extracted component.
         */
        private String? parseComponent(String? delimiters, Boolean mayEnd, Boolean isIp = False)
            {
            Int          curlyBracketsCount  = 0;
            Int          squareBracketsCount = 0;
            StringBuffer sb                  = new StringBuffer();
            Boolean      endOfInput          = False;
            Char         c                   = current();

            while (!endOfInput) {
                // REVIEW JK: looks like a perfect "switch" candidate
                if (c == '{')
                    {
                    curlyBracketsCount++;
                    sb.append(c);
                    }
                else if (c == '}')
                    {
                    curlyBracketsCount--;
                    sb.append(c);
                    }
                else if (isIp && c == '[')
                    {
                    squareBracketsCount++;
                    sb.append(c);
                    }
                else if (isIp && c == ']')
                    {
                    squareBracketsCount--;
                    sb.append(c);
                    }
                else if ((!isIp || squareBracketsCount == 0) && (curlyBracketsCount == 0)
                           && (delimiters != Null && delimiters.as(String).indexOf(c)))
                    {
                    return sb.size == 0 ? Null : sb.toString();
                    }
                else
                    {
                    sb.append(c);
                    }
                endOfInput = !hasNext();
                if (!endOfInput)
                    {
                    c = next();
                    }
                }

            if (mayEnd)
                {
                return sb.size == 0 ? Null : sb.toString();
                }
            throw new IllegalArgument($"Component does not end by a delimiter '{delimiters}' at index {this.index}.");
            }

        private void parseHierarchicalUri()
            {
            if (hasNext() && peek() == '/')
                {
                // authority
                next();
                next();
                parseAuthority();
                }

            if (!hasNext())
                {
                if (current() == '/')
                    {
                    path = "/";
                    }
                return;
                }
            parsePath();
            }

        private void parseAuthority()
            {
            Int start = index;
            String? comp = parseComponent("@/?#", True, True);
            if (current() == '@')
                {
                this.userInfo = comp;
                if (!hasNext())
                    {
                    return;
                    }
                next();
                comp = parseComponent(":/?#", True, True);
                }
            else
                {
                setPosition(start);
                comp = parseComponent("@:/?#", True, True);
                }

            this.host = comp;

            if (current() == ':')
                {
                if (!hasNext())
                    {
                    return;
                    }
                next();
                String? s = parseComponent("/?#", True);
                if (s.is(String))
                    {
                    this.port = new IntLiteral(s);
                    }
                }

            if (index - start > 1)
                {
                this.authority = input[start..index];
                }
            else
                {
                this.authority = Null;
                }
            }

        private void parsePath()
            {
            this.path = parseComponent("?#", True);

            if (current() == '?')
                {
                if (!hasNext())
                    {
                    return;
                    }
                next(); // skip the ?

                this.query = parseComponent("#", True);
                }

            if (current() == '#')
                {
                if (!hasNext())
                    {
                    return;
                    }
                next();
                this.fragment = input[index..size);
                }
            }
        }
    }
