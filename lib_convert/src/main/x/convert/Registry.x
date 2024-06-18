import json.Doc;
import json.Schema;

import net.IPAddress;
import net.Uri;

import formats.BasicFormat;
import formats.BooleanFormat;
import formats.NullableFormat;
import formats.JsonFormat;

/**
 * A registry for codecs, formats, converters, etc.
 */
service Registry
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Default constructor.
     */
    construct() {} finally {
        DefaultFormats.forEach(registerFormat);
    }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The internal registry-by-name of the `Converter` objects.
     */
    protected Map<String, Converter> convertersByName = new HashMap();

    /**
     * The internal registry-by-name of the `Codec` objects.
     */
    protected Map<String, Codec> codecsByName = new HashMap();

    /**
     * The internal registry-by-name of the named `Format` objects.
     */
    protected Map<String, Format> formatsByName = new HashMap();

    /**
     * The internal registry-by-type of derivative `Format` objects from a given named format.
     */
    protected Map<String, Map<Type, Format?>> derivedFormatsByType = new HashMap();

    /**
     * The internal registry-by-type of `Format` objects.
     */
    protected Map<Type, Format> formatsByType = new HashMap();

    /**
     * The internal registry-by-name of various singleton-like resources.
     */
    protected Map<String, Shareable> resources = new HashMap();


    // ----- well-known resources ------------------------------------------------------------------

    /**
     * The JSON Schema held by this registry.
     */
    Schema jsonSchema {
        @Override
        Schema get() {
            if (val schema := resources.get("jsonSchema")) {
                return schema.as(Schema);
            }

            return Schema.DEFAULT;
        }

        @Override
        void set(Schema schema) {
            resources.put("jsonSchema", schema);
        }
    }

    /**
     * These formats are known to the system and automatically registered.
     */
    static Format[] DefaultFormats =
        [
        new BasicFormat<Path>(),
        new BasicFormat<Uri>(),
        new BasicFormat<IPAddress>(),

        // REVIEW CP - should we support Time using the http.parseImfFixDate() & http.formatImfFixDate() helpers instead?
        new BasicFormat<Time>(),
        new BasicFormat<Date>(),
        new BasicFormat<TimeOfDay>(),
        new BasicFormat<Duration>(),

        JsonFormat.Default,
        BooleanFormat,

        new BasicFormat<IntLiteral>(),
        new BasicFormat<FPLiteral>(),

        new BasicFormat< Int   >(),
        new BasicFormat< Int8  >(),
        new BasicFormat< Int16 >(),
        new BasicFormat< Int32 >(),
        new BasicFormat< Int64 >(),
        new BasicFormat< Int128>(),
        new BasicFormat< IntN  >(),
        new BasicFormat<UInt   >(),
        new BasicFormat<UInt8  >(),
        new BasicFormat<UInt16 >(),
        new BasicFormat<UInt32 >(),
        new BasicFormat<UInt64 >(),
        new BasicFormat<UInt128>(),
        new BasicFormat<UIntN  >(),

        new BasicFormat<Dec   >(),
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


    // ----- Converter support ---------------------------------------------------------------------

    /**
     * Register the passed [Converter] for the specified name.
     *
     * @param name   the name to register the `Converter` under
     * @param codec  the `Converter` to register
     */
    void registerConverter(String name, Converter converter) {
        convertersByName.putIfAbsent(name, converter);
    }

    /**
     * Look up a [Converter] by a name.
     *
     * @param name  the name of the `Converter`
     *
     * @return `True` iff there exists a `Converter` for the specified name
     * @return (conditional) the `Converter` for the specified name
     */
    conditional Converter getConverter(String name)
        {
        return convertersByName.get(name);
        }


    // ----- Codec support -------------------------------------------------------------------------

    /**
     * Register the passed [Codec] for the specified name.
     *
     * @param name   the name to register the `Codec` under
     * @param codec  the `Codec` to register
     */
    void registerCodec(Codec codec) {
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
    <Value> conditional Codec<Value> findCodec(String name, Type<Value>? type=Null) {
        if (Codec codec := codecsByName.get(name)) {
            if (type == Null) {
                return True, codec.as(Codec<Value>);
            }

            if (codec.Value.isA(type)) {
                return True, codec.as(Codec<Value>);
            }
        }

        return False;
    }

    // ----- Format support ------------------------------------------------------------------------

    /**
     * Register the passed `Format`. The `Format` is registered by its `Value` type, which must be
     * unique, and by its name iff the same name is not already registered.
     *
     * @param format  the `Format` to register
     */
    void registerFormat(Format format) {
        formatsByName.putIfAbsent(format.name,  format);
        formatsByType.putIfAbsent(format.Value, format);

        if (!Null.is(format.Value)) {
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
    <Value> conditional Format<Value> findFormat(String name, Type<Value>? type=Null) {
        if (Format format := formatsByName.get(name)) {
            if (type == Null || format.Value == type) {
                return True, format.as(Format<Value>);
            }

            Map<Type, Format?> derivedFormats =
                    derivedFormatsByType.computeIfAbsent(name, () -> new HashMap());
            if (Format? derivedFormat := derivedFormats.get(type)) {
                return derivedFormat == Null
                        ? False
                        : (True, derivedFormat.as(Format<Value>));
            }

            if (Format<Value> newFormat := format.forType(type, this)) {
                derivedFormats.put(type, newFormat);
                formatsByType.putIfAbsent(type, format);
                return True, newFormat;
            }
            derivedFormats.put(type, Null);
        }

        return False;
    }

    /**
     * Look up a `Format` by its `Value` type.
     *
     * @param type  the `Value` type for the `Format`
     *
     * @return `True` iff there exists a `Format` for the specified type
     * @return (conditional) the `Format` for the specified type
     */
    <Value> conditional Format<Value> findFormatByType(Type<Value> type) {
        if (Format format := formatsByType.get(type)) {
            return True, format.as(Format<Value>);
        }
        return False;
    }

    // ----- resources support ---------------------------------------------------------------------

    /**
     * Register the passed [Sharable] resource for the specified name.
     *
     * @param name      the name to register the resource under
     * @param resource  the resource to register
     */
    void registerResource(String name, Shareable resource)
        {
        resources.put(name, resource);
        }

    /**
     * Look up a [Sharable] resource  by a name.
     *
     * @param name  the name of the resource
     *
     * @return `True` iff there exists a resource for the specified name
     * @return (conditional) the resource for the specified name
     */
    conditional Shareable getResource(String name)
        {
        return resources.get(name);
        }
    }