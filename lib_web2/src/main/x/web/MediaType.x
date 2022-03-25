import collections.LRUCache;

/**
 * A representation of a media type, such as is used in the `Content-Type` header of an HTTP request
 * or response.
 *
 * A MediaType can optionally carry a number of file extensions to which it corresponds.
 *
 * @see [Media Types](https://www.iana.org/assignments/media-types/media-types.xhtml)
 * @see [RFC 2046](https://tools.ietf.org/html/rfc2046)
 */
const MediaType
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MediaType.
     *
     * @param type        the type, such as "text" in `text/html`
     * @param subtype     the sub-type, such as "html" in `text/html`
     * @param params      (optional) MediaType parameters, which are almost never present
     * @param extensions  (optional) file extensions that the MediaType maps to
     */
    construct(String type, String subtype, Map<String, String> params=[], String[] extensions=[])
        {
        // check type / subtype / params
        assert:arg http.validToken(type) as $"Invalid type: {type.quoted()}";
        assert:arg http.validToken(subtype) as $"Invalid type: {subtype.quoted()}";
        for (String key : params)
            {
            assert:arg key != "q" && http.validToken(key) as $"Invalid parameter name: {key.quoted()}";
            }

        // check extensions
        for (String ext : extensions)
            {
            assert:arg http.validExtension(ext) as $"Invalid file extension: {ext.quoted()}";
            }

        this.type       = type;
        this.subtype    = subtype;
        this.params     = params;
        this.extensions = extensions;
        this.text       = $"{type}/{subtype}";

        if (!params.empty)
            {
            // TODO CP escape value portion if necessary as HTTP "quoted-string"
            this.text += params.appendTo(new StringBuffer(), sep=";", pre=";", post="");
            }
        }

    /**
     * Internal constructor for a MediaType parsed from a string.
     *
     * @param text        the text that the MediaType was parsed from
     * @param type        the type, such as "text" in `text/html`
     * @param subtype     the sub-type, such as "html" in `text/html`
     * @param params      (optional) MediaType parameters, which are almost never present
     * @param extensions  (optional) file extensions that the MediaType maps to
     */
    private construct(String text, String type, String subtype, Map<String, String> params, String[] extensions=[])
        {
        this.text       = text;
        this.type       = type;
        this.subtype    = subtype;
        this.params     = params;
        this.extensions = extensions;
        }

    /**
     * Given the passed text containing MediaType information, obtain the corresponding MediaType
     * object.
     *
     * @param text  a string in the form of "type/subtype"
     *
     * @return True if the MediaType could be successfully parsed
     * @return (conditional) the parsed MediaType
     */
    static conditional MediaType of(String text)
        {
        if (Marker|MediaType result := cache.get(text))
            {
            return result.is(MediaType)
                    ? (True, result)
                    : False;
            }

        if ((String type, String subtype, Map<String, String> params) := parseMediaType(text))
            {
            MediaType mediaType = new MediaType(text, type, subtype, params);
            cache.put(text, mediaType);
            return True, mediaType;
            }

        cache.put(text, Invalid);
        return False;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The text that would represent this MediaType in an HTTP message.
     */
    String text;

    /**
     * The type of the MediaType; for example, "text" in the MediaType `text/html`.
     */
    String type;

    /**
     * The subtype of the MediaType; for example, "html" in the MediaType `text/html`.
     */
    String subtype;

    /**
     * The MediaType parameters, which are rarely if ever used.
     */
    Map<String, String> params;

    /**
     * The file extensions associated with this MediaType.
     */
    String[] extensions;

    /**
     * The secondary, tertiary, and so on, MediaType objects related to this MediaType. This is
     * an historical anachronism from the early days of the web, when arbitrarily defining
     * multiple different media types to represent the same exact media type was considered
     * normal.
     */
    MediaType[] alternatives = [];

    /**
     * A token used to indicate a negative cache result.
     */
    private enum Marker {Invalid}

    /**
     * A cache of MediaType objects keyed by the text used to create the MediaType.
     */
    private static LRUCache<String, Marker|MediaType> cache = new LRUCache(1000);


    // ----- TODO ------------------------------------------------------------------

    @Override
    String toString()
        {
        return text;
        }


    // ----- standard media types ------------------------------------------------------------------

    // TODO GG remove the various "Array<String>:"
    enum Std
            extends MediaType
        {
        JavaScript  ("application/javascript",                                                    "js"),
        Json        ("application/json",                                                          "json"),
        JsonLD      ("application/ld+json",                                                       "jsonld"),
        PDF         ("application/pdf",                                                           "pdf"),
        SQL         ("application/sql",                                                           "sql"),
        JsonAPI     ("application/vnd.api+json",                                                  Array<String>:[]),
        OpenOffice  ("application/vnd.oasis.opendocument.text",                                   "odt"),
        Word        ("application/msword",                                                        "doc"),
        WordX       ("application/vnd.openxmlformats-officedocument.wordprocessingml.document",   "docx"),
        Excel       ("application/vnd.ms-excel",                                                  "xls"),
        ExcelX      ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",         "xlsx"),
        PowerPoint  ("application/vnd.ms-powerpoint",                                             "ppt"),
        PowerPointX ("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
        FormURL     ("application/x-www-form-urlencoded",                                         Array<String>:[]),
        XML         (Array<String>:["application/xml", "text/xml"],                               "xml"),
        XHTML       ("application/xhtml+xml",                                                     "xhtml"),
        Zip         ("application/zip",                                                           "zip"),
        ZStd        ("application/zstd",                                                          "zst"),
        CDAudio     ("application/x-cdf",                                                         "cda"),
        MpegAudio   (Array<String>:["audio/mpeg", "audio/MPA", "audio/mpa-robust"],               "mp3"),
        AACAudio    ("audio/aac",                                                                 "aac"),
        OGG         ("audio/ogg",                                                                 "ogg"),
        Opus        ("audio/opus",                                                                "opus"),
        WAV         ("audio/wav",                                                                 "wav"),
        WEBMAudio   ("audio/webm",                                                                "weba"),
        MIDI        (Array<String>:["audio/midi", "audio/x-midi"],                                Array<String>:["mid", "midi"]),
        AVIF        ("image/avif",                                                                "avif"),
        JPEG        ("image/jpeg",                                                                Array<String>:["jpg", "jpeg", "jfif", "pjpeg", "pjp"]),
        PNG         ("image/png",                                                                 "png"),
        SVG         ("image/svg+xml",                                                             "svg"),
        WebP        ("image/webp",                                                                "webp"),
        FormData    ("multipart/form-data",                                                       Array<String>:[]),
        CSS         ("text/css",                                                                  "css"),
        CSV         ("text/csv",                                                                  "csv"),
        HTML        ("text/html",                                                                 Array<String>:["htm", "html"]),
        Text        ("text/plain",                                                                "txt"),
        ;

        /**
         * Construct one of the pre-defined (aka "standard") media types.
         *
         * @param name       one or more media type strings in the form "type/subtype"
         * @param extension  one or more file extensions to associate with the media type
         */
        construct(String|String[] name, String|String[] extension)
            {
            if (name.is(String[]))
                {
                alternatives = new MediaType[];
                for (Int index = 1, Int count = name.size; index < count; ++index)
                    {
                    assert MediaType altType := MediaType.of(name[index]);
                    alternatives.add(altType);
                    }
                name = name[0];
                }

            text = name;
            assert (type, subtype, params) := parseMediaType(name);

            extensions = extension.is(String[])
                    ? extension
                    : [extension];
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Given the passed text containing MediaType information, validate that the text is correctly
     * formatted, and parse it into the various pieces of the corresponding MediaType.
     *
     * @param text  the text that contains the MediaType information
     *
     * @return True iff the passed text is a valid MediaType string
     * @return type     (conditional) the type, such as "text" in `text/html`
     * @return subtype  (conditional) the sub-type, such as "html" in `text/html`
     * @return params   (conditional) the MediaType parameters (almost always empty)
     */
    static conditional (String type, String subtype, Map<String, String> params) parseMediaType(String text)
        {
        String              type;
        String              subtype;
        Map<String, String> params  = [];

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
            if (!(http.validToken(type) && http.validToken(subtype)))
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
                if (key.size == 0 || key == "q" || key == "Q" || !http.validToken(key))
                    {
                    // the quality key ("q") is reserved for use by the Accept header, and must not
                    // exist as a parameter
                    return False;
                    }

                (params.empty ? new ListMap<String, String>() : params).put(key, val);
                }
            else
                {
                return False;
                }
            }

        return True, type, subtype, params;
        }
    }
