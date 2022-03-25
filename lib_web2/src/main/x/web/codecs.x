package codecs
    {
    /**
     * The default codecs for encoding and decoding data from and to different media types.
     */
    MediaTypeCodec[] DEFAULT_CODECS =
        [
        new TextPlainCodec(),
        new JsonCodec(),
        ];
    }