import collections.LRUCache;

/**
 * A representation of a list of media types that are accepted, as found in an `Accept` header. For
 * example:
 *
 *     Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*\/*;q=0.8
 */
const AcceptList
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an AcceptList from an array of AcceptTypes.
     *
     * @param accepts  the array of AcceptTypes
     */
    construct(AcceptType[] accepts)
        {
        // render the types as they would appear in an "Accept:" header
        String text = accepts.appendTo(new StringBuffer(
                accepts.estimateStringLength(",", "", "")), ",", "", "").toString();

        // sort AcceptTypes into 3 buckets: 1.0, 0.0, and anything in-between
        AcceptType[] always = [];
        AcceptType[] maybes = [];
        AcceptType[] nevers = [];
        if (accepts.all(a -> a.quality == 1.0))
            {
            always = accepts;
            }
        else
            {
            for (AcceptType accept : accepts)
                {
                switch (accept.quality)
                    {
                    case 1.0:
                        always = (always.empty ? new AcceptType[] : always).add(accept);
                        break;
                    default:
                        maybes = (maybes.empty ? new AcceptType[] : maybes).add(accept);
                        break;
                    case 0.0:
                        nevers = (nevers.empty ? new AcceptType[] : nevers).add(accept);
                        break;
                    }
                }
            }

        construct AcceptList(text, always, maybes, nevers);
        }

    /**
     * Internal constructor.
     *
     * @param text    the text of the
     * @param always  the array of AcceptTypes with quality 1.0
     * @param maybes  the array of AcceptTypes with quality between 0 and 1, exclusive
     * @param nevers  the array of AcceptTypes with quality 0.0
     */
    private construct(String text, AcceptType[] always, AcceptType[] maybes,  AcceptType[] nevers)
        {
        this.text   = text;
        this.always = always;
        this.maybes = maybes.size <= 1 ? maybes : maybes.sorted((a1, a2) -> a2.quality <=> a1.quality, inPlace=True);
        this.nevers = nevers;
        }

    /**
     * Given the passed text containing comma-separated AcceptType information, obtain the
     * corresponding AcceptList object.
     *
     * @param text  a string containing one or more AcceptTypes
     *
     * @return True if the AcceptList could be successfully parsed
     * @return (conditional) the parsed AcceptList of AcceptTypes
     */
    conditional AcceptList of(String text)
        {
        if (Marker|AcceptList result := cache.get(text))
            {
            return result.is(AcceptList)
                    ? (True, result)
                    : False;
            }

        // parse the text into AcceptType objects
        AcceptType[] always = [];
        AcceptType[] maybes = [];
        AcceptType[] nevers = [];
        for (String part : text.split(','))
            {
            if (AcceptType accept := AcceptType.of(part))
                {
                switch (accept.quality)
                    {
                    case 1.0:
                        always = (always.empty ? new AcceptType[] : always).add(accept);
                        break;
                    default:
                        maybes = (maybes.empty ? new AcceptType[] : maybes).add(accept);
                        break;
                    case 0.0:
                        nevers = (nevers.empty ? new AcceptType[] : nevers).add(accept);
                        break;
                    }
                }
            else
                {
                cache.put(text, Invalid);
                return False;
                }
            }

        AcceptList accepts = new AcceptList(text, always, maybes, nevers);
        cache.put(text, accepts);
        return True, accepts;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The textual form of the AcceptList, as it would appear in an "Accept:" header.
     */
    String text;

    /**
     * The AcceptTypes with a quality of 1.0.
     */
    AcceptType[] always;

    /**
     * The AcceptTypes with a quality between 0.0 and 1.0 (excslusive).
     */
    AcceptType[] maybes;

    /**
     * The AcceptTypes with a quality of 0.0.
     */
    AcceptType[] nevers;

    /**
     * A token used to indicate a negative cache result.
     */
    private enum Marker {Invalid}

    /**
     * A cache of AcceptList objects keyed by the text used to create the AcceptList.
     */
    private static LRUCache<String, Marker|AcceptList> cache = new LRUCache(1K);


    // ----- individual AcceptType -----------------------------------------------------------------

    /**
     * An `AcceptType` is a single entry in an AcceptList. For example:
     *
     *     Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*\/*;q=0.8
     *
     * The above HTTP header entry includes 4 AcceptTypes:
     * * `text/html;q=1.0`
     * * `application/xhtml+xml;q=1.0`
     * * `application/xml;q=0.9`
     * * `*\/*;q=0.8`
     *
     * @param type     for example "text" in the `text/html` media type
     * @param subtype  for example "html" in the `text/html` media type
     * @param params   media type parameters, which are very rarely used
     * @param quality  the "q=" appears to be the last param, but it is **not** a param; rather, it
     *                 is a separate "quality" preference, where 1.0 indicates "perfect" and 0.0
     *                 indicates "absolutely not", and everything in between is a gradient thereof
     */
    static const AcceptType(String type, String subtype, Map<String, String> params=[], Dec quality=1.0)
        {
        construct(String type, String subtype, Map<String, String> params=[], Dec quality=1.0)
            {
            assert:arg type == "*" || http.validToken(type) as $"Invalid media type: {type.quoted()}";
            assert:arg subtype == "*" || http.validToken(subtype) as $"Invalid media subtype: {subtype.quoted()}";
            assert:arg type != "*" || subtype == "*" as $"Invalid usage of wildcard: \"{type}/{subtype}\"";
            assert:arg params.empty || params.keys.all(k -> http.validToken(k) && k != "q" && k != "Q");
            assert:arg 0 <= quality <= 1;

            this.type    = type;
            this.subtype = subtype;
            this.params  = params;
            this.quality = quality;
            }

        /**
         * Produce an AcceptType for the passed String.
         *
         * @param text  a chunk of text that should contain an AcceptType
         *
         * @return True if the AcceptType could be successfully parsed
         * @return (conditional) the parsed AcceptType
         */
        static conditional AcceptType of(String text)
            {
            // we don't bother to cache AcceptType objects; it's expected to be extremely rare to
            // ever see new ones, once a few AcceptList objects are cached
            if ((String type, String subtype, Map<String, String> params, Dec quality) := parseAcceptType(text))
                {
                return True, new AcceptType(type, subtype, params, quality);
                }

            return False;
            }

        /**
         * Determine if this AcceptType matches a MediaType.
         */
        conditional Dec matches(MediaType that)
            {
            if (this.type == "*")
                {
                return True, quality;
                }

            if (this.type != that.type)
                {
                return False;
                }

            if (this.subtype == "*")
                {
                return True, quality;
                }

            if (this.subtype != that.subtype)
                {
                return False;
                }

            // the specifications do not have any indication how params are to be matched, e.g.
            // does the integer "level" value indicate that higher should cover lower or vice-versa?
            // as a result, we accept only exact matches
            return this.params == that.params
                    ? (True, quality)
                    : False;
            }

        @Override
        Int estimateStringLength()
            {
            TODO
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            TODO
            }

        /**
         * Given the passed text containing AcceptType information, validate that the text is
         * correctly formatted, and parse it into the various pieces of the AcceptType.
         *
         * @param text  the text that contains the AcceptType information
         *
         * @return True iff the passed text is a valid AcceptType string
         * @return type     (conditional) the type, such as "text" in `text/html`; may be "*"
         * @return subtype  (conditional) the sub-type, such as "html" in `text/html`; may be "*"
         * @return params   (conditional) the MediaType parameters (almost always empty)
         * @return quality  (conditional) a quality value between 0 and 1, inclusive
         */
        static conditional (String type, String subtype, Map<String, String> params, Dec quality) parseAcceptType(String text)
            {
            String              type;
            String              subtype;
            Map<String, String> params  = [];
            Dec                 quality = 1.0;

            String part;
            String rest;
            if (Int semi := text.indexOf(';'))
                {
                part = text[0..semi);
                rest = text[semi+1 .. text.size);
                }
            else
                {
                part = text;
                rest = "";
                }

            if (Int slash := part.indexOf('/'))
                {
                type    = part[0..slash).trim();
                subtype = part[slash+1 .. part.size).trim();
                if (!(type == "*" && subtype == "*"
                        || http.validToken(type) && (subtype == "*" || http.validToken(subtype))))
                    {
                    return False;
                    }
                }
            else
                {
                return False;
                }

            while (rest.size > 0)
                {
                if (Int semi := rest.indexOf(';'))
                    {
                    part = rest[0..semi);
                    rest = rest[semi+1 .. rest.size);
                    }
                else
                    {
                    part = rest;
                    rest = "";
                    }

                if (Int eq := part.indexOf('='))
                    {
                    String key = part[0..eq).trim();
                    String val = part[eq+1 .. part.size).trim();
                    if (key.size == 0 || !http.validToken(key))
                        {
                        return False;
                        }
                    else if (key == "q" || key == "Q")
                        {
                        // the quality key ("q") is reserved for use by the Accept header
                        try
                            {
                            quality = new FPLiteral(val).toDec64();
                            }
                        catch (Exception e)
                            {
                            return False;
                            }
                        if (!(0.0 <= quality <= 1.0))
                            {
                            return False;
                            }
                        // no parameters are after quality; there's some other stuff in the spec
                        // that can theoretically be present, but no one uses that capability
                        break;
                        }

                    (params.empty ? new ListMap<String, String>() : params).put(key, val);
                    }
                else
                    {
                    return False;
                    }
                }

            return True, type, subtype, params, quality;
            }
        }


    // ----- methods -------------------------------------------------------------------------------

    /**
     * Determine if the required MediaType matches an AcceptType in this AcceptList.
     * For example, an AcceptList containing `text/*` will match `text/html`.
     *
     * @param required  the required MediaType
     *
     * @return True if the AcceptList satisfies the required media type
     * @return (conditional) the MediaType to use (which may differ from the passed MediaType in the
     *         case of an alternative MediaType being selected)
     * @return (conditional) the AcceptType that matched the MediaType
     */
    conditional (MediaType selected, AcceptType matched) matches(MediaType required)
        {
        Boolean mightMatch = True;
        for (AcceptType accept : nevers)
            {
            if (accept.matches(required))
                {
                mightMatch = False;
                break;
                }
            }

        if (mightMatch)
            {
            for (AcceptType accept : always)
                {
                if (accept.matches(required))
                    {
                    return True, required, accept;
                    }
                }
            }

        // check alternatives
        MediaType?  bestAlt    = Null;
        AcceptType? altMatched = Null;
        for (MediaType alt : required.alternatives)
            {
            if ((MediaType selected, AcceptType matched) := matches(alt))
                {
                if (matched.quality == 1.0)
                    {
                    return True, selected, matched;
                    }
                else if (matched.quality > (altMatched?.quality : 0))
                    {
                    bestAlt    = selected;
                    altMatched = matched;
                    }
                }
            }

        if (mightMatch)
            {
            for (AcceptType accept : maybes)
                {
                if (accept.matches(required))
                    {
                    return altMatched?.quality > accept.quality
                            ? (True, bestAlt.as(MediaType), altMatched)
                            : (True, required, accept);
                    }
                }
            }

        // TODO GG:
        // return bestAlt == Null
        //     ? False
        //     : True, bestAlt, altMatched;
        return True, bestAlt?, altMatched?;
        return False;
        }

    /**
     * Determine if the any of the required MediaTypes matches an AcceptType in this AcceptList.
     *
     * @param required  the required MediaTypes
     *
     * @return True if the AcceptList satisfies at least one of the required media types
     * @return (conditional) the MediaType to use (which may differ from the passed MediaTypes in
     *         the case of an alternative MediaType being selected)
     * @return (conditional) the AcceptType that matched the MediaType
     */
    conditional (MediaType selected, AcceptType matched) matches(MediaType[] required)
        {
        MediaType?  bestMediaType  = Null;
        AcceptType? bestAcceptType = Null;
        for (MediaType mediaType : required)
            {
            if ((MediaType selected, AcceptType matched) := matches(mediaType))
                {
                if (matched.quality == 1.0)
                    {
                    return True, selected, matched;
                    }
                else if (matched.quality > (bestAcceptType?.quality : 0.0))
                    {
                    bestMediaType  = selected;
                    bestAcceptType = matched;
                    }
                }
            }

        return True, bestMediaType?, bestAcceptType?;
        return False;
        }
    }
