package codec
    {
    /**
     * The default codecs for encoding and decoding data from and too different media types.
     */
    MediaTypeCodec[] DEFAULT_CODECS =
        [
        new TextPlainCodec(),
        new JsonCodec(),
        ];
    }