/**
 * The Ecstasy Protocol Buffers module.
 */
module protobuf.xtclang.org {

    /**
     * An enum used to indicate a Protobuf field is unset.
     */
    enum Presence {
        Unset
    }

    /**
     * A type alias for a Protobuf field that may be an Int32 or unset.
     */
    typedef Presence | Int32 as MaybeInt32;

    /**
     * A type alias for a Protobuf field that may be an Int64 or unset.
     */
    typedef Presence | Int64 as MaybeInt64;

    /**
     * A type alias for a Protobuf field that may be a Float32 or unset.
     */
    typedef Presence | Float32 as MaybeFloat32;

    /**
     * A type alias for a Protobuf field that may be a Float64 or unset.
     */
    typedef Presence | Float64 as MaybeFloat64;

    /**
     * A type alias for a Protobuf field that may be a UInt32 or unset.
     */
    typedef Presence | UInt32 as MaybeUInt32;

    /**
     * A type alias for a Protobuf field that may be a UInt64 or unset.
     */
    typedef Presence | UInt64 as MaybeUInt64;

    /**
     * A type alias for a Protobuf field that may be a Boolean or unset.
     */
    typedef Presence | Boolean as MaybeBoolean;

    /**
     * A type alias for a Protobuf field that may be a String or unset.
     */
    typedef Presence | String as MaybeString;

    /**
     * A type alias for a Protobuf field that may be a ByteString or unset.
     */
    typedef Presence | ByteString as MaybeByteString;

    /**
     * A type alias for a Protobuf field that may be a Byte array or unset.
     */
    typedef Presence | Byte[] as MaybeBytes;


    void run(String[] args) {
        @Inject Console console;
        console.print("Ecstasy Protocol Buffers");
    }

}
