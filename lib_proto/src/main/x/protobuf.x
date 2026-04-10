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
     * A type alias for a Protobuf field that may be a Byte array or unset.
     */
    typedef Presence | Byte[] as MaybeBytes;

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
//        import ecstasy.io.FileInputStream;
//        import ecstasy.io.FileOutputStream;
//
//        import google.DescriptorProto;
//        import google.FileDescriptorProto;
//
//        import google.compiler.CodeGeneratorRequest;
//        import google.compiler.CodeGeneratorResponse;
//
//        assert args.size > 0;
//        @Inject Directory curDir;
//        File requestFile  = curDir.fileFor(args[0]);
//        File responseFile = curDir.fileFor("response-" + args[0]);
//
//        CodedInput in = new CodedInput(new FileInputStream(requestFile));
//
//        CodeGeneratorResponse response = new  CodeGeneratorResponse();
//        CodeGeneratorRequest  request  = new  CodeGeneratorRequest();
//        request.mergeFrom(in);
//
//        Map<String, FileDescriptorProto> fileMap = new HashMap();
//        for (FileDescriptorProto fd : request.protoFile) {
//            MaybeString name = fd.name;
//            assert name.is(String);
//            fileMap.put(name, fd);
//        }
//
//        ProtoCodeGen codeGen = new ProtoCodeGen();
//        for (String fileToGenerate : request.fileToGenerate) {
//            assert FileDescriptorProto descriptor := fileMap.get(fileToGenerate);
//
//            for (DescriptorProto messageDescriptor : descriptor.getMessageTypeList()) {
//                Map<String, String> sourceMap = codeGen.generate(messageDescriptor);
//                for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
//                    String key = entry.key;
//                    String value = entry.value;
//                    CodeGeneratorResponse.File sourceFile = new CodeGeneratorResponse();
//                    sourceFile.name    = entry.key;
//                    sourceFile.content = entry.value;
//                    response.file.add(sourceFile);
//                }
//            }
//        }
//
//        CodedOutput out = new CodedOutput(new FileOutputStream(responseFile));
//        response.writeTo(out);

    }

}
