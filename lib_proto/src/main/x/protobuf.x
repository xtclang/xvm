/**
 * The Ecstasy Protocol Buffers module.
 */
module protobuf.xtclang.org {
    /**
     * A type alias for a Protobuf scalar type.
     */
    typedef Int32 | Int64 | UInt32 | UInt64 | Float32 | Float64 | Boolean
            | String | Byte[] as ProtoScalarType;

    /**
     * A type alias for a Protobuf message field type.
     */
    typedef ProtoScalarType | ProtoScalarType[] | MessageLite | MessageLite[]
            | ProtoEnum | ProtoEnum[] as ProtoFieldType;

    void run(String[] args) {
        assert args.size > 1;
        @Inject Directory curDir;

        File requestFile  = curDir.fileFor(args[0]);
        File responseFile = curDir.fileFor(args[1]);

        ProtocPlugin plugin = new ProtocPlugin();
        plugin.generate(requestFile, responseFile);
    }
}
