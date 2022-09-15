/**
 * A codec registry.
 */
service Registry
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Default constructor.
     */
    construct()
        {
        }
    finally
        {
        DefaultFormats.forEach(registerFormat);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * These formats are known to the system and automatically registered.
     */
    static Format[] DefaultFormats =
        [
        new BasicFormat<Path>(),
        new BasicFormat<URI>(),
        new BasicFormat<IPAddress>(),

        new BasicFormat<Time>(),
        new BasicFormat<Date>(),
        new BasicFormat<TimeOfDay>(),
        new BasicFormat<Duration>(),

        // TODO CP
        // Boolean - "true" and "false" like in JSON? or 0/1? N/Y? n/y? no/yes? non/oui?

        // TODO CP need a JSON specific implementation that knows how to answer "forType()" method
        new LambdaFormat<json.Doc>(s ->
            {
            if (json.Doc doc := new json.Parser(new ecstasy.io.CharArrayReader(s)).next())
                {
                return doc;
                }
            else
                {
                return Null;
                }
            }),

        new BasicFormat<IntLiteral>(),
        new BasicFormat<FPLiteral>(),

        new BasicFormat< Int8  >(),
        new BasicFormat< Int16 >(),
        new BasicFormat< Int32 >(),
        new BasicFormat< Int64 >(),
        new BasicFormat< Int128>(),
        new BasicFormat< IntN  >(),
        new BasicFormat<UInt8  >(),
        new BasicFormat<UInt16 >(),
        new BasicFormat<UInt32 >(),
        new BasicFormat<UInt64 >(),
        new BasicFormat<UInt128>(),
        new BasicFormat<UIntN  >(),

        new BasicFormat<@Unchecked  Int8  >(),
        new BasicFormat<@Unchecked  Int16 >(),
        new BasicFormat<@Unchecked  Int32 >(),
        new BasicFormat<@Unchecked  Int64 >(),
        new BasicFormat<@Unchecked  Int128>(),
        new BasicFormat<@Unchecked  IntN  >(),
        new BasicFormat<@Unchecked UInt8  >(),
        new BasicFormat<@Unchecked UInt16 >(),
        new BasicFormat<@Unchecked UInt32 >(),
        new BasicFormat<@Unchecked UInt64 >(),
        new BasicFormat<@Unchecked UInt128>(),
        new BasicFormat<@Unchecked UIntN  >(),

        new BasicFormat<Dec32 >(),
        new BasicFormat<Dec64 >(),
        new BasicFormat<Dec128>(),
        new BasicFormat<DecN  >(),

        new BasicFormat<BFloat16>(),
        new BasicFormat<Float16 >(),
        new BasicFormat<Float32 >(),
        new BasicFormat<Float64 >(),
        new BasicFormat<Float128>(),
        new BasicFormat<FloatN  >(),
        ];

    /**
     * The internal registry-by-type of the Format objects.
     */
    private Map<Type, Format?> formatsByType = new HashMap();

    /**
     * The internal registry-by-name of the Format objects.
     */
    private Map<String, Format> formatsByName = new HashMap();

    /**
     * The internal registry-by-type (given a MediaType) of the Codec objects.
     */
    private Map<MediaType, Map<Type, Codec?>> codecsByType = new HashMap();

    /**
     * The internal registry-by-name of the Format objects.
     */
    private Map<String, Codec> codecsByName = new HashMap();

    /**
     * TODO
     */
//    private Map<Tuple<Type, Type>>, Converter> converters = new HashMap();


    // ----- Format support ------------------------------------------------------------------------

    /**
     * Register the passed `Format`. The `Format` is registered by its `Value` type, which must be
     * unique, and by its name iff the same name is not already registered.
     *
     * @param format  the `Format` to register
     */
    void registerFormat(Format format)
        {
        assert formatsByType.putIfAbsent(format.Value, format)
            || formatsByType.replace(format.Value, Null, format);
        formatsByName.putIfAbsent(format.name, format);

        if (!Null.is(format.Value))
            {
            registerFormat(new NullableFormat<format.Value>(format));
            }
        }

    /**
     * Look up a `Format` by the `Type` that it can encode and decode.
     *
     * @param type  the `Value` type for the `Format`
     *
     * @return `True` iff there exists a `Format` for the specified `Type`
     * @return (conditional) the `Format` for the `Type`
     */
    <Value> conditional Format<Value> findFormat(Type<Value> type)
        {
        if (Format? format := formatsByType.get(type))
            {
            return format == Null
                    ? False
                    : (True, format.as(Format<Value>));
            }

        for (Format? format : formatsByType.values)
            {
            if (Format<Value> newFormat := format?.forType(type, this))
                {
                registerFormat(newFormat);
                return True, newFormat;
                }
            }

        formatsByType.put(type, Null); // cache the miss
        return False;
        }

    /**
     * Look up a `Format` by the `Type` that it can encode and decode, and throw an exception if
     * the format is not found.
     *
     * @param type  the `Value` type for the `Format`
     *
     * @return the `Format` for the specified `Type`
     */
    <Value> Format<Value> requireFormat(Type<Value> type)
        {
        assert Format<Value> format := findFormat(type) as $"Unable to find Format for Type {type}";
        return format;
        }


    // ----- Codec support -------------------------------------------------------------------------

//    /**
//     * Construct a Codec Registry.
//     * The registry will be initialized with the default codecs along
//     * with any additional codecs provided to the constructor.
//     *
//     * @param codecs  an optional array of additional Codec
//     *                instances to initialize the registry with.
//     */
//    construct (Codec[] codecs = [])
//        {
//        codecsByType      = new HashMap();
//        codecsByExtension = new HashMap();
//
//        TODO use "Std": codecs += codecs.DEFAULT_CODECS;
//
//        for (Codec codec : codecs)
//            {
//            for (MediaType mediaType : codecs.mediaTypes)
//                {
//                codecsByType.put(mediaType, codec);
//                String? ext = mediaType.extension;
//                if (ext != Null)
//                    {
//                    codecsByExtension.put(ext, codec);
//                    }
//                }
//            }
//        codecsByType.makeImmutable();
//        codecsByExtension.makeImmutable();
//        }
//
//    /**
//     * A map of MediaType to Codec.
//     */
//    private Map<MediaType, Codec> codecsByType;
//
//    /**
//     * A map of MediaType extension to Codec.
//     */
//    private Map<String, Codec> codecsByExtension;
//
//    /**
//     * Find the Codec that can encode or decode the specified MediaType.
//     * A Codec will be returned if a codec is registered directly with
//     * the media type or alternatively a codec is registered with the requested
//     * media type's extension.
//     *
//     * @param type  the MediatType to encode or decode
//     *
//     * @return a True iff a Codec is associated to the requested MediaType
//     * @return the Codec associated with the specified MediaType (conditional)
//     */
//    conditional Codec findCodec(MediaType type)
//        {
//        if (Codec codec := codecsByType.get(type))
//            {
//            return True, codec;
//            }
//
//        TODO String? ext = type.extension;
//        if (ext != Null)
//            {
//            if (Codec codec := codecsByExtension.get(ext))
//                {
//                return True, codec;
//                }
//            }
//
//        return False;
//        }
    }