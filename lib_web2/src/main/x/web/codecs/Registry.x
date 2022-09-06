/**
 * A codec registry.
 */
service Registry
    {
//    findStringCodec(Type)
//    findBinaryCodec(Type)

    // TODO

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