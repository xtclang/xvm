/**
 * A representation of a Uniform Resource Identifier (URI) reference.
 *
 * @see: https://www.ietf.org/rfc/rfc2396.txt
 */
const Uri
        implements Destringable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Uri from a String.
     *
     * @param text  the URI
     */
    @Override
    construct(String text)
        {
        (Boolean    success,
         String?    scheme,
         String?    authority,
         String?    user,
         String?    host,
         IPAddress? ip,
         UInt16?    port,
         String?    path,
         String?    query,
         String?    opaque,
         String?    fragment,
         String?    error) = parse(text);

        assert:arg success as error ?: $"Illegal URI: {text.quoted()}";

        construct Uri(text, scheme, authority, user, host, ip, port, path, query, opaque, fragment);
        }

    /**
     * Construct a Uri from its parts.
     *
     * If any of `user`, `host`, `ip`, or `port` are passed, then `authority` should be passed as
     * `Null`. Similarly, either optionally pass the `host` or the `ip`, but not both.
     *
     * @param scheme     the scheme name, or Null if none
     * @param authority  the entire authority string, which can be empty, or Null if none
     * @param user       the user name, or Null if none
     * @param host       the host string, or Null if none
     * @param ip         the IP address, otherwise Null
     * @param port       the port number, or Null if none
     * @param path       the '/' path portion, or Null if none
     * @param query      the '?' query portion, or Null if none
     * @param opaque     the opaque portion, if the URI is not of the hierarchical form
     * @param fragment   the '#' fragment portion, which may be blank, or Null if none
     */
    construct(String?    scheme    = Null,
              String?    authority = Null,
              String?    user      = Null,
              String?    host      = Null,
              IPAddress? ip        = Null,
              UInt16?    port      = Null,
              String?    path      = Null,
              String?    query     = Null,
              String?    opaque    = Null,
              String?    fragment  = Null,
             )
        {
        // scheme can be Null, but cannot be blank or contain illegal characters
        assert:arg scheme?.size > 0 as "scheme cannot be blank";
        assert:arg scheme?.chars.all(schemeValid)
                as $"scheme contains illegal character(s): {scheme.quoted()}";

        // authority includes user, host, ip, and port; these all go together. authority and user
        // can be blank, but host cannot be
        assert:arg host?.size > 0 as "host cannot be blank";
        if (authority == Null)
            {
            if (host != Null || ip != Null)
                {
                if (host == Null)
                    {
                    // populate the host from the ip
                    host = ip.toString();
                    }
                else if (Byte[] bytes := IPAddress.parse(host))
                    {
                    if (ip == Null)
                        {
                        // populate the ip from the host
                        ip = new IPAddress(bytes);
                        }
                    else
                        {
                        assert:arg bytes == ip.bytes as $"IP Address ({ip}) does not match host ({host})";
                        }
                    }
                else
                    {
                    assert:arg ip == Null as $|IP Address ({ip}) cannot be specified because the\
                                              | host ({host}) is not a valid IP Address
                                             ;
                    }

                // build the authority string
                authority = renderAuthority(new StringBuffer(), user, host, ip, port).toString();
                }
            else
                {
                // user and port are only allowed if there is a host or ip
                assert:arg user == Null as "user cannot be specified without a host or ip";
                assert:arg port == Null as "port cannot be specified without a host or ip";
                }
            }
        // attempt to parse the authority into its parts
        else if ((String? authUser, String? authHost, IPAddress? authIp, UInt16? authPort,
                String? error) := parseAuthority(authority))
            {
            // verify that passed user, host, ip, port are either Null or match the authority
            if (user == Null)
                {
                user = authUser;
                }
            else
                {
                assert:arg user == authUser as $|The user from the authority {authority.quoted()}\
                                                | does not match the passed user {user.quoted()}
                                               ;
                }

            if (host == Null)
                {
                host = authHost;
                }
            else
                {
                assert:arg host == authHost as $|The host from the authority {authority.quoted()}\
                                                | does not match the passed host {host.quoted()}
                                               ;
                }

            if (ip == Null)
                {
                ip = authIp;
                }
            else
                {
                assert:arg ip == authIp as $|The ip from the authority {authority.quoted()}\
                                            | does not match the passed ip {ip}
                                           ;
                }

            if (port == Null)
                {
                port = authPort;
                }
            else
                {
                assert:arg port == authPort as $|The port from the authority {authority.quoted()}\
                                                | does not match the passed port {port}
                                               ;
                }

            if (host != Null || ip != Null)
                {
                // re-build the authority string from its constituent pieces
                authority = renderAuthority(new StringBuffer(), user, host, ip, port).toString();
                }
            }
        else
            {
            assert:arg user == Null && host == Null && ip == Null && port == Null
                    as $"Authority ({authority}) is not parsable, so user, host, ip, and port must be Null";
            }

        // opaque is only allowed in lieu of
        if (opaque != Null)
            {
            assert:arg authority == Null as "an opaque value cannot be specified with an authority";
            assert:arg user      == Null as "an opaque value cannot be specified with an user";
            assert:arg host      == Null as "an opaque value cannot be specified with an host";
            assert:arg ip        == Null as "an opaque value cannot be specified with an ip";
            assert:arg port      == Null as "an opaque value cannot be specified with an port";
            assert:arg path      == Null as "an opaque value cannot be specified with an path";
            assert:arg query     == Null as "an opaque value cannot be specified with an query";
            }

        // scheme requires at least one of: authority, path, or opaque
        assert:arg scheme == Null || (authority != Null || path != Null || opaque != Null)
                as "a scheme requires at least one of: authority, path, or opaque";

        // some information is required or the URI is invalid (not everything can be Null)
        assert:arg scheme != Null || authority != Null || path != Null || fragment != Null
                as "the URI requires at least one of: scheme, authority, path, or fragment";

        construct Uri(Null, scheme, authority, user, host, ip, port, path, query, opaque, fragment);
        }

    /**
     * Construct a Uri from its parts.
     *
     * If any of `user`, `host`, `ip`, or `port` are passed, then `authority` should be passed as
     * `Null`. Similarly, either optionally pass the `host` or the `ip`, but not both.
     *
     * @param originalForm  the original URI string, or Null if none
     * @param scheme        the scheme name, or Null if none
     * @param authority     the entire authority string, which can be empty, or Null if none
     * @param user          the user name, or Null if none
     * @param host          the host string, or Null if none
     * @param ip            the IP address, otherwise Null
     * @param port          the port number, or Null if none
     * @param path          the '/' path portion, or Null if none
     * @param query         the '?' query portion, or Null if none
     * @param opaque        the opaque portion, if the URI is not of the hierarchical form
     * @param fragment      the '#' fragment portion, which may be blank, or Null if none
     */
    private construct(String?    originalForm,
                      String?    scheme,
                      String?    authority,
                      String?    user,
                      String?    host,
                      IPAddress? ip,
                      UInt16?    port,
                      String?    path,
                      String?    query,
                      String?    opaque,
                      String?    fragment,
                     )
        {
        this.originalForm = originalForm;
        this.scheme       = scheme;
        this.authority    = authority;
        this.user         = user;
        this.host         = host;
        this.ip           = ip;
        this.port         = port;
        this.path         = path;
        this.query        = query;
        this.opaque       = opaque;
        this.fragment     = fragment;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The optional scheme.
     */
    String? scheme;
    /**
     * The optional authority.
     */
    String? authority;
    /**
     * The optional user, which must match the user portion of the authority.
     */
    String? user;
    /**
     * The optional host, which must match the host portion of the authority.
     */
    String? host;
    /**
     * The optional ip, which must match the host, and also the host portion of the authority.
     */
    IPAddress? ip;
    /**
     * The optional port number, which must match the port portion of the authority.
     */
    UInt16? port;
    /**
     * The optional path.
     */
    String? path;
    /**
     * The optional query, which is the part that follows '?'.
     */
    String? query;
    /**
     * The optional opaque portion of the URI.
     */
    String? opaque;
    /**
     * The optional URI fragment, which is the part that follows '#'.
     */
    String? fragment;

    /**
     * The String used to construct the Uri, if any, which may differ from the String that the Uri
     * would produce if it were requested to do so.
     */
    String? originalForm;


    // ----- searching -----------------------------------------------------------------------------

    /**
     * The Uri is divided into sections.
     */
    enum Section
        {
        Scheme, Authority, Path, Query, Fragment;

        @Lazy Position start.calc()
            {
            return new Position(this, 0);
            }
        }

    /**
     * Describes a position within the Uri. This data structure is directly related to the manner in
     * which this Uri implementation stores its internal data, which may differ from how the Uri is
     * rendered canonically.
     */
    static const Position(Section section, Int offset)
        {
        Position with(Section? section=Null, Int? offset=Null)
            {
            return new Position(section ?: this.section, offset ?: this.offset);
            }

        /**
         * A pre-defined Position for the beginning of a Uri.
         */
        static Position Start = new Position(Scheme, 0);
        /**
         * A pre-defined Position for the beginning of the Scheme.
         */
        static Position StartScheme = Start;
        /**
         * A pre-defined Position for the beginning of the Authority.
         */
        static Position StartAuthority = new Position(Authority, 0);
        /**
         * A pre-defined Position for the beginning of the Path.
         */
        static Position StartPath = new Position(Path, 0);
        /**
         * A pre-defined Position for the beginning of the Query.
         */
        static Position StartQuery = new Position(Query, 0);
        /**
         * A pre-defined Position for the beginning of the Fragment.
         */
        static Position StartFragment = new Position(Fragment, 0);
        }

    /**
     * Calculate the text of the specified section.
     *
     * @param section  the section to determine the textual content of
     *
     * @return the textual content of the section
     */
    String sectionText(Section section)
        {
        @Lazy(() -> authority == Null ? "" : $"//{authority}") String cachedAuthority;
        @Lazy(() -> query     == Null ? "" : $"?{query}")      String cachedQuery;
        @Lazy(() -> fragment  == Null ? "" : $"#{fragment}")   String cachedFragment;

        return switch (section)
            {
            case Scheme:    scheme ?: "";
            case Authority: cachedAuthority;
            case Path:      path ?: "";
            case Query:     cachedQuery;
            case Fragment:  cachedFragment;
            };
        }

    /**
     * Calculate the length of the specified section.
     *
     * @param section  the section to determine the length of
     *
     * @return the length of the section
     */
    Int sectionLength(Section section)
        {
        return switch (section)
            {
            case Scheme:    scheme?.size        : 0;
            case Authority: authority?.size + 2 : 0;
            case Path:      path?.size          : 0;
            case Query:     query?.size + 1     : 0;
            case Fragment:  fragment?.size + 1  : 0;
            };
        }

    /**
     * The "at the start" (inclusive) position for this Uri.
     */
    @RO Position beginPosition.get()
        {
        return scheme    != Null ? Section.Scheme   .start
             : opaque    != Null ? assert
             : authority != Null ? Section.Authority.start
             : path      != Null ? Section.Path     .start
             : query     != Null ? Section.Query    .start
             : fragment  != Null ? Section.Fragment .start
             : assert;
        }

    /**
     * The "after the end" (exclusive) position for this Uri.
     */
    @RO Position endPosition.get()
        {
        return fragment  != Null ? new Position(Fragment , fragment       .size)
             : opaque    != Null ? assert
             : query     != Null ? new Position(Query    , query          .size)
             : path      != Null ? new Position(Path     , path.toString().size)
             : authority != Null ? new Position(Authority, authority      .size)
             : scheme    != Null ? new Position(Scheme   , scheme         .size)
             : assert;
        }

    /**
     * Regardless of what section the passed position is in, test to see if that position
     * corresponds to a position within the specified section.
     *
     * Specifically, if the passed position is immediately after the last character of a section,
     * and that section precedes the desired section, then the position is actually the same as the
     * position of the first character of the following section. Similarly, the desired section may
     * be separated from the specified position by empty sections, which can be ignored.
     *
     * @param position  the position to evaluate
     * @param section   the section that the caller desires the position to be within
     *
     * @return True iff the passed position is within the specified section
     * @return (conditional) the position object to use instead of the passed position object
     */
    conditional Position positionAt(Position position, Section section)
        {
        switch (position.section <=> section)
            {
            case Lesser:
                Int offset = position.offset;
                for (Section current : position.section ..< section)
                    {
                    if (offset == sectionLength(current))
                        {
                        // we were at the end of the previous section, so the offset in the next
                        // section will be zero
                        offset = 0;
                        }
                    else
                        {
                        return False;
                        }
                    }
                return True, section.start;

            case Equal:
                return True, position;

            case Greater:
                Int offset = position.offset;
                for (Section current : position.section ..< section)
                    {
                    if (offset == 0)
                        {
                        offset = sectionLength(current.prevValue());
                        }
                    else
                        {
                        return False;
                        }
                    }
                return True, new Position(section, offset);
            }
        }

    /**
     * Attempt to find the specified literal in this Uri.
     *
     * @param literal  the literal to search for
     * @param from     (optional) the position (inclusive) to begin searching from
     * @param to       (optional) the position (exclusive) to not search at or beyond
     *
     * @return `True` iff the literal is found
     * @return (conditional) the position within the Uri that the literal is located
     * @return (conditional) the position within the Uri that immediately follows the literal
     */
    conditional (Position found, Position next) find(String literal, Position? from=Null, Position? to=Null)
        {
        // start at the beginning if no "start from" specified
        from ?:= Start;

        Int literalLength = literal.size;
        if (literalLength == 0)
            {
            return True, from, from;
            }

        Char start = literal[0];

        Section section = from.section;
        Int     offset  = from.offset;
        String  part    = sectionText(section);
        while (True)
            {
            // check if we need to stop looking for the literal before reaching the end of the part
            Int partLength = part.size;
            if (section == to?.section)
                {
                partLength = partLength.minOf(to.offset - literalLength);
                }

            // scan for the first character of the literal
            while (offset < partLength)
                {
                if (Int nextOffset := matchCharacter(start, part, offset))
                    {
                    if (Position afterLiteral := matchRemainder(
                            literal, 1, literalLength, section, nextOffset, sectionText))
                        {
                        // we found it, but before returning, make sure that we did not pass the
                        // end of the region that we were allowed to match within
                        if (afterLiteral > to?)
                            {
                            return False;
                            }

                        return True, new Position(section, offset), afterLiteral;
                        }

                    offset = nextOffset;
                    }
                else
                    {
                    ++offset;
                    }
                }

            // check if we have reached the end of the area to match within
            if (section >= to?.section)
                {
                return False;
                }

            // load the next section
            if (val temp := section.next(), section := temp.is(Section))
                {
                part   = sectionText(section);
                offset = 0;
                }
            else
                {
                // "We're all out of roofs!" (The Tick vs. The Idea Men)
                return False;
                }
            }
        }

    /**
     * Attempt to match the specified literal at the specified position within this Uri.
     *
     * @param literal  the literal to match
     * @param from     the exactly position that the literal must be located at
     *
     * @return `True` iff the literal is matched
     * @return (conditional) the position within the Uri that immediately follows the literal
     */
    conditional (Position next) matches(String literal, Position from)
        {
        Int literalLength = literal.size;
        if (literalLength == 0)
            {
            return True, from;
            }

        return matchRemainder(literal, 0, literalLength, from.section, from.offset, sectionText);
        }

    /**
     * Match a single character.
     *
     * @param ch      the single character to match, which may be a UCS character
     * @param part    the String to match the character within, which may contain `pct-encoded` data
     * @param offset  the offset within the String at which to begin the matching
     *
     * @return `True` iff the character matches
     * @return (conditional) the offset of the character following the matched character
     */
    protected static conditional (Int offset) matchCharacter(Char ch, String part, Int offset)
        {
        if (ch == part[offset])
            {
            return True, offset+1;
            }

        // it's possible that the character needs to be UTF-8 encoded, or the Uri is pct-encoded
        if (!ch.ascii || part[offset] == '%')
            {
            // get the UTF8 bytes for the character and then try every possible encoding
            Int length = part.size;
            for (Byte byte : ch.utf8())
                {
                if (offset >= length)
                    {
                    return False;
                    }

                if (byte.toChar() == part[offset])
                    {
                    ++offset;
                    }
                else if (part[offset] == '%' && offset+2 < length
                        && part[offset+1] == byte.highNibble.toChar()
                        && part[offset+2] == byte.lowNibble.toChar())
                    {
                    offset += 3;
                    }
                else
                    {
                    return False;
                    }
                }
            return True, offset;
            }

        return False;
        }

    /**
     * Match the remaining portion of a literal string value.
     *
     * @param literal        the literal string value
     * @param literalOffset  the offset within the literal string value continue matching from
     * @param literalLength  the effective length of the literal string value (if the entire value
     *                       is not being matched)
     * @param section        the current section of the Uri being matched
     * @param offset         the offset into the current section of the Uri that must match the next
     *                       character ofo the literal string value
     * @param partFor        the function to use to load subsequent sections of the Uri
     */
    protected static conditional (Position next) matchRemainder(
            String                   literal,
            Int                      literalOffset,
            Int                      literalLength,
            Section                  section,
            Int                      offset,
            function String(Section) partFor,
           )
        {
        if (literalOffset >= literalLength)
            {
            return True, new Position(section, offset);
            }

        while (True)
            {
            String part       = partFor(section);
            Int    partLength = part.size;
            if (offset < partLength)
                {
                while (True)
                    {
                    if (literalOffset >= literalLength)
                        {
                        return True, new Position(section, offset);
                        }

                    if (offset >= partLength)
                        {
                        break;
                        }

                    if (!(offset := matchCharacter(literal[literalOffset++], part, offset)))
                        {
                        return False;
                        }
                    }
                }

            // load the next section
            if (val temp := section.next(), section := temp.is(Section))
                {
                part   = partFor(section);
                offset = 0;
                }
            else
                {
                return False;
                }
            }
        }


    // ----- formatting ----------------------------------------------------------------------------

    /**
     * The URI in the String format selected by this implementation.
     */
    @Lazy
    String canonicalForm.calc()
        {
        StringBuffer buf = new StringBuffer();
        if (String scheme ?= scheme)
            {
            escape(scheme, schemeValid).appendTo(buf);
            buf.add(':');
            }

        if (String opaque ?= opaque)
            {
            escape(opaque, uricValid).appendTo(buf);
            }
        else
            {
            if (authority != Null)
                {
                "//".appendTo(buf);
                authority.appendTo(buf);
                }

            if (path != Null)
                {
                // TODO escape
                path.appendTo(buf);
                }

            if (String query ?= query)
                {
                buf.add('?');
                escape(query, uricValid).appendTo(buf);
                }
            }

        if (String fragment ?= fragment)
            {
            buf.add('#');
            escape(fragment, uricValid).appendTo(buf);
            }

        return buf.toString();
        }

    /**
     * Obtain a portion of the URI's canonical form starting from the specified [Position] and
     * ending immediately before the `next` [Position].
     *
     * @param start  the position to start the slice from (inclusive)
     * @param next   (optional) the position to end the slice at (exclusive)
     *
     * @return the specified slice of the URI in the URI's canonical format
     */
    String canonicalSlice(Position start, Position? next=Null)
        {
        static Position WayBeyondTheEnd = new Position(Fragment, 1TB);
        next ?:= WayBeyondTheEnd;

        Section startSection = start.section;
        Int     startOffset  = start.offset;
        Section nextSection  = next.section;
        Int     nextOffset   = next.offset;
        if (startSection > nextSection || startSection == nextSection && startOffset >= nextOffset)
            {
            return "";
            }

        StringBuffer buf = new StringBuffer();

        switch (startSection)
            {
            case Scheme:
                Boolean last = nextSection == Scheme;
                if (String scheme ?= scheme)
                    {
                    Boolean truncate = last && nextOffset < scheme.size;
                    scheme = truncate ? scheme[startOffset ..< nextOffset]
                                      : scheme.substring(startOffset);

                    escape(scheme, schemeValid).appendTo(buf);
                    if (!truncate)
                        {
                        buf.add(':');
                        }
                    }

                if (last || nextSection == Authority && nextOffset == 0)
                    {
                    break;
                    }

                startOffset = 0;
                continue;

            case Authority:
                assert opaque == Null;
                Boolean last = nextSection == Authority;
                if (String authority ?= authority)
                    {
                    Boolean truncate = last && nextOffset < authority.size;
                    authority = truncate ? authority[startOffset ..< nextOffset]
                                         : authority.substring(startOffset);

                    if (startOffset == 0)
                        {
                        "//".appendTo(buf);
                        }
                    authority.appendTo(buf);
                    }

                if (last || nextSection == Path && nextOffset == 0)
                    {
                    break;
                    }

                startOffset = 0;
                continue;

            case Path:
                assert opaque == Null;
                Boolean last = nextSection == Path;
                if (String path ?= path)
                    {
                    String  pathString = path.toString();
                    Boolean truncate   = last && nextOffset < pathString.size;
                    pathString = truncate ? pathString[startOffset ..< nextOffset]
                                          : pathString.substring(startOffset);

                    // TODO escape
                    pathString.appendTo(buf);
                    }

                if (last || nextSection == Query && nextOffset == 0)
                    {
                    break;
                    }

                startOffset = 0;
                continue;

            case Query:
                assert opaque == Null;
                Boolean last = nextSection == Query;
                if (String query ?= query)
                    {
                    Boolean truncate = last && nextOffset < query.size;
                    query = truncate ? query[startOffset ..< nextOffset]
                                     : query.substring(startOffset);

                    if (startOffset == 0)
                        {
                        buf.add('?');
                        }
                    escape(query, uricValid).appendTo(buf);
                    }

                if (last || nextSection == Fragment && nextOffset == 0)
                    {
                    break;
                    }

                startOffset = 0;
                continue;

            case Fragment:
                if (String fragment ?= fragment)
                    {
                    Boolean truncate = nextOffset < fragment.size;
                    fragment = truncate ? fragment[startOffset ..< nextOffset]
                                        : fragment.substring(startOffset);

                    if (startOffset == 0)
                        {
                        buf.add('#');
                        }
                    escape(fragment, uricValid).appendTo(buf);
                    }

                break;
            }

        return buf.toString();
        }

    /**
     * Render the components of the authority into an authority string. This method does not attempt
     * to validate the contents of the components of the authority string.
     *
     * @param user  an optional user name
     * @param host  an optional host name or IP address string
     * @param ip    an optional IPAddress, used in lieu of the host
     * @param port  an optional port number
     */
    static StringBuffer renderAuthority(StringBuffer buf,
                                        String?      user,
                                        String?      host,
                                        IPAddress?   ip,
                                        UInt16?      port)
        {
        if (host != Null || ip != Null)
            {
            if (user != Null)
                {
                escape(user, ch -> regnameValid(ch) && ch != '@').appendTo(buf);
                buf.add('@');
                }

            if (ip != Null)
                {
                if (ip.v6)
                    {
                    // the colons in the IPv6 address get confused with the colon before the port,
                    // so the IPv6 address is wrapped in square brackets
                    buf.add('[');
                    ip.appendTo(buf);
                    buf.add(']');
                    }
                else
                    {
                    ip.appendTo(buf);
                    }
                }
            else
                {
                host.appendTo(buf);
                }

            if (port != Null)
                {
                buf.add(':');
                port.appendTo(buf);
                }
            }

        return buf;
        }

    @Override
    String toString()
        {
        return originalForm ?: canonicalForm;
        }


    // ----- parsing -------------------------------------------------------------------------------

    /**
     * Create a `Uri` from the passed `String`, iff the `String` contains a valid URI.
     *
     * @param text  the text containing a URI
     *
     * @return True if the text was successfully parsed into a Uri
     * @return (conditional) the Uri
     */
    static conditional Uri fromString(String text)
        {
        if ((String?    scheme,
             String?    authority,
             String?    user,
             String?    host,
             IPAddress? ip,
             UInt16?    port,
             String?    path,
             String?    query,
             String?    opaque,
             String?    fragment) := parse(text))
            {
            return True, new Uri(text, scheme, authority, user, host, ip, port, path, query, opaque, fragment);
            }

        return False;
        }

    /**
     * Parse URI information from a String, without relying on an exception to report failure.
     *
     * @param text  the String containing the URI
     *
     * @return success    True iff the parsing succeeded and the URI is lexically valid
     * @return scheme     the scheme name, or Null if none
     * @return user       the user name, or Null if none
     * @return authority  the entire authority string, which can be empty, or Null if none
     * @return host       the host string (name or IP v4/v6 address), or Null if none
     * @return ip         the parsed IP address iff the host is an IP address, otherwise Null
     * @return port       the port number, or Null if none
     * @return path       the '/' path portion, or Null if none
     * @return query      the '?' query portion, or Null if none
     * @return opaque     the opaque portion, if the URI is not of the hierarchical form
     * @return fragment   the '#' fragment portion, or Null if none
     * @return error      if parsing failed for any reason, this may contain an explanation of the
     *                    parsing error
     */
    static (Boolean     success,
            String?     scheme,
            String?     authority,
            String?     user,
            String?     host,
            IPAddress?  ip,
            UInt16?     port,
            String?     path,
            String?     query,
            String?     opaque,
            String?     fragment,
            String?     error) parse(String text)
        {
        String?    scheme    = Null;
        String?    user      = Null;
        String?    authority = Null;
        String?    host      = Null;
        IPAddress? ip        = Null;
        UInt16?    port      = Null;
        String?    path      = Null;
        String?    query     = Null;
        String?    opaque    = Null;
        String?    fragment  = Null;
        String?    error     = Null;

        Int offset = 0;
        Int length = text.size;

        // an empty URI is not legal
        if (length == 0)
            {
            return False, scheme, authority, user, host, ip, port, path, query, opaque, fragment, "Empty URI";
            }

        // a Uri is either an absoluteURI or a relativeURI:
        //
        //   URI-reference = [ absoluteURI | relativeURI ] [ "#" fragment ]
        //
        // and an absolute URI always starts with a scheme and a colon:
        //
        //   absoluteURI = scheme ":" ( hier_part | opaque_part )
        //   scheme = alpha *( alpha | digit | "+" | "-" | "." )
        //   hier_part = ( net_path | abs_path ) [ "?" query ]
        //   opaque_part = uric_no_slash *uric
        //   uric_no_slash = unreserved | escaped | ";" | "?" | ":" | "@" | "&" | "=" | "+" | "$" | ","
        //
        // and a relative URI starts with either '/' or a rel_segment followed by '/'
        //
        //   relativeURI = ( net_path | abs_path | rel_path ) [ "?" query ]
        //   net_path = "//" authority [ abs_path ]
        //   abs_path = "/"  path_segments
        //   rel_path = rel_segment [ abs_path ]
        //   rel_segment = 1*( unreserved | escaped | ";" | "@" | "&" | "=" | "+" | "$" | "," )
        if ((scheme, offset, error) := parseScheme(text, offset, error))
            {
            if ((authority, user, host, ip, port, path, offset, error) := parseNetPath(text, offset, error))
                {
                (query, offset, error) := parseQuery(text, offset, error);
                (fragment, offset, error) := parseFragment(text, offset, error);
                }
            else if ((path, offset, error) := parseAbsPath(text, offset, error))
                {
                (query, offset, error) := parseQuery(text, offset, error);
                (fragment, offset, error) := parseFragment(text, offset, error);
                }
            else
                {
                (opaque, offset, error) := parseOpaque(text, offset, error);
                (fragment, offset, error) := parseFragment(text, offset, error);
                }
            }
        else if ((authority, user, host, ip, port, path, offset, error) := parseNetPath(text, offset, error))
            {
            (query, offset, error) := parseQuery(text, offset, error);
            (fragment, offset, error) := parseFragment(text, offset, error);
            }
        else if ((path, offset, error) := parseAbsPath(text, offset, error))
            {
            (query, offset, error) := parseQuery(text, offset, error);
            (fragment, offset, error) := parseFragment(text, offset, error);
            }
        else if ((path, offset, error) := parseRelPath(text, offset, error))
            {
            (query, offset, error) := parseQuery(text, offset, error);
            (fragment, offset, error) := parseFragment(text, offset, error);
            }
        else if (!((fragment, offset, error) := parseFragment(text, offset, error)))
            {
            error = $"Not an absolute (scheme-based) nor a relative (path-based) URI: {text.quoted()}";
            }

        // verify that the text was consumed
        if (error == Null && offset < length)
            {
            error = $"Unparsable URI portion: {text[offset ..< length].quoted()}";
            }

        return error==Null, scheme, authority, user, host, ip, port, path, query, opaque, fragment, error;

        /**
         * Internal: Parse a URI "scheme", if there is one.
         */
        static (Boolean found, String? scheme, Int offset, String? error) parseScheme(
                String text, Int offset, String? error)
            {
            if (error != Null)
                {
                return True, Null, offset, error;
                }

            // save some time and some allocations by explicitly testing for the most common schemes
            Int length = text.size;
            switch (text[offset])
                {
                case 'f':
                    if (match(text, offset+1, "ile:"))
                        {
                        return True, "file", offset+5, Null;
                        }
                    else if (match(text, offset+1, "tp:"))
                        {
                        return True, "ftp", offset+4, Null;
                        }
                    break;

                case 'h':
                    if (match(text, offset+1, "ttp"))
                        {
                        if (length > offset+4 && text[offset+4] == ':')
                            {
                            return True, "http", offset+5, Null;
                            }
                        else if (length > offset+5 && text[offset+4] == 's' && text[offset+5] == ':')
                            {
                            return True, "https", offset+6, Null;
                            }
                        }
                    break;

                case 'l':
                    if (match(text, offset+1, "dap"))
                        {
                        if (length > offset+4 && text[offset+4] == ':')
                            {
                            return True, "ldap", offset+5, Null;
                            }
                        else if (length > offset+5 && text[offset+4] == 's' && text[offset+5] == ':')
                            {
                            return True, "ldaps", offset+6, Null;
                            }
                        }
                    break;

                case 'm':
                    if (match(text, offset+1, "ailto:"))
                        {
                        return True, "mailto", offset+7, Null;
                        }
                    break;

                case 'n':
                    if (match(text, offset+1, "ews:"))
                        {
                        return True, "news", offset+5, Null;
                        }
                    else if (match(text, offset+1, "fs:"))
                        {
                        return True, "nfs", offset+4, Null;
                        }
                    break;

                case 's':
                    if (match(text, offset+1, "sh:"))
                        {
                        return True, "ssh", offset+4, Null;
                        }
                    else if (match(text, offset+1, "ftp:"))
                        {
                        return True, "sftp", offset+5, Null;
                        }
                    break;

                case 't':
                    if (match(text, offset+1, "elnet:"))
                        {
                        return True, "telnet", offset+7, Null;
                        }
                    else if (match(text, offset+1, "ftp:"))
                        {
                        return True, "tftp", offset+5, Null;
                        }
                    break;

                case 'A'..'Z':
                case 'a'..'z':
                    // this is a legal first character for a scheme
                    break;

                case '/': // path
                case '#': // fragment
                default:
                    // scheme must start with an "alpha" character
                    return False, Null, offset, Null;
                }

            // attempt to parse a scheme; parse until we hit a ':'
            //
            //      scheme = alpha *( alpha | digit | "+" | "-" | "." )
            //
            // any other character would indicate "not a scheme", for example:
            //
            //      '/' - it is a relativeURI
            //      '#' - it was a relativeURI, next is fragment
            //      '?' - it was a relativeURI, next is query
            //      '%' - an escape, as well as any symbol not '+', '-', or '.', indicates (i.e. is
            //            only legal in) a path
            for (Int i = offset+1; i < length; ++i)
                {
                switch (text[i])
                    {
                    case 'A'..'Z', 'a'..'z':
                    case '0'..'9':
                    case '+', '-', '.':
                        // this is a valid scheme character
                        break;

                    case ':':
                        // this is the separator that terminates the scheme
                        return True, text[offset ..< i], i+1, Null;

                    default:
                        // not a valid scheme character
                        return False, Null, offset, Null;
                    }
                }

            // got to the end of the URI, and no colon was encountered, so it was not a scheme
            return False, Null, offset, Null;
            }

        /**
         * Internal: Parse a URI "net_path", if there is one.
         */
        static (Boolean found, String? authority, String? user, String? host, IPAddress? ip,
                UInt16? port, String? path, Int offset, String? error) parseNetPath(
                String text, Int offset, String? error)
            {
            if (error != Null)
                {
                return True, Null, Null, Null, Null, Null, Null, offset, error;
                }

            // check for the leading "//"
            Int length = text.size;
            if (offset + 1 >= length || text[offset] != '/' || text[offset+1] != '/')
                {
                return False, Null, Null, Null, Null, Null, Null, offset, error;
                }

            // move past the "//"
            offset += 2;

            String?    authority = Null;
            String?    user      = Null;
            String?    host      = Null;
            IPAddress? ip        = Null;
            UInt16?    port      = Null;
            String?    path      = Null;

            Int     atSign       = -1;
            Int     atSigns      = 0;
            Int     leftSquare   = -1;
            Int     leftSquares  = 0;
            Int     rightSquare  = -1;
            Int     rightSquares = 0;
            Int     colon        = -1;
            Int     colons       = 0;
            Boolean escaped      = False;

            // parse up to the '/' path, the '?' query, or the '#' fragment
            Int start = offset;
            for ( ; offset < length; ++offset)
                {
                Char ch = text[offset];
                if (ch == '/' || ch == '?' || ch == '#')
                    {
                    break;
                    }

                switch (ch)
                    {
                    case '@':
                        atSign = offset;
                        ++atSigns;
                        break;

                    case '[':
                        leftSquare = offset;
                        ++leftSquares;
                        break;

                    case ']':
                        rightSquare = offset;
                        ++rightSquares;
                        break;

                    case ':':
                        if (leftSquares == rightSquares)
                            {
                            colon = offset;
                            ++colons;
                            }
                        break;

                    case '%':
                        (_, _, error) = decodeEscape(text, offset, error);
                        escaped = True;
                        break;

                    default:
                        if (!regnameValid(ch))
                            {
                            error ?:= $"Illegal character {ch.quoted()} in the authority portion of {text.quoted()}";
                            }
                        break;
                    }
                }

            authority = text[start ..< offset];

            // test if the authority appears to contain server info
            if (error == Null && atSigns <= 1 && leftSquares <= 1 && rightSquares == leftSquares
                    && (leftSquares == 0 || rightSquare > leftSquare > atSign)
                    && colons <= 1 && (colons == 0 || colon > rightSquare && colon > atSign))
                {
                (user, host, ip, port, error) := parseAuthority(authority, atSign-start, colon-start, error);
                if (error == Null && host != Null)
                    {
                    user = unescape(user?);
                    }
                else
                    {
                    user = Null;
                    host = Null;
                    ip   = Null;
                    port = Null;
                    }
                }

            if ((leftSquares > 0 || rightSquares > 0) && ip == Null && error == Null)
                {
                // the only use for the '[' and ']' characters is to enclose an IPv6 address
                error = $|Square brackets are only permitted in a URI authority string to enclose\
                         | a valid IPv6 address: {authority.quoted()}
                        ;
                }

            if (escaped)
                {
                authority = unescape(authority);
                }

            (_, path, offset, error) = parseAbsPath(text, offset, error);

            return True, authority, user, host, ip, port, path, offset, error;
            }

        /**
         * Internal: Parse a URI "abs_path", if there is one.
         *
         *     abs_path      = "/" path_segments
         *     path_segments = segment *( "/" segment )
         *     segment       = *pchar *( ";" param )
         *     param         = *pchar
         *     pchar         = unreserved | escaped | ":" | "@" | "&" | "=" | "+" | "$" | ","
         */
        static (Boolean found, String? path, Int offset, String? error) parseAbsPath(
                String text, Int offset, String? error)
            {
            if (error != Null)
                {
                return True, Null, offset, error;
                }

            Int length = text.size;
            if (offset >= length || text[offset] != '/')
                {
                return False, Null, offset, error;
                }

            (String path, offset, error) = parsePath(text, offset, error);
            return True, path, offset, error;
            }

        /**
         * Internal: Parse a URI "rel_path", if there is one.
         *
         *     rel_path    = rel_segment [ abs_path ]
         *     rel_segment = 1*( unreserved | escaped | ";" | "@" | "&" | "=" | "+" | "$" | "," )
         */
        static (Boolean found, String? path, Int offset, String? error) parseRelPath(
                String text, Int offset, String? error)
            {
            if (error != Null)
                {
                return True, Null, offset, error;
                }

            // the path is followed by the query, the fragment, or nothing
            Char next = offset < text.size ? text[offset] : '#';
            if (next == '?' || next == '#')
                {
                return False, Null, offset, error;
                }

            (String path, offset, error) = parsePath(text, offset, error);
            return True, path, offset, error;
            }

        /**
         * Internal: Parse a path.
         */
        static (String path, Int offset, String? error) parsePath(String text, Int offset, String? error)
            {
            Int start  = offset;
            Int length = text.size;
            EachChar: for ( ; offset < length; ++offset)
                {
                Char ch = text[offset];
                switch (ch)
                    {
                    case '?':   // query
                    case '#':   // fragment
                        break EachChar;

                    case '/':
                        break;

                    // unreserved:
                    case 'A'..'Z', 'a'..'z':
                    case '0'..'9':
                    case '-', '_', '.', '!', '~', '*', '\'', '(', ')':
                    // other
                    case ':', '@', '&', '=', '+', '$', ',':
                    // param
                    case ';':
                        break;

                    // escaped (requires 2 digits to follow):
                    case '%':
                        (_, _, error) = decodeEscape(text, offset, error);
                        break;

                    default:
                        error ?:= $"Illegal character {ch.quoted()} in the relative path of {text.quoted()}";
                        break;
                    }
                }

            return text[start ..< offset], offset, error;
            }

        /**
         * Internal: Parse a URI "query", if there is one.
         */
        static (Boolean found, String? query, Int offset, String? error) parseQuery(
                String text, Int offset, String? error)
            {
            if (error != Null)
                {
                return True, Null, offset, error;
                }

            Int length = text.size;
            if (offset >= length || text[offset] != '?')
                {
                return False, Null, offset, error;
                }

            // the query is the last thing in the URI except for the '#' fragment
            Int     start   = ++offset;
            for ( ; offset < length; ++offset)
                {
                Char ch = text[offset];
                if (ch == '#')
                    {
                    break;
                    }

                if (error == Null)
                    {
                    if (!uricValid(ch))
                        {
                        error = $"Illegal character {ch.quoted()} in the '?' query of {text.quoted()}";
                        }
                    else if (ch == '%')
                        {
                        (_, _, error) = decodeEscape(text, offset, error);
                        }
                    }
                }

            String query = text[start ..< offset];
            // note that the query cannot be unescaped, because delimiters within the query are
            // unknown in the general URI case; for a particular scheme (like HTTP), the delimiters
            // are known to be '=' and '&', but the URI specification is too open-ended to make any
            // such assumption here
            return True, query, offset, error;
            }

        /**
         * Internal: Parse a URI "opaque_part", if there is one.
         */
        static (Boolean found, String? opaque, Int offset, String? error) parseOpaque(
                String text, Int offset, String? error)
            {
            if (error != Null)
                {
                return True, Null, offset, error;
                }

            Int length = text.size;
            if (offset >= length)
                {
                return False, Null, offset, error;
                }

            // the opaque part is the last thing in the URI except for the '#' fragment
            Int     start   = offset;
            Boolean escaped = False;
            Loop: for ( ; offset < length; ++offset)
                {
                Char ch = text[offset];
                if (ch == '#')
                    {
                    break;
                    }

                if (error == Null)
                    {
                    if (!uricValid(ch))
                        {
                        error = $"Illegal character {ch.quoted()} in the opaque part of {text.quoted()}";
                        }
                    else if (ch == '%')
                        {
                        (_, _, error) = decodeEscape(text, offset, error);
                        }
                    else if (ch == '/' && Loop.first)
                        {
                        error = $"The opaque portion of the URI cannot start with a '/' character: {text.quoted()}";
                        }
                    }
                }

            String opaque = text[start ..< offset];
            if (escaped && error == Null)
                {
                opaque = unescape(opaque);
                }
            return True, opaque, offset, error;
            }

        /**
         * Internal: Parse a URI "fragment", if there is one.
         */
        static (Boolean found, String? fragment, Int offset, String? error) parseFragment(
                String text, Int offset, String? error)
            {
            if (error != Null)
                {
                return True, Null, offset, error;
                }

            Int length = text.size;
            if (offset >= length || text[offset] != '#')
                {
                return False, Null, offset, error;
                }

            // the fragment is the last thing in the URI, so check all the way to the end
            Int     start   = ++offset;
            Boolean escaped = False;
            for ( ; offset < length; ++offset)
                {
                Char ch = text[offset];
                if (error == Null)
                    {
                    if (!uricValid(ch))
                        {
                        error = $"Illegal character {ch.quoted()} in the '#' fragment of {text.quoted()}";
                        }
                    else if (ch == '%')
                        {
                        escaped = True;
                        (_, _, error) = decodeEscape(text, offset, error);
                        }
                    }
                }

            String fragment = text[start ..< offset];
            if (escaped && error == Null)
                {
                fragment = unescape(fragment);
                }
            return True, fragment, offset, error;
            }

        /**
         * Simple lexical exact match helper.
         */
        static Boolean match(String text, Int offset, String match)
            {
            Int count = match.size;
            Int end   = offset + count;
            if (end > text.size)
                {
                return False;
                }

            for (Int i : 0 ..< count)
                {
                if (text[offset+i] != match[i])
                    {
                    return False;
                    }
                }

            return True;
            }
        }

    /**
     * Internal helper for parsing an authority string.
     */
    protected static conditional (String? user, String? host, IPAddress? ip, UInt16? port, String? error)
            parseAuthority(String authority)
        {
        Int     atSign       = -1;
        Int     leftSquare   = -1;
        Int     rightSquare  = -1;
        Int     colon        = -1;

        EachChar: for (Char ch : authority)
            {
            switch (ch)
                {
                case '@':
                    if (atSign >= 0 || leftSquare >= 0 || colon >= 0)
                        {
                        return False;
                        }
                    atSign = EachChar.count;
                    break;

                case '[':
                    if (leftSquare >= 0 || colon >= 0)
                        {
                        return True, Null, Null, Null, Null,
                                $"Authority {authority.quoted()} contains an illegal '[' character";
                        }
                    leftSquare = EachChar.count;
                    break;

                case ']':
                    if (leftSquare < 0 || rightSquare >= 0 || colon >= 0)
                        {
                        return True, Null, Null, Null, Null,
                                $"Authority {authority.quoted()} contains an illegal ']' character";
                        }
                    rightSquare = EachChar.count;
                    break;

                case ':':
                    if (leftSquare >= 0 == rightSquare >= 0)
                        {
                        if (colon >= 0)
                            {
                            return False;
                            }
                        colon = EachChar.count;
                        }
                    break;

                case '%':
                    (_, _, String? error) = decodeEscape(authority, EachChar.count);
                    if (error != Null)
                        {
                        return True, Null, Null, Null, Null, error;
                        }
                    break;

                default:
                    if (!regnameValid(ch))
                        {
                        return True, Null, Null, Null, Null, $"Illegal character in authority: {ch.quoted()}";
                        }
                    break;
                }
            }

        if (leftSquare >= 0 != rightSquare >= 0)
            {
            return True, Null, Null, Null, Null,
                    $"Authority {authority.quoted()} contains an unbalanced '[' character";
            }

        (Boolean found, String? user, String? host, IPAddress? ip, UInt16? port, String? error) =
                parseAuthority(authority, atSign, colon, Null);

        if (leftSquare >= 0 && ip == Null && error == Null)
            {
            // the only use for the '[' and ']' characters is to enclose an IPv6 address
            error = $|Square brackets are only permitted in a URI authority string to enclose\
                     | a valid IPv6 address: {authority.quoted()}
                    ;
            }

        return True, user, host, ip, port, error;
        }

    /**
     * Internal helper for parsing an authority string.
     */
    protected static (Boolean found, String? user, String? host, IPAddress? ip, UInt16? port, String? error)
            parseAuthority(String text, Int atSign, Int colon, String? error)
        {
        if (error != Null)
            {
            return True, Null, Null, Null, Null, error;
            }

        Int length = text.size;
        if (length == 0)
            {
            return True, Null, Null, Null, Null, "Authority string is blank";
            }

        String?    user   = Null;
        String?    host   = Null;
        IPAddress? ip     = Null;
        UInt16?    port   = Null;

        // per rfc2396, user is permitted to be blank (zero characters), and all of the
        // regname characters are valid user characters, so there is nothing else to check
        if (atSign >= 0)
            {
            user = unescape(text[0 ..< atSign]);
            }

        // the host can be an IP address (either IPv4, or IPv6 inside square brackets), or:
        //   hostname = *( domainlabel "." ) toplabel [ "." ]
        //   domainlabel = alphanum | alphanum *( alphanum | "-" ) alphanum
        //   toplabel = alpha | alpha *( alphanum | "-" ) alphanum
        host = text[(atSign < 0 ? 0 : atSign+1) ..< (colon < 0 ? length : colon)];
        if (host.size == 0)
            {
            // host string is required for this to be an authority
            return False, Null, Null, Null, Null, error;
            }

        Char ch = host[0];
        if (ch.asciiDigit() || ch == '[')
            {
            // the host needs to be a legal IP address, or the authority is not a valid host
            // format
            if (Byte[] bytes := IPAddress.parse(host))
                {
                ip = new IPAddress(bytes);
                }
            else
                {
                // we do not treat the parsing failure as an error here; it just means that the
                // authority is a "reg_name" (basically, it's opaque)
                return False, Null, Null, Null, Null, error;
                }
            }
        else
            {
            // validate the host name
            //   host        = hostname | IPv4address
            //   hostname    = *( domainlabel "." ) toplabel [ "." ]
            //   domainlabel = alphanum | alphanum *( alphanum | "-" ) alphanum
            //   toplabel    = alpha | alpha *( alphanum | "-" ) alphanum
            Boolean startedWithAlpha = False;
            Boolean endedWithDash    = False;
            Boolean firstChar        = True;
            for (ch : host)
                {
                switch (ch)
                    {
                    case '.':
                        if (firstChar || endedWithDash)
                            {
                            // can't start with a dot or have two dots in a row
                            return False, Null, Null, Null, Null, error;
                            }
                        firstChar = True;
                        break;

                    case 'A'..'Z', 'a'..'z':
                        if (firstChar)
                            {
                            startedWithAlpha = True;
                            }
                        endedWithDash = False;
                        firstChar     = False;
                        break;

                    case '0'..'9':
                        if (firstChar)
                            {
                            startedWithAlpha = False;
                            }
                        endedWithDash = False;
                        firstChar     = False;
                        break;

                    case '-':
                        if (firstChar)
                            {
                            // can't start with a dash
                            return False, Null, Null, Null, Null, error;
                            }
                        endedWithDash = True;
                        firstChar     = False;
                        break;

                    default:
                        // nothing else (including escapes) are allowed in a host string
                        return False, Null, Null, Null, Null, error;
                    }
                }

            if (endedWithDash || !startedWithAlpha)
                {
                // no segment can end with a dash
                // the last segment must start with an alpha char
                return False, Null, Null, Null, Null, error;
                }
            }

        if (colon >= 0)
            {
            Int offset = colon + 1;
            if (offset >= length)
                {
                // per rfc2396, port is permitted to be blank (zero characters), but with port
                // being an integer type that poses a problem, so require at least 1 digit to
                // break out the authority into its parts
                return False, Null, Null, Null, Null, error;
                }

            port = 0;
            while (offset < length)
                {
                if (Int n := text[offset].asciiDigit())
                    {
                    n += port.toInt64() * 10;
                    if (n > UInt16.MaxValue)
                        {
                        // we could either treat this as an error or pretend that the entire
                        // authority is just opaque
                        return False, Null, Null, Null, Null, error;
                        }
                    port = n.toUInt16();
                    }
                else
                    {
                    // we do not treat the parsing failure as an error here; it just means that
                    // the authority is a "reg_name" (basically, it's opaque)
                    return False, Null, Null, Null, Null, error;
                    }
                }
            }

        return True, user, host, ip, port, error;
        }

    /**
     * A function that tests whether characters are valid in a URI "scheme".
     *
     * @param ch  a character
     *
     * @return True iff the character can be used in the "scheme" portion of a URI
     */
    static Boolean schemeValid(Char ch)
        {
        return switch (ch)
            {
            case 'A'..'Z', 'a'..'z':
            case '0'..'9':
            case '+', '-', '.': True;

            default: False;
            };
        }

    /**
     * A function that tests whether characters are valid URI "reg_name" characters.
     *
     * The caller must validate escape sequences, since this function only examines one character.
     *
     * @param ch  a character
     *
     * @return True iff the character can be used in the "authority" portion of a URI "net_path"
     */
    static Boolean regnameValid(Char ch)
        {
        return switch (ch)
            {
            // unreserved:
            case 'A'..'Z', 'a'..'z':
            case '0'..'9':
            case '-', '_', '.', '!', '~', '*', '\'', '(', ')':
            // escaped (requires 2 digits to follow):
            case '%':
            // other
            case '$', ',', ';', ':', '@', '&', '=', '+': True;

            default: False;
            };
        }

    /**
     * A function that tests whether characters are valid URI "pchar" characters.
     *
     * The caller must validate escape sequences, since this function only examines one character.
     *
     * @param ch  a character
     *
     * @return True iff the character can be used in the "segment" and "param" portions of several
     *         different URI path constructs
     */
    static Boolean pcharValid(Char ch)
        {
        return switch (ch)
            {
            // unreserved:
            case 'A'..'Z', 'a'..'z':
            case '0'..'9':
            case '-', '_', '.', '!', '~', '*', '\'', '(', ')':
            // escaped (requires 2 digits to follow):
            case '%':
            // other
            case ':', '@', '&', '=', '+', '$', ',': True;

            default: False;
            };
        }

    /**
     * A function that tests whether characters are valid URI "uric" characters. The "uric"
     * designation is used in several URI constructs, including "query", "opaque", and "fragment".
     * ("uric" is probably an abbreviation of "URI character".)
     *
     * The caller must validate escape sequences, since this function only examines one character.
     *
     * @param ch  a character
     *
     * @return True iff the character can be used in the "query", "opaque", and "fragment" portions
     *         of a URI
     */
    static Boolean uricValid(Char ch)
        {
        return switch (ch)
            {
            // unreserved:
            case 'A'..'Z', 'a'..'z':
            case '0'..'9':
            // mark:
            case '-', '_', '.', '!', '~', '*', '\'', '(', ')':
            // reserved:
            case ';', '/', '?', ':', '@', '&', '=', '+', '$', ',', '[', ']':
            // escape (requires 2 digits to follow):
            case '%': True;

            default: False;
            };
        }

    /**
     * Validate an escape sequence within a URI string.
     *
     * @param text    the text containing the escape sequence
     * @param offset  the offset of the '%' character that begins the escape sequence
     * @param error   an optional previous error (returned if
     *
     * @return char    the unescaped character, or Null if none
     * @return offset  the offset after the escape sequence
     * @return error   if parsing the escape failed for any reason, this will contain an
     *                 explanation of the parsing error
     */
    static (Char ch, Int offset, String? error) decodeEscape(String text, Int offset, String? error=Null)
        {
        Int length = text.size;
        assert:arg 0 <= offset < length;
        assert:arg text[offset] == '%';

        Int nextOffset = offset + 3;
        if (error != Null || nextOffset > length)
            {
            return '?', nextOffset.minOf(length), error ?: $"The escape sequence is truncated: {text.quoted()}";
            }

        Int codepoint = 0;
        for (Int i : 1..2)
            {
            Char ch = text[offset+i];
            if (Int n := ch.asciiHexit())
                {
                codepoint = codepoint << 4 + n;
                }
            else
                {
                return '?', nextOffset, $"Illegal escape sequence: {text[offset ..< offset+2]}";
                }
            }

        return codepoint.toChar(), nextOffset, Null;
        }

    /**
     * Produce the unescaped form of the passed text, which may contain escape sequences. (For each
     * `%xx` escape sequence, the equivalent ASCII character is used instead.)
     *
     * To avoid exceptions, this method should only be called when the escaped contents of the
     * passed string have already been validated.
     *
     * @param text  a String that may contain `%xx` escape sequences
     *
     * @return the passed String, but with escape sequences replaced with their ASCII equivalents
     */
    static String unescape(String text, function Boolean(Char)? except=Null)
        {
        Int offset = -1;
        Scan: for (Char ch : text)
            {
            if (ch == '%')
                {
                // this is the first character to escape
                offset = Scan.count;
                break;
                }
            }

        if (offset < 0)
            {
            return text;
            }

        StringBuffer buf = new StringBuffer();
        if (offset > 0)
            {
            text[0 ..< offset].appendTo(buf);
            }

        Int length = text.size;
        while (offset < length)
            {
            Char ch = text[offset];
            if (ch == '%')
                {
                (ch, offset) = decodeEscape(text, offset);
                }
            else
                {
                ++offset;
                }

            if (except?(ch))
                {
                // don't unescape this character
                buf.add('%').add(text[offset-2]).add(text[offset-1]);
                }
            else
                {
                buf.add(ch);
                }
            }

        return buf.toString();
        }

    /**
     * Using the passed function to identify characters that do not need to be escaped, produce a
     * String that escapes all other characters in the form `%xx`, with `x` representing a
     * hexadecimal digit ("hexit").
     *
     * @param text   the String to escape
     * @param valid  the function that identifies characters that do **not** require escaping
     *
     * @return the contents of the passed String, but escaped as necessary
     */
    static String escape(String text, function Boolean(Char) valid)
        {
        Int offset = -1;
        Scan: for (Char ch : text)
            {
            if (!valid(ch))
                {
                // this is the first character to escape
                offset = Scan.count;
                break;
                }
            }

        if (offset < 0)
            {
            return text;
            }

        StringBuffer buf = new StringBuffer();
        if (offset > 0)
            {
            text[0 ..< offset].appendTo(buf);
            }

        for (Int length = text.size; offset < length; ++offset)
            {
            Char ch = text[offset];
            if (valid(ch))
                {
                buf.add(ch);
                }
            else
                {
                assert ch.ascii;
                UInt32 n = ch.codepoint;
                buf.add('%');
                buf.add((n >> 4).toHexit());
                buf.add(n.toHexit());
                }
            }

        return buf.toString();
        }


    // ----- Comparable, Orderable & Hashable funky interface implementations ----------------------

    @Override
    static <CompileType extends Uri> Int hashCode(CompileType value)
        {
        return value.hashCache;
        }

    private @Lazy Int hashCache.calc()
        {
        return (scheme?   .hashCode() : 481667)
            ^^ (authority?.hashCode() : 240073)
            ^^ (user?     .hashCode() : 778777)
            ^^ (host?     .hashCode() : 174263)
            ^^ (ip?       .hashCode() : 425857)
            ^^ (port?     .hashCode() : 855391)
            ^^ (path?     .hashCode() : 380447)
            ^^ (query?    .hashCode() : 273323)
            ^^ (opaque?   .hashCode() : 444487)
            ^^ (fragment? .hashCode() : 277373);
        }

    @Override
    static <CompileType extends Uri> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.scheme    == value2.scheme
            && value1.authority == value2.authority
            && value1.user      == value2.user
            && value1.host      == value2.host
            && value1.ip        == value2.ip
            && value1.port      == value2.port
            && value1.path      == value2.path
            && value1.query     == value2.query
            && value1.opaque    == value2.opaque
            && value1.fragment  == value2.fragment;
        }
    }