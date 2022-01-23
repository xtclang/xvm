/**
 * A registry of MediaTypeCodec instances.
 */
const MediaTypeCodecRegistry
    {
    /**
     * Construct a MediaTypeCodecRegistry.
     * The registry will be initialized with the default codecs along
     * with any additional codecs provided to the constructor.
     *
     * @param codecs  an optional array of additional MediaTypeCodec
     *                instances to initialize the registry with.
     */
    construct (MediaTypeCodec[] codecs = [])
        {
        codecsByType      = new HashMap();
        codecsByExtension = new HashMap();

        codecs += codec.DEFAULT_CODECS;

        for (MediaTypeCodec codec : codecs)
            {
            for (MediaType mediaType : codec.mediaTypes)
                {
                codecsByType.put(mediaType, codec);
                String? ext = mediaType.extension;
                if (ext != Null)
                    {
                    codecsByExtension.put(ext, codec);
                    }
                }
            }
        codecsByType.makeImmutable();
        codecsByExtension.makeImmutable();
        }

    /**
     * A map of MediaType to MediaTypeCodec.
     */
    private Map<MediaType, MediaTypeCodec> codecsByType;

    /**
     * A map of MediaType extension to MediaTypeCodec.
     */
    private Map<String, MediaTypeCodec> codecsByExtension;

    /**
     * Find the MediaTypeCodec that can encode or decode the specified MediaType.
     * A MediaTypeCodec will be returned if a codec is registered directly with
     * the media type or alternatively a codec is registered with the requested
     * media type's extension.
     *
     * @param type  the MediatType to encode or decode
     *
     * @return a True iff a MediaTypeCodec is associated to the requested MediaType
     * @return the MediaTypeCodec associated with the specified MediaType (conditional)
     */
    conditional MediaTypeCodec findCodec(MediaType type)
        {
        if (MediaTypeCodec codec := codecsByType.get(type))
            {
            return True, codec;
            }

        String? ext = type.extension;
        if (ext != Null)
            {
            if (MediaTypeCodec codec := codecsByExtension.get(ext))
                {
                return True, codec;
                }
            }

        return False;
        }
    }