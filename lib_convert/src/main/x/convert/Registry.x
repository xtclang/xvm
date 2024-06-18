/**
 * A registry for codecs, formats, converters, etc.
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
        DefaultConverters.forEach(registerConverter);
        DefaultCodecs.entries.forEach(e -> registerCodec(e.key, e.value));
        DefaultFormats.forEach(registerFormat);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The internal registry-by-name of various singleton-like resources.
     */
    private Map<String, Shareable> resources = new HashMap();

    /**
     * The internal registry-by-name of the `Format` objects.
     */
    private Map<String, Codec> codecsByName = new HashMap();

    /**
     * The internal registry-by-type (given a `MediaType`) of the `Codec` objects.
     */
    private Map<MediaType, Map<Type, Codec?>> codecsByMedia = new HashMap();

    /**
     * The internal registry-by-name of the named `Format` objects.
     */
    private Map<String, Format> formatsByName = new HashMap();

    /**
     * The internal registry-by-type of derivative `Format` objects from a given named format.
     */
    private Map<String, Map<Type, Format?>> derivedFormatsByType = new HashMap();

    /**
     * The internal registry-by-from-and-to-type of the `Converter` objects.
     */
    // private Map<Converter.Key, Converter> converters = new HashMap();

    /**
     * The internal registry-by-file extension of the `MediaType` objects.
     */
    private Map<String, MediaType> mediaTypeByExtension = new HashMap();


    // ----- well-known resources ------------------------------------------------------------------

    /**
     * The JSON Schema held by this registry.
     */
    Schema jsonSchema
        {
        @Override
        Schema get()
            {
            if (val schema := resources.get("jsonSchema"))
                {
                return schema.as(Schema);
                }

            return Schema.DEFAULT;
            }

        @Override
        void set(Schema schema)
            {
            resources.put("jsonSchema", schema);
            }
        }

    /**
     * These codecs are known to the system and automatically registered.
     */
    static Map<MediaType, Codec> DefaultCodecs =
        [
        Json       = Utf8Codec,
        JsonLD     = Utf8Codec,
        JsonPatch  = Utf8Codec,
        CSS        = Utf8Codec,
        CSV        = Utf8Codec,
        HTML       = Utf8Codec,
        JavaScript = Utf8Codec,
        Text       = Utf8Codec,
        ];

    /**
     * These formats are known to the system and automatically registered.
     */
    static Format[] DefaultFormats =
        [
        new BasicFormat<Path>(),
        new BasicFormat<Uri>(),
        new BasicFormat<IPAddress>(),

        // REVIEW CP - should we support Time using the Header.parseImfFixDate() & Header.formatImfFixDate() helpers instead?
        new BasicFormat<Time>(),
        new BasicFormat<Date>(),
        new BasicFormat<TimeOfDay>(),
        new BasicFormat<Duration>(),

        new JsonFormat(),

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


    // ----- Codec support -------------------------------------------------------------------------

    /**
     * Register the passed `Codec` for the specified `MediaType`. The `Codec` is registered by its
     * `Value` type, which must be unique, and also by its name iff the same name is not already
     * registered.
     *
     * @param mediaType  the `MediaType` to register the `Codec` under
     * @param codec      the `Codec` to register
     */
    void registerCodec(MediaType mediaType, Codec codec)
        {
        Map<Type, Codec?> codecsByType = codecsByMedia.computeIfAbsent(mediaType, () -> new HashMap());
        assert codecsByType.putIfAbsent(codec.Value, codec)
            || codecsByType.replace(codec.Value, Null, codec);
        codecsByName.putIfAbsent(codec.name, codec);
        }

    /**
     * Look up a `Codec` by a name.
     *
     * @param name  the name of the `Codec`
     * @param type  (optional) the `Value` type for the `Codec`
     *
     * @return `True` iff there exists a `Codec` for the specified name
     * @return (conditional) the `Codec` for the specified name
     */
    <Value> conditional Codec<Value> findCodec(String name, Type<Value>? type=Null)
        {
        if (Codec codec := codecsByName.get(name))
            {
            if (type == Null)
                {
                return True, codec.as(Codec<Value>);
                }

            if (codec.Value.isA(type))
                {
                return True, codec.as(Codec<Value>);
                }
            }

        return False;
        }

    /**
     * Look up a `Codec` first by the `MediaType` that describes the raw data, and then by the
     * `Type` that the `Codec` can encode and decode.
     *
     * @param mediaType  the `MediaType` that the `Codec` will operate on
     * @param type  the `Value` type for the `Codec`
     *
     * @return `True` iff there exists a `Codec` for the specified `MediaType` and `Type`
     * @return (conditional) the `Codec` for the `Type`
     */
    <Value> conditional Codec<Value> findCodec(MediaType mediaType, Type<Value> type)
        {
        if (Map<Type, Codec?> codecsByType := codecsByMedia.get(mediaType))
            {
            if (Codec? codec := codecsByType.get(type))
                {
                return codec == Null
                        ? False
                        : (True, codec.as(Codec<Value>));
                }

            for (Codec? codec : codecsByType.values)
                {
                if (Codec<Value> newCodec := codec?.forType(type.DataType, this))
                    {
                    registerCodec(mediaType, newCodec);
                    return True, newCodec;
                    }
                }

            codecsByType.put(type, Null); // cache the miss
            }

        return False;
        }

    /**
     * Look up a `Codec` by the `Type` that it can encode and decode, and throw an exception if
     * the codec is not found.
     *
     * @param mediaType  the `MediaType` that the `Codec` will operate on
     * @param type       the `Value` type for the `Codec`
     *
     * @return the `Codec` for the specified `Type`
     */
    <Value> Codec<Value> requireCodec(MediaType mediaType, Type<Value> type)
        {
        assert Codec<Value> codec := findCodec(mediaType, type)
                as $"Unable to find Codec for Type {type} on MediaType {mediaType}";

        return codec;
        }


    // ----- Format support ------------------------------------------------------------------------

    /**
     * Register the passed `Format`. The `Format` is registered by its `Value` type, which must be
     * unique, and by its name iff the same name is not already registered.
     *
     * @param format  the `Format` to register
     */
    void registerFormat(Format format)
        {
        formatsByName.putIfAbsent(format.name, format);

        if (!Null.is(format.Value))
            {
            registerFormat(new NullableFormat<format.Value>(format));
            }
        }

    /**
     * Look up a `Format` by a name.
     *
     * @param name  the name of the `Format`
     * @param type  (optional) the `Value` type for the `Format`
     *
     * @return `True` iff there exists a `Format` for the specified name
     * @return (conditional) the `Format` for the specified name
     */
    <Value> conditional Format<Value> findFormat(String name, Type<Value>? type=Null)
        {
        if (Format format := formatsByName.get(name))
            {
            if (type == Null || format.Value == type)
                {
                return True, format.as(Format<Value>);
                }

            Map<Type, Format?> derivedFormats =
                    derivedFormatsByType.computeIfAbsent(name, () -> new HashMap());
            if (Format? derivedFormat := derivedFormats.get(type))
                {
                return derivedFormat == Null
                        ? False
                        : (True, derivedFormat.as(Format<Value>));
                }

            if (Format<Value> newFormat := format.forType(type.DataType, this))
                {
                derivedFormats.put(type, newFormat);
                return True, newFormat;
                }
            derivedFormats.put(type, Null);
            }

        return False;
        }


    // ----- Converter support ---------------------------------------------------------------------

    // TODO CP


    // ----- MediaType support ---------------------------------------------------------------------

    /**
     * Register the passed `MediaType`.
     *
     * @param mediaType  the `MediaType` to register
     */
    void registerMediaType(MediaType mediaType)
        {
        for (String extension : mediaType.extensions)
            {
            assert mediaTypeByExtension.putIfAbsent(extension, mediaType) as
                $|Extension {extension.quoted()} has already been registered by
                 |{mediaTypeByExtension.getOrNull(extension)}"
                 ;
            }
        }

    /**
     * Look up a `MediaType` for the file name extension.
     *
     * @param fileNAme  the file name
     *
     * @return `True` iff there exists a `MediaType` for the specified file name
     * @return (conditional) the `MediaType` for the file name
     */
    conditional MediaType findMediaType(String fileName)
        {
        if (Int of := fileName.lastIndexOf('.'))
            {
            return mediaTypeByExtension.get(fileName.substring(of+1));
            }
        return False;
        }


    // ----- resources support ---------------------------------------------------------------------

    /**
     * TODO
     */
    void registerResource(String name, Shareable resource)
        {
        resources.put(name, resource);
        }

    /**
     * TODO
     */
    conditional Shareable getResource(String name)
        {
        return resources.get(name);
        }
    }