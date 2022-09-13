/**
 * A representation of a media type.
 *
 * @see [Media Types](https://www.iana.org/assignments/media-types/media-types.xhtml)
 * @see [RFC 2046](https://tools.ietf.org/html/rfc2046)
 */
const MediaType
    {
    /**
     * Construct a new MediaType.
     *
     * @param name       the name of the MediaType in the format `type/subType`
     * @param extension  the optional extension, if not provided the extension will be the subType
     * @param params     the parameters for the MediaType, additional parameters will be parsed from the name
     *
     * @throws IllegalArgument if the name is not in the `type/subType` format
     */
    construct (String name, String? extension = Null, Map<String, String> params = Map:[])
        {
        name = name.trim();

        String withoutArgs;
        HashMap<String, String> parameters = new HashMap();
        if (name.indexOf(";"))
            {
            String[] tokenWithArgs = name.split(';');
            String[] paramsList    = tokenWithArgs[1 ..< tokenWithArgs.size];
            withoutArgs = tokenWithArgs[0];
            for (String param : paramsList)
                {
                if (param.indexOf("="))
                    {
                    String[] parts = param.split('=');
                    parameters.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        else
            {
            withoutArgs = name;
            }

        if (Int index := withoutArgs.indexOf('/'))
            {
            this.type    = index == 0 ? "" : withoutArgs[0 ..< index];
            this.subType = withoutArgs.substring(index + 1);
            }
        else
            {
            throw new IllegalArgument("Illegal media type name - missing type/subType separator '" + name + "'");
            }

        if (extension != Null)
            {
            this.extension = extension;
            }
        else
            {
            if (Int j := subType.indexOf('+'))
                {
                this.extension = subType.substring(j + 1);
                }
            else
                {
                this.extension = subType;
                }
            }

        parameters.putAll(params);

        this.name        = withoutArgs;
        this.parameters  = parameters;
        this.extension   = extension;
        this.allTypes    = this.type == "*";
        this.allSubTypes = this.subType == "*";
        }


    // ----- Properties ----------------------------------------------------------------------------

    String                  name;
    String                  type;
    Boolean                 allTypes;
    String                  subType;
    Boolean                 allSubTypes;
    String?                 extension;
    HashMap<String, String> parameters;


    // ----- constants -----------------------------------------------------------------------------

    /**
     * The character set parameter {@code "charset"}.
     */
    private static String CHARSET_PARAMETER = "charset";

    /**
     * The version parameter {@code "v"}.
     */
    private static String VERSION_PARAMETER = "v";

    /**
     * The quality parameter {@code "q"}.
     */
    private static String QUALITY_PARAMETER = "q";

    /**
     * The default quality parameter.
     */
    private String DEFAULT_QUALITY = "1.0";


    // ----- MediaType methods ---------------------------------------------------------------------

    /**
     * Determine if the requested MediaType can be satisfied by this MediaType.
     * e.g. text/* will satisfy text/html.
     *
     * @param required  the required MediaType
     *
     * @return True if this MediaType satisfies the required media type
     */
    Boolean matches(MediaType required)
        {
        return (this.allTypes || this.type == required.type)
            && (this.allSubTypes || this.subType == required.subType);
        }

    /**
     * Determine if any of the requested media types can be satisfied by this MediaType.
     * e.g. text/* will satisfy text/html.
     *
     * @param required  the required media types
     *
     * @return True if this MediaType satisfies any of the required media types
     */
    Boolean matchesAny(MediaType[] required)
        {
        for (MediaType mt : required)
            {
            if (matches(mt))
                {
                return True;
                }
            }
        return False;
        }

    /**
     * Determine if any of the actual media types can satisfy any of the
     * required media types.
     *
     * @param actual    the actual media types
     * @param required  the required media types
     *
     * @return True any of the actual media types can satisfy any of the
     *         required media types
     */
    public static Boolean matches(MediaType[] actual, MediaType[] required)
        {
        if (required.size == 0)
            {
            return True;
            }

        for (MediaType mt : required)
            {
            if (mt.name == MediaType.ALL)
                {
                return True;
                }
            }

        if (actual.size == 0)
            {
            return False;
            }

        for (MediaType mt : actual)
            {
            if (mt.matchesAny(required))
                {
                return True;
                }
            }
        return False;
        }

    /**
     * Obtain the optional version parameter for this MediaType.
     *
     * @return The version of the Mime type
     */
    conditional String getVersion()
        {
        return parameters.get(VERSION_PARAMETER);
        }

    /**
     * Obtain the optional character set version parameter for this MediaType.
     *
     * @return The optional character set of the media type
     */
    conditional String getCharset()
        {
        return parameters.get(CHARSET_PARAMETER);
        }

    /**
     * Obtain the quality of the media type.
     *
     * @return The quality of the media type
     */
    public String getQuality()
        {
        return parameters.getOrDefault(QUALITY_PARAMETER, DEFAULT_QUALITY);
        }


    // ----- Stringable interface implementation ---------------------------------------------------

    @Override
    public Int estimateStringLength()
        {
        return name.size;
        }

    @Override
    public Appender<Char> appendTo(Appender<Char> buf)
        {
        buf.addAll(name);
        return buf;
        }


//    // ----- Hashable ------------------------------------------------------------------------------
//
//    static <CompileType extends MediaType> Int hashCode(CompileType value)
//        {
//        return value.name.hashCode();
//        }


    // ----- Orderable -----------------------------------------------------------------------------

    /**
     * MediaTypes are equal if their names are equal.
     */
    static <CompileType extends MediaType> Boolean equals(CompileType value1, CompileType value2)
        {
        return value1.name == value2.name;
        }

    /**
     * MediaTypes are compared by their names.
     */
    static <CompileType extends MediaType> Ordered compare(CompileType value1, CompileType value2)
        {
        return value1.name <=> value2.name;
        }


    // ----- Constants -----------------------------------------------------------------------------

    /**
     * A wildcard media type representing all types.
     */
    static String ALL = "*/*";

    /**
     * A wildcard media type representing all types.
     */
    static MediaType ALL_TYPE = new MediaType(ALL);

   /**
     * Form encoded data: application/x-www-form-urlencoded.
     */
    static String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";

    /**
     * Form encoded data: application/x-www-form-urlencoded.
     */
    static MediaType APPLICATION_FORM_URLENCODED_TYPE = new MediaType(APPLICATION_FORM_URLENCODED);

    /**
     * Short cut for {@link #APPLICATION_FORM_URLENCODED_TYPE}.
     */
    static MediaType FORM = APPLICATION_FORM_URLENCODED_TYPE;

    /**
     * Multi part form data: multipart/form-data.
     */
    static String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * Multi part form data: multipart/form-data.
     */
    static MediaType MULTIPART_FORM_DATA_TYPE = new MediaType(MULTIPART_FORM_DATA);

    /**
     * HTML: text/html.
     */
    static String TEXT_HTML = "text/html";

    /**
     * HTML: text/html.
     */
    static MediaType TEXT_HTML_TYPE = new MediaType(TEXT_HTML, "html");

    /**
     * XHTML: application/xhtml+xml.
     */
    static String APPLICATION_XHTML = "application/xhtml+xml";

    /**
     * XHTML: application/xhtml+xml.
     */
    static MediaType APPLICATION_XHTML_TYPE = new MediaType(APPLICATION_XHTML);

    /**
     * XML: application/xml.
     */
    static String APPLICATION_XML = "application/xml";

    /**
     * XML: application/xml.
     */
    static MediaType APPLICATION_XML_TYPE = new MediaType(APPLICATION_XML, "xml");

    /**
     * JSON: application/json.
     */
    static String APPLICATION_JSON = "application/json";

    /**
     * JSON: application/json.
     */
    static MediaType APPLICATION_JSON_TYPE = new MediaType(MediaType.APPLICATION_JSON, "json");

    /**
     * YAML: application/x-yaml.
     */
    static String APPLICATION_YAML = "application/x-yaml";

    /**
     * YAML: application/x-yaml.
     */
    static MediaType APPLICATION_YAML_TYPE = new MediaType(MediaType.APPLICATION_YAML);

    /**
     * XML: text/xml.
     */
    static String TEXT_XML = "text/xml";

    /**
     * XML: text/xml.
     */
    static MediaType TEXT_XML_TYPE = new MediaType(TEXT_XML);

    /**
     * Plain Text: text/plain.
     */
    static String TEXT_PLAIN = "text/plain";

    /**
     * Plain Text: text/plain.
     */
    static MediaType TEXT_PLAIN_TYPE = new MediaType(TEXT_PLAIN, "txt");

    /**
     * HAL JSON: application/hal+json.
     */
    static String APPLICATION_HAL_JSON = "application/hal+json";

    /**
     * HAL JSON: application/hal+json.
     */
    static MediaType APPLICATION_HAL_JSON_TYPE = new MediaType(APPLICATION_HAL_JSON);

    /**
     * HAL XML: application/hal+xml.
     */
    static String APPLICATION_HAL_XML = "application/hal+xml";

    /**
     * HAL XML: application/hal+xml.
     */
    static MediaType APPLICATION_HAL_XML_TYPE = new MediaType(APPLICATION_HAL_XML);

    /**
     * Atom: application/atom+xml.
     */
    static String APPLICATION_ATOM_XML = "application/atom+xml";

    /**
     * Atom: application/atom+xml.
     */
    static MediaType APPLICATION_ATOM_XML_TYPE = new MediaType(APPLICATION_ATOM_XML);

    /**
     * VND Error: application/vnd.error+json.
     */
    static String APPLICATION_VND_ERROR = "application/vnd.error+json";

    /**
     * VND Error: application/vnd.error+json.
     */
    static MediaType APPLICATION_VND_ERROR_TYPE = new MediaType(APPLICATION_VND_ERROR);

    /**
     * Server Sent Event: text/event-stream.
     */
    static String TEXT_EVENT_STREAM = "text/event-stream";

    /**
     * Server Sent Event: text/event-stream.
     */
    static MediaType TEXT_EVENT_STREAM_TYPE = new MediaType(TEXT_EVENT_STREAM);

    /**
     * JSON Stream: application/x-json-stream.
     */
    static String APPLICATION_JSON_STREAM = "application/x-json-stream";

    /**
     * JSON Stream: application/x-json-stream.
     */
    static MediaType APPLICATION_JSON_STREAM_TYPE = new MediaType(APPLICATION_JSON_STREAM);

    /**
     * BINARY: application/octet-stream.
     */
    static String APPLICATION_OCTET_STREAM = "application/octet-stream";

    /**
     * BINARY: application/octet-stream.
     */
    static MediaType APPLICATION_OCTET_STREAM_TYPE = new MediaType(APPLICATION_OCTET_STREAM);

    /**
     * GraphQL: application/graphql.
     */
    static String APPLICATION_GRAPHQL = "application/graphql";

    /**
     * GraphQL: application/graphql.
     */
    static MediaType APPLICATION_GRAPHQL_TYPE = new MediaType(APPLICATION_GRAPHQL);

    /**
     * Png Image: image/png.
     */
    static String IMAGE_PNG = "image/png";

    /**
     * Png Image: image/png.
     */
    static MediaType IMAGE_PNG_TYPE = new MediaType(IMAGE_PNG, "png");

    /**
     * Jpeg Image: image/jpeg.
     */
    static String IMAGE_JPEG = "image/jpeg";

    /**
     * Jpeg Image: image/jpeg.
     */
    static MediaType IMAGE_JPEG_TYPE = new MediaType(IMAGE_JPEG, "jpg");

    /**
     * Gif Image: image/gif.
     */
    static String IMAGE_GIF = "image/gif";

    /**
     * Gif Image: image/gif.
     */
    static MediaType IMAGE_GIF_TYPE = new MediaType(IMAGE_GIF, "gif");

    /**
     * Webp Image: image/webp.
     */
    static String IMAGE_WEBP = "image/webp";

    /**
     * Webp Image: image/webp.
     */
    static MediaType IMAGE_WEBP_TYPE = new MediaType(IMAGE_WEBP);
    }
