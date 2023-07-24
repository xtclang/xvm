import collections.LRUCache;

import codecs.Format;


/**
 * A representation of a media type, such as is used in the `Content-Type` header of an HTTP request
 * or response.
 *
 * A MediaType can optionally carry a number of file extensions to which it corresponds.
 *
 * A MediaType for UTF-8 encoded data can specify a [Format], and in doing so, it will be possible
 * to create a pipeline from the binary body contents to the `Value` type of the `Format`, and (via
 * [Format.forType()]), to other supported types. Similarly, to produce an outgoing body, that same
 * path can be followed in reverse. For example, the `Json` MediaType specifies the `JsonFormat`,
 * so it is possible to annotate an endpoint parameter  `Cart` class
 *
 * @see [Media Types](https://www.iana.org/assignments/media-types/media-types.xhtml)
 * @see [RFC 2046](https://tools.ietf.org/html/rfc2046)
 */
const MediaType {
    // ----- standard and/or common predefined media types -----------------------------------------

    static MediaType Json        = predefine("application/json",                              ["json", "map"], "json");
    static MediaType JsonLD      = predefine("application/ld+json",                           "jsonld");
    static MediaType JsonPatch   = predefine("application/json-patch+json"                          );
    static MediaType PDF         = predefine("application/pdf",                               "pdf" );
    static MediaType SQL         = predefine("application/sql",                               "sql" );
    static MediaType JsonAPI     = predefine("application/vnd.api+json"                             );
    static MediaType Word        = predefine("application/msword",                            "doc" );
    static MediaType Excel       = predefine("application/vnd.ms-excel",                      "xls" );
    static MediaType PowerPoint  = predefine("application/vnd.ms-powerpoint",                 "ppt" );

    static MediaType WordX       = predefine("application/vnd.openxmlformats-officedocument.wordprocessingml.document",   "docx");
    static MediaType ExcelX      = predefine("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",         "xlsx");
    static MediaType PowerPointX = predefine("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");

    static MediaType OpenOffice  = predefine("application/vnd.oasis.opendocument.text",       "odt" );
    static MediaType FormURL     = predefine("application/x-www-form-urlencoded"                    );
    static MediaType XML         = predefine(["application/xml", "text/xml"],                 "xml" );
    static MediaType XHTML       = predefine("application/xhtml+xml",                         "xhtml");
    static MediaType Zip         = predefine("application/zip",                               "zip" );
    static MediaType ZStd        = predefine("application/zstd",                              "zst" );
    static MediaType CDAudio     = predefine("application/x-cdf",                             "cda" );
    static MediaType AACAudio    = predefine("audio/aac",                                     "aac" );
    static MediaType MpegAudio   = predefine(["audio/mpeg", "audio/MPA", "audio/mpa-robust"], "mp3" );
    static MediaType OGG         = predefine("audio/ogg",                                     "ogg" );
    static MediaType Opus        = predefine("audio/opus",                                    "opus");
    static MediaType WAV         = predefine("audio/wav",                                     "wav" );
    static MediaType WEBMAudio   = predefine("audio/webm",                                    "weba");
    static MediaType MIDI        = predefine(["audio/midi", "audio/x-midi"],                  ["mid", "midi"]);
    static MediaType AVIF        = predefine("image/avif",                                    "avif");
    static MediaType JPEG        = predefine("image/jpeg",                                    ["jpg", "jpeg", "jfif", "pjpeg", "pjp"]);
    static MediaType PNG         = predefine("image/png",                                     "png" );
    static MediaType SVG         = predefine("image/svg+xml",                                 "svg" );
    static MediaType WebP        = predefine("image/webp",                                    "webp");
    static MediaType FormData    = predefine("multipart/form-data"                                  );
    static MediaType CSS         = predefine("text/css",                                      "css" );
    static MediaType CSV         = predefine("text/csv",                                      "csv" );
    static MediaType HTML        = predefine("text/html",                                     ["htm", "html"]);
    static MediaType JavaScript  = predefine(["text/javascript", "application/javascript"],   "js"  );
    static MediaType Text        = predefine("text/plain",                                    "txt" );
    static MediaType Woff        = predefine("font/woff",                                    "woff" );
    static MediaType Woff2       = predefine("font/woff2",                                   "woff2" );

    /**
     * All of the pre-defined media types.
     */
    static MediaType[] Predefined = [JavaScript, Json, JsonLD, JsonPatch, PDF, SQL, JsonAPI,
            Word, WordX, Excel, ExcelX, PowerPoint, PowerPointX, OpenOffice, FormURL, XML, XHTML,
            Zip, ZStd, CDAudio, AACAudio, MpegAudio, OGG, Opus, WAV, WEBMAudio, MIDI,
            AVIF, JPEG, PNG, SVG, WebP, FormData, CSS, CSV, HTML, Text, Woff, Woff2, ];


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MediaType.
     *
     * @param type        the type, such as "text" in `text/html`
     * @param subtype     the sub-type, such as "html" in `text/html`
     * @param params      (optional) MediaType parameters, which are almost never present
     * @param extensions  (optional) file extensions that the MediaType maps to
     * @param format      (optional) the format name for the text associated with this MediaType
     */
    construct(String              type,
              String              subtype,
              Map<String, String> params     = [],
              String[]            extensions = [],
              String?             format     = Null,
             ) {
        // check type / subtype / params
        assert:arg http.validToken(type) as $"Invalid type: {type.quoted()}";
        assert:arg http.validToken(subtype) as $"Invalid type: {subtype.quoted()}";
        for (String key : params) {
            assert:arg key != "q" && http.validToken(key) as $"Invalid parameter name: {key.quoted()}";
        }

        // check extensions
        for (String ext : extensions) {
            assert:arg http.validExtension(ext) as $"Invalid file extension: {ext.quoted()}";
        }

        this.type       = type;
        this.subtype    = subtype;
        this.params     = params;
        this.extensions = extensions;
        this.format     = format;
        this.text       = $"{type}/{subtype}";

        if (!params.empty) {
            // TODO CP escape value portion if necessary as HTTP "quoted-string"
            this.text += params.appendTo(new StringBuffer(), sep=";", pre=";", post="");
        }
    }

    /**
     * Internal constructor for a MediaType parsed from a string.
     *
     * @param text          the text that the MediaType was parsed from
     * @param type          the type, such as "text" in `text/html`
     * @param subtype       the sub-type, such as "html" in `text/html`
     * @param params        (optional) MediaType parameters, which are almost never present
     * @param extensions    (optional) file extensions that the MediaType maps to
     * @param format        (optional) the format name for the text associated with this MediaType
     * @param alternatives  (optional) alternative MediaTypes
     */
    private construct(String              text,
                      String              type,
                      String              subtype,
                      Map<String, String> params       = [],
                      String[]            extensions   = [],
                      String?             format       = Null,
                      MediaType[]         alternatives = [],
                     ) {
        this.text         = text;
        this.type         = type;
        this.subtype      = subtype;
        this.params       = params;
        this.extensions   = extensions;
        this.format       = format;
        this.alternatives = alternatives;
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
    static conditional MediaType of(String text) {
        if (MediaType mediaType := knownTypes.get(text)) {
            return True, mediaType;
        }

        if (Marker|MediaType result := cache.get(text)) {
            return result.is(MediaType)
                    ? (True, result)
                    : False;
        }

        if ((String type, String subtype, Map<String, String> params) := parseMediaType(text)) {
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
     * The text format name associated with this MediaType, iff this MediaType is UTF8 encoded text
     * and a known [codecs.Format] is available for it.
     */
    String? format;

    /**
     * The secondary, tertiary, and so on, MediaType objects related to this MediaType. This is
     * an historical anachronism from the early days of the web, when arbitrarily defining
     * multiple different media types to represent the same exact media type was considered
     * normal.
     */
    MediaType[] alternatives = [];

    /**
     * A cache of predefined MediaType objects keyed by the text used to create the MediaType.
     */
    private static HashMap<String, MediaType> knownTypes = {
        HashMap<String, MediaType> map = new HashMap(Predefined.size);
        for (MediaType mt : Predefined) {
            map.put(mt.text, mt);
            for (MediaType alt : mt.alternatives) {
                map.put(alt.text, alt);
            }
        }
        return map.freeze(True);
    };

    /**
     * A token used to indicate a negative cache result.
     */
    private enum Marker {Invalid}

    /**
     * A cache of MediaType objects keyed by the text used to create the MediaType.
     */
    private static LRUCache<String, Marker|MediaType> cache = new LRUCache(1K);


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    String toString() {
        return text;
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Construct one of the pre-defined (aka "standard") media types.
     *
     * @param name       one or more media type strings in the form "type/subtype"
     * @param extension  (optional) one or more file extensions to associate with this `MediaType`
     * @param format     (optional) the format name for the text associated with this `MediaType`
     */
    private static MediaType predefine(String|String[] name, String|String[] extension = [], String? format=Null) {
        MediaType[] alternatives = [];
        if (name.is(String[])) {
            alternatives = new MediaType[];
            for (Int index = 1, Int count = name.size; index < count; ++index) {
                String altName = name[index];
                assert (String type, String subtype, Map<String, String> params) := parseMediaType(altName);
                MediaType altType = new MediaType(altName, type, subtype, params);
                alternatives.add(altType);
            }
            name = name[0];
        }

        assert (String type, String subtype, Map<String, String> params) := parseMediaType(name);
        String[] extensions = extension.is(String[])
                ? extension
                : [extension];

        return new MediaType(name, type, subtype, params, extensions, format, alternatives);
    }

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
    static conditional (String type, String subtype, Map<String, String> params) parseMediaType(String text) {
        String              type;
        String              subtype;
        Map<String, String> params  = [];

        String part;
        String rest;
        if (Int semi := text.indexOf(';')) {
            part = text[0 ..< semi];
            rest = text[semi >..< text.size];
        } else {
            part = text;
            rest = "";
        }

        if (Int slash := part.indexOf('/')) {
            type    = part[0 ..< slash].trim();
            subtype = part[slash >..< part.size].trim();
            if (!(http.validToken(type) && http.validToken(subtype))) {
                return False;
            }
        } else {
            return False;
        }

        while (rest.size > 0) {
            if (Int semi := rest.indexOf(';')) {
                part = rest[0 ..< semi];
                rest = rest[semi >..< rest.size];
            } else {
                part = rest;
                rest = "";
            }

            if (Int eq := part.indexOf('=')) {
                String key = part[0 ..< eq].trim();
                String val = part[eq >..< part.size].trim();
                if (key.size == 0 || key == "q" || key == "Q" || !http.validToken(key)) {
                    // the quality key ("q") is reserved for use by the Accept header, and must not
                    // exist as a parameter
                    return False;
                }

                (params.empty ? new ListMap<String, String>() : params).put(key, val);
            } else {
                return False;
            }
        }

        return True, type, subtype, params;
    }
}