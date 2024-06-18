import json.Doc;
import json.Schema;

import convert.Codec;
import convert.Format;

import convert.codecs.FormatCodec;
import convert.codecs.Utf8Codec;
import convert.formats.JsonFormat;

/**
 * A registry for codecs, formats, media types etc.
 */
service Registry
    extends convert.Registry {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Default constructor.
     */
    construct() {
        construct convert.Registry();
    } finally {
        DefaultCodecs.entries.forEach(e -> registerCodec(e.key, e.value));
        MediaType.Predefined.forEach(registerMediaType);
    }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The internal registry-by-type (given a `MediaType`) of the `Codec` objects.
     */
    protected Map<MediaType, Map<Type, Codec?>> codecsByMedia = new HashMap();

    /**
     * The internal registry-by-file extension of the `MediaType` objects.
     */
    protected Map<String, MediaType> mediaTypeByExtension = new HashMap();


    // ----- well-known resources ------------------------------------------------------------------

    /**
     * These codecs are known to the system and automatically registered.
     */
    static Map<MediaType, Codec> DefaultCodecs =
        [
        Json       = new FormatCodec<Doc>(Utf8Codec, JsonFormat.Default),
        JsonLD     = Utf8Codec,
        JsonPatch  = Utf8Codec,
        CSS        = Utf8Codec,
        CSV        = Utf8Codec,
        HTML       = Utf8Codec,
        JavaScript = Utf8Codec,
        Text       = Utf8Codec,
        FormURL    = Utf8Codec,
        ];


    // ----- Codec support -------------------------------------------------------------------------

    /**
     * Register the passed [Codec] for the specified [MediaType]. The `Codec` is registered by its
     * `Value` type, which must be unique, and also by its name iff the same name is not already
     * registered.
     *
     * @param mediaType  the `MediaType` to register the `Codec` under
     * @param codec      the `Codec` to register
     */
    void registerCodec(MediaType mediaType, Codec codec) {
        Map<Type, Codec?> codecsByType = codecsByMedia.computeIfAbsent(mediaType, () -> new HashMap());
        assert codecsByType.putIfAbsent(codec.Value, codec)
            || codecsByType.replace(codec.Value, Null, codec);
        registerCodec(codec);
    }

    /**
     * Look up a [Codec] first by the `MediaType` that describes the raw data, and then by the
     * `Type` that the `Codec` can encode and decode.
     *
     * @param mediaType  the `MediaType` that the `Codec` will operate on
     * @param type  the `Value` type for the `Codec`
     *
     * @return `True` iff there exists a `Codec` for the specified `MediaType` and `Type`
     * @return (conditional) the `Codec` for the `Type`
     */
    <Value> conditional Codec<Value> findCodec(MediaType mediaType, Type<Value> type) {
        if (Map<Type, Codec?> codecsByType := codecsByMedia.get(mediaType)) {
            if (Codec? codec := codecsByType.get(type)) {
                return codec == Null
                        ? False
                        : (True, codec.as(Codec<Value>));
            }

            for (Codec? codec : codecsByType.values) {
                if (Codec<Value> newCodec := codec?.forType(type, this)) {
                    registerCodec(mediaType, newCodec);
                    return True, newCodec;
                }
            }
        }

        if (String formatName ?= mediaType.format,
                Format<Value> format := findFormat(formatName, type)) {
            Codec<Value> newCodec = new FormatCodec<Value>(Utf8Codec, format);
            registerCodec(mediaType, newCodec);
            return True, newCodec;
        }

        if (Format<Value> format := findFormat(type.toString(), type)) {
            Codec<Value> newCodec = new FormatCodec<Value>(Utf8Codec, format);
            registerCodec(mediaType, newCodec);
            return True, newCodec;
        }

        // cache the miss
        Map<Type, Codec?> codecsByType =
            codecsByMedia.computeIfAbsent(mediaType, () -> new HashMap());
        codecsByType.put(type, Null);
        return False;
    }

    /**
     * Look up a [Codec] by the `Type` that it can encode and decode, and throw an exception if
     * the codec is not found.
     *
     * @param mediaType  the `MediaType` that the `Codec` will operate on
     * @param type       the `Value` type for the `Codec`
     *
     * @return the `Codec` for the specified `Type`
     */
    <Value> Codec<Value> requireCodec(MediaType mediaType, Type<Value> type) {
        assert Codec<Value> codec := findCodec(mediaType, type)
                as $"Unable to find Codec for Type {type} on MediaType {mediaType}";

        return codec;
    }


    // ----- MediaType support ---------------------------------------------------------------------

    /**
     * Register the passed [MediaType].
     *
     * @param mediaType  the `MediaType` to register
     */
    void registerMediaType(MediaType mediaType) {
        for (String extension : mediaType.extensions) {
            assert mediaTypeByExtension.putIfAbsent(extension, mediaType) as
                $|Extension {extension.quoted()} has already been registered by
                 |{mediaTypeByExtension.getOrNull(extension)}"
                 ;
        }
    }

    /**
     * Look up a [MediaType] for the file name extension.
     *
     * @param fileName  the file name
     *
     * @return `True` iff there exists a `MediaType` for the specified file name
     * @return (conditional) the `MediaType` for the file name
     */
    conditional MediaType findMediaType(String fileName) {
        if (Int of := fileName.lastIndexOf('.')) {
            return mediaTypeByExtension.get(fileName.substring(of+1));
        }
        return False;
    }

    /**
     * Find a [MediaType] that best fits the specified data type.
     */
    conditional MediaType inferMediaType(Object content) {
        switch (content.is(_)) {
        case Doc, Number:
            return True, Json;

        case File:
            return findMediaType(content.name);
        }

        Type type = &content.actualType;

        if (jsonSchema.findMapping(type)) {
            // there's a Json schema for this type, so it's convertible (serializable) as Json
            return True, Json;
        }

        // look for a format; if there is one, then we can turn the content into a String and return
        // as Json
        for (Format format : formatsByName.values) {
            if (format.Value == type) {
                return True, Json;
            }
        }
        return False;
    }
}