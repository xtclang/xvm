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
        new LambdaFormat<Path     >(s -> new Path(s)),      // TODO GG new BasicFormat<Path>(),
        new LambdaFormat<URI      >(s -> new URI(s)),       // TODO GG new BasicFormat<URI>(),
        new LambdaFormat<IPAddress>(s -> new IPAddress(s)), // TODO GG new BasicFormat<IPAddress>(),

// TODO?
// Boolean - "true" and "false" like in JSON? or 0/1? N/Y? n/y? no/yes? non/oui?
// Date - what format to use?
// Time - what format to use? HTTP does have a "standard" format
// TimeOfDay - what format to use?
// Duration - what format to use?

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

        new LambdaFormat<IntLiteral>(s -> new IntLiteral(s)),   // TODO GG new BasicFormat<IntLiteral>(),
        new LambdaFormat<FPLiteral >(s -> new FPLiteral(s)),    // TODO GG new BasicFormat<FPLiteral>(),

        new LambdaFormat< Int8  >(s -> new IntLiteral(s).toInt8()),
        new LambdaFormat< Int16 >(s -> new IntLiteral(s).toInt16()),
        new LambdaFormat< Int32 >(s -> new IntLiteral(s).toInt32()),
        new LambdaFormat< Int64 >(s -> new IntLiteral(s).toInt64()),
        new LambdaFormat< Int128>(s -> new IntLiteral(s).toInt128()),
        new LambdaFormat< IntN  >(s -> new IntLiteral(s).toIntN()),
        new LambdaFormat<UInt8  >(s -> new IntLiteral(s).toUInt8()),
        new LambdaFormat<UInt16 >(s -> new IntLiteral(s).toUInt16()),
        new LambdaFormat<UInt32 >(s -> new IntLiteral(s).toUInt32()),
        new LambdaFormat<UInt64 >(s -> new IntLiteral(s).toUInt64()),
        new LambdaFormat<UInt128>(s -> new IntLiteral(s).toUInt128()),
        new LambdaFormat<UIntN  >(s -> new IntLiteral(s).toUIntN()),

        new LambdaFormat<@Unchecked  Int8  >(s -> new IntLiteral(s).toInt8()   .toUnchecked()),
        new LambdaFormat<@Unchecked  Int16 >(s -> new IntLiteral(s).toInt16()  .toUnchecked()),
        new LambdaFormat<@Unchecked  Int32 >(s -> new IntLiteral(s).toInt32()  .toUnchecked()),
        new LambdaFormat<@Unchecked  Int64 >(s -> new IntLiteral(s).toInt64()  .toUnchecked()),
        new LambdaFormat<@Unchecked  Int128>(s -> new IntLiteral(s).toInt128() .toUnchecked()),
        new LambdaFormat<@Unchecked  IntN  >(s -> new IntLiteral(s).toIntN()   .toUnchecked()),
        new LambdaFormat<@Unchecked UInt8  >(s -> new IntLiteral(s).toUInt8()  .toUnchecked()),
        new LambdaFormat<@Unchecked UInt16 >(s -> new IntLiteral(s).toUInt16() .toUnchecked()),
        new LambdaFormat<@Unchecked UInt32 >(s -> new IntLiteral(s).toUInt32() .toUnchecked()),
        new LambdaFormat<@Unchecked UInt64 >(s -> new IntLiteral(s).toUInt64() .toUnchecked()),
        new LambdaFormat<@Unchecked UInt128>(s -> new IntLiteral(s).toUInt128().toUnchecked()),
        new LambdaFormat<@Unchecked UIntN  >(s -> new IntLiteral(s).toUIntN()  .toUnchecked()),

        new LambdaFormat<Dec32 >(s -> new FPLiteral(s).toDec32()),
        new LambdaFormat<Dec64 >(s -> new FPLiteral(s).toDec64()),
        new LambdaFormat<Dec128>(s -> new FPLiteral(s).toDec128()),
        new LambdaFormat<DecN  >(s -> new FPLiteral(s).toDecN()),

        new LambdaFormat<BFloat16>(s -> new FPLiteral(s).toBFloat16()),
        new LambdaFormat<Float16 >(s -> new FPLiteral(s).toFloat16()),
        new LambdaFormat<Float32 >(s -> new FPLiteral(s).toFloat32()),
        new LambdaFormat<Float64 >(s -> new FPLiteral(s).toFloat64()),
        new LambdaFormat<Float128>(s -> new FPLiteral(s).toFloat128()),
        new LambdaFormat<FloatN  >(s -> new FPLiteral(s).toFloatN()),
        ];

    /**
     * The internal registry-by-type of the Format objects.
     */
    private Map<Type, Format?> formatsByType = new HashMap();

    /**
     * The internal registry-by-name of the Format objects.
     */
    private Map<String, Format> formatsByName = new HashMap();


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
            // TODO GG: if (Format<Value> newFormat := format?.forType(type))
            if (val newFormat := format?.forType(type))
                {
                registerFormat(newFormat);
                return True, newFormat /*TODO GG*/.as(Format<Value>);
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