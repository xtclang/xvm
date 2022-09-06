package codecs
    {
    /**
     * The default codecs for encoding and decoding data from and to different media types.
     */
    Codec[] DEFAULT_CODECS =
        [
        new TextPlainCodec(),
        new JsonCodec(),
        ];
    }