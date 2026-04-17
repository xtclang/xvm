import ecstasy.lang.src.Compiler;
import ecstasy.mgmt.ModuleRepository;

import protobuf.ProtoCodeGen;
import protobuf.ProtoParser;
import protobuf.wellknown.FileDescriptorProto;

class ProtoCodeGenTest {

    // ----- simple scalar message -----------------------------------------------------------------

    @Test
    void shouldGenerateSimpleMessage() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Person \{
                                              |  string name = 1;
                                              |  int32 id = 2;
                                              |  bool active = 3;
                                              |}
                                             );
        assert files.size == 1;
        assert String source := files.get("Person.x");

        // class declaration
        assert source.indexOf("class Person");
        assert source.indexOf("extends protobuf.AbstractMessage");

        // proto3 implicit presence: no presence bitmask, no backing fields
        assert !source.indexOf("presentBits_0");
        assert !source.indexOf("private String _name");
        assert !source.indexOf("private Int32 _id");
        assert !source.indexOf("private Boolean _active");

        // plain field declarations with defaults
        assert source.indexOf("String name = \"\"");
        assert source.indexOf("Int32 id = 0");
        assert source.indexOf("Boolean active = False");

        // parseField
        assert source.indexOf("input.readString()");
        assert source.indexOf("input.readInt32()");
        assert source.indexOf("input.readBool()");

        // writeKnownFields with default-value checks (not presence bits)
        assert source.indexOf("name.size != 0");
        assert source.indexOf("id != 0");

        // knownFieldsSize
        assert source.indexOf("computeStringSize(1, name)");
        assert source.indexOf("computeInt32Size(2, id)");
        assert source.indexOf("computeBoolSize(3)");

        // freeze
        assert source.indexOf("immutable Person freeze");
    }

    // ----- repeated fields -----------------------------------------------------------------------

    @Test
    void shouldGenerateRepeatedField() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message List \{
                                              |  repeated string items = 1;
                                              |  repeated int32 numbers = 2;
                                              |}
                                             );
        assert String source := files.get("List.x");

        // repeated string uses Array, not Maybe
        assert source.indexOf("String[] items = []");
        // repeated int32 uses packed encoding
        assert source.indexOf("Int32[] numbers = []");
        assert source.indexOf("writePackedVarints");
        assert source.indexOf("computePackedVarintsSize");
    }

    // ----- enum generation -----------------------------------------------------------------------

    @Test
    void shouldGenerateEnum() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |enum Status \{
                                              |  UNKNOWN = 0;
                                              |  ACTIVE = 1;
                                              |  INACTIVE = 2;
                                              |}
                                             );
        assert files.size == 1;
        assert String source := files.get("Status.x");
        assert source.indexOf("enum Status");
        assert source.indexOf("implements protobuf.ProtoEnum");
        assert source.indexOf("Unknown(0)");
        assert source.indexOf("Active(1)");
        assert source.indexOf("Inactive(2)");
        assert source.indexOf("Int protoValue");
    }

    // ----- map fields ----------------------------------------------------------------------------

    @Test
    void shouldGenerateMapField() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Config \{
                                              |  map<string, int32> labels = 1;
                                              |}
                                             );
        assert String source := files.get("Config.x");
        assert source.indexOf("Map<String, Int32> labels = Map:[]");
        assert source.indexOf("readMapStringInt32()");
        assert source.indexOf("writeMapStringInt32");
        assert source.indexOf("computeMapStringInt32Size");
    }

    // ----- oneof ---------------------------------------------------------------------------------

    @Test
    void shouldGenerateOneof() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Result \{
                                              |  int32 id = 1;
                                              |  oneof value \{
                                              |    string name = 2;
                                              |    int32 code = 3;
                                              |  }
                                              |}
                                             );
        assert String source := files.get("Result.x");

        // oneof typedef
        assert source.indexOf("typedef String | Int32 as ValueType");

        // oneof field declaration
        assert source.indexOf("ValueType? value = Null");

        // parseField assigns directly
        assert source.indexOf("value = input.readString()");
        assert source.indexOf("value = input.readInt32()");

        // writeKnownFields uses is checks
        assert source.indexOf("value.is(String)");
        assert source.indexOf("value.is(Int32)");
    }

    // ----- nested message ------------------------------------------------------------------------

    @Test
    void shouldGenerateNestedMessage() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Outer \{
                                              |  message Inner \{
                                              |    int32 value = 1;
                                              |  }
                                              |  Inner inner = 1;
                                              |}
                                             );
        assert files.size == 1;  // only one file for the top-level message
        assert String source := files.get("Outer.x");

        // nested message as static class
        assert source.indexOf("static class Inner");

        // field uses nullable type
        assert source.indexOf("Inner? inner = Null");
    }

    // ----- service generation --------------------------------------------------------------------

    @Test
    void shouldGenerateService() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Request \{}
                                              |message Response \{}
                                              |service Greeter \{
                                              |  rpc SayHello (Request) returns (Response);
                                              |}
                                             );
        assert String source := files.get("Greeter.x");
        assert source.indexOf("interface Greeter");
        assert source.indexOf("Response sayHello(Request request)");
    }

    @Test
    void shouldGenerateStreamingServiceAsTodo() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Req \{}
                                              |message Res \{}
                                              |service S \{
                                              |  rpc BiDi (stream Req) returns (stream Res);
                                              |}
                                             );
        assert String source := files.get("S.x");
        assert source.indexOf("TODO: streaming RPC");
    }

    // ----- nested enum ---------------------------------------------------------------------------

    @Test
    void shouldGenerateNestedEnum() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  enum Color \{
                                              |    RED = 0;
                                              |    GREEN = 1;
                                              |  }
                                              |  Color color = 1;
                                              |}
                                             );
        assert String source := files.get("Msg.x");
        assert source.indexOf("enum Color");
        assert source.indexOf("Red(0)");
        assert source.indexOf("Green(1)");
    }

    // ----- enum field parsing --------------------------------------------------------------------

    @Test
    void shouldGenerateEnumFieldParsing() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  enum Color \{
                                              |    RED = 0;
                                              |    GREEN = 1;
                                              |  }
                                              |  Color color = 1;
                                              |}
                                             );
        assert String source := files.get("Msg.x");

        // parseField should use ProtoEnum.byProtoValue, not raw readEnum
        assert source.indexOf("protobuf.ProtoEnum.byProtoValue(Color.values, input.readEnum())");
        assert source.indexOf("color = v");
    }

    @Test
    void shouldGenerateRepeatedEnumField() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  enum Color \{
                                              |    RED = 0;
                                              |    GREEN = 1;
                                              |  }
                                              |  repeated Color colors = 1;
                                              |}
                                             );
        assert String source := files.get("Msg.x");

        // field declaration should use Color[]
        assert source.indexOf("Color[] colors");

        // parseField should convert packed varints to enum values
        assert source.indexOf("protobuf.ProtoEnum.byProtoValue(Color.values");

        // writeKnownFields should use writeEnum per element
        assert source.indexOf("out.writeEnum(1, v)");

        // knownFieldsSize should use computeEnumSize per element
        assert source.indexOf("computeEnumSize(1, v)");
    }

    // ----- message field -------------------------------------------------------------------------

    @Test
    void shouldGenerateMessageField() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Inner \{
                                              |  int32 value = 1;
                                              |}
                                              |message Outer \{
                                              |  Inner nested = 1;
                                              |}
                                             );
        assert String source := files.get("Outer.x");
        assert source.indexOf("Inner? nested = Null");
        assert source.indexOf("nested != Null");
        assert source.indexOf("writeMessage(1, nested)");
    }

    // ----- copy constructor ----------------------------------------------------------------------

    @Test
    void shouldGenerateCopyConstructor() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  string name = 1;
                                              |  repeated int32 ids = 2;
                                              |}
                                             );
        assert String source := files.get("Msg.x");
        assert source.indexOf("construct(Msg other)");
        assert source.indexOf("construct protobuf.AbstractMessage(other)");
        assert source.indexOf("name = other.name");
        assert source.indexOf("ids = other.ids.duplicate()");
    }

    // ----- multiple top-level types --------------------------------------------------------------

    @Test
    void shouldGenerateMultipleFiles() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |enum Status \{
                                              |  UNKNOWN = 0;
                                              |}
                                              |message Person \{
                                              |  string name = 1;
                                              |}
                                              |service PersonService \{
                                              |  rpc Get (Person) returns (Person);
                                              |}
                                             );
        assert files.size == 3;
        assert files.get("Status.x");
        assert files.get("Person.x");
        assert files.get("PersonService.x");
    }

    // ----- keyword escaping --------------------------------------------------------------------

    @Test
    void shouldEscapeKeywordFieldNames() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  string class = 1;
                                              |  int32 import = 2;
                                              |}
                                             );
        assert String source := files.get("Msg.x");

        // field names should have underscore appended (proto3 implicit presence: plain fields)
        assert source.indexOf("String class_ = \"\"");
        assert source.indexOf("Int32 import_ = 0");
    }

    // ----- empty message --------------------------------------------------------------------

    @Test
    void shouldGenerateEmptyMessage() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Empty \{}
                                             );
        assert String source := files.get("Empty.x");

        // parseField should just return False, no switch
        assert source.indexOf("return False;");
        assert !source.indexOf("switch (protobuf.WireType");
    }

    // ----- dotted type name as nullable ---------------------------------------------------------

    @Test
    void shouldGenerateNullableDottedTypeName() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  some.other.MyType ts = 1;
                                              |}
                                             );
        assert String source := files.get("Msg.x");

        // dotted message type should be nullable
        assert source.indexOf("some.other.MyType? ts = Null");
    }

    // ----- no Array import -------------------------------------------------------------------

    @Test
    void shouldNotImportArray() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  repeated string items = 1;
                                              |}
                                             );
        assert String source := files.get("Msg.x");

        // no explicit import of Array
        assert !source.indexOf("import ecstasy.collections.Array");
        // but the field should still use Array type
        assert source.indexOf("String[]");
    }

    // ----- nested message imports ---------------------------------------------------------------

    @Test
    void shouldImportTypesNeededByNestedMessage() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Outer \{
                                              |  message Inner \{
                                              |    int32 value = 1;
                                              |  }
                                              |  Inner inner = 1;
                                              |}
                                             );
        assert String source := files.get("Outer.x");

        // no Presence or Maybe imports needed
        assert !source.indexOf("import protobuf.Presence;");
        assert !source.indexOf("MaybeInt32");

        // nullable message field
        assert source.indexOf("Inner? inner = Null");
    }

    // ----- dotted enum reference ---------------------------------------------------------------

    @Test
    void shouldHandleDottedEnumReference() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Outer \{
                                              |  message Inner \{
                                              |    enum Status \{
                                              |      UNKNOWN = 0;
                                              |      ACTIVE = 1;
                                              |    }
                                              |  }
                                              |  Inner.Status status = 1;
                                              |}
                                             );
        assert String source := files.get("Outer.x");

        // parseField should use ProtoEnum.byProtoValue for enum fields
        assert source.indexOf("protobuf.ProtoEnum.byProtoValue(");
        assert source.indexOf("input.readEnum()");
        // parseField should not contain message-style mergeFrom for this enum field
        assert !source.indexOf("elem.mergeFrom");

        // writeKnownFields should use writeEnum, not writeMessage
        assert source.indexOf("writeEnum(1,");
        assert !source.indexOf("writeMessage(1,");

        // knownFieldsSize should use computeEnumSize, not computeMessageSize
        assert source.indexOf("computeEnumSize(1,");
    }

    // ----- compilation tests --------------------------------------------------------------------

    @Test
    void shouldCompileSimpleScalarMessage() {
        testCompile(generate($|syntax = "proto3";
                              |message Person \{
                              |  string name = 1;
                              |  int32 id = 2;
                              |  bool active = 3;
                              |}
                             ));
    }

    @Test
    void shouldCompileRepeatedFields() {
        testCompile(generate($|syntax = "proto3";
                              |message Msg \{
                              |  repeated string items = 1;
                              |  repeated int32 numbers = 2;
                              |  repeated bool flags = 3;
                              |}
                             ));
    }

    @Test
    void shouldCompileMapFields() {
        testCompile(generate($|syntax = "proto3";
                              |message Config \{
                              |  map<string, string> labels = 1;
                              |  map<string, int32> counts = 2;
                              |  map<int32, string> names = 3;
                              |}
                             ));
    }

    @Test
    void shouldCompileNestedEnum() {
        testCompile(generate($|syntax = "proto3";
                              |message Msg \{
                              |  enum Color \{
                              |    RED = 0;
                              |    GREEN = 1;
                              |    BLUE = 2;
                              |  }
                              |  Color color = 1;
                              |  repeated Color colors = 2;
                              |}
                             ));
    }

    @Test
    void shouldCompileNestedMessage() {
        testCompile(generate($|syntax = "proto3";
                              |message Outer \{
                              |  message Inner \{
                              |    string value = 1;
                              |  }
                              |  Inner inner = 1;
                              |  repeated Inner items = 2;
                              |}
                             ));
    }

    @Test
    void shouldCompileOneof() {
        testCompile(generate($|syntax = "proto3";
                              |message Result \{
                              |  int32 id = 1;
                              |  oneof value \{
                              |    string name = 2;
                              |    int32 code = 3;
                              |    bool flag = 4;
                              |  }
                              |}
                             ));
    }

    @Test
    void shouldCompileEmptyMessage() {
        testCompile(generate($|syntax = "proto3";
                              |message Empty \{}
                             ));
    }

    @Test
    void shouldCompileAllScalarTypes() {
        testCompile(generate($|syntax = "proto3";
                              |message AllTypes \{
                              |  double   f_double   = 1;
                              |  float    f_float    = 2;
                              |  int32    f_int32    = 3;
                              |  int64    f_int64    = 4;
                              |  uint32   f_uint32   = 5;
                              |  uint64   f_uint64   = 6;
                              |  sint32   f_sint32   = 7;
                              |  sint64   f_sint64   = 8;
                              |  fixed32  f_fixed32  = 9;
                              |  fixed64  f_fixed64  = 10;
                              |  sfixed32 f_sfixed32 = 11;
                              |  sfixed64 f_sfixed64 = 12;
                              |  bool     f_bool     = 13;
                              |  string   f_string   = 14;
                              |  bytes    f_bytes    = 15;
                              |}
                             ));
    }

    @Test
    void shouldCompileKeywordFieldNames() {
        testCompile(generate($|syntax = "proto3";
                              |message Msg \{
                              |  string class = 1;
                              |  int32 import = 2;
                              |  bool return = 3;
                              |}
                             ));
    }

    @Test
    void shouldCompileComplexMessage() {
        testCompile(generate($|syntax = "proto3";
                              |message Complex \{
                              |  enum Status \{
                              |    UNKNOWN = 0;
                              |    ACTIVE = 1;
                              |  }
                              |  message Address \{
                              |    string street = 1;
                              |    int32 zip = 2;
                              |  }
                              |  string name = 1;
                              |  int32 id = 2;
                              |  Status status = 3;
                              |  Address address = 4;
                              |  repeated string tags = 5;
                              |  map<string, string> metadata = 6;
                              |}
                             ));
    }

    // ----- proto2 explicit presence (regression) ---------------------------------------------------

    @Test
    void shouldGenerateProto2ExplicitPresence() {
        Map<String, String> files = generate($|syntax = "proto2";
                                              |message Person \{
                                              |  optional string name = 1;
                                              |  optional int32 id = 2;
                                              |}
                                             );
        assert String source := files.get("Person.x");

        // proto2: all singular fields have explicit presence
        assert source.indexOf("private Int presentBits_0 = 0");
        assert source.indexOf("private String _name");
        assert source.indexOf("private Int32 _id");
        assert source.indexOf("hasName()");
        assert source.indexOf("hasId()");
    }

    @Test
    void shouldCompileProto2Message() {
        testCompile(generate($|syntax = "proto2";
                              |message Person \{
                              |  optional string name = 1;
                              |  optional int32 id = 2;
                              |  optional bool active = 3;
                              |}
                             ));
    }

    // ----- proto3 explicit optional --------------------------------------------------------------

    @Test
    void shouldGenerateProto3ExplicitOptional() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Person \{
                                              |  optional string name = 1;
                                              |  int32 id = 2;
                                              |}
                                             );
        assert String source := files.get("Person.x");

        // 'name' has explicit presence (optional keyword)
        assert source.indexOf("private String _name");
        assert source.indexOf("hasName()");

        // 'id' has implicit presence (no optional keyword)
        assert source.indexOf("Int32 id = 0");
        assert !source.indexOf("private Int32 _id");
        assert !source.indexOf("hasId()");
    }

    @Test
    void shouldCompileProto3ExplicitOptional() {
        testCompile(generate($|syntax = "proto3";
                              |message Mixed \{
                              |  string name = 1;
                              |  optional string nickname = 2;
                              |  int32 age = 3;
                              |  optional int32 score = 4;
                              |}
                             ));
    }

    // ----- editions field presence ---------------------------------------------------------------

    @Test
    void shouldGenerateEditionsFieldPresence() {
        import protobuf.wellknown.FieldOptions;
        import protobuf.wellknown.FeatureSet;
        import protobuf.wellknown.FeatureSet.FieldPresence;
        import protobuf.wellknown.FieldDescriptorProto;
        import protobuf.wellknown.FieldDescriptorProto.Type as FieldType;
        import protobuf.wellknown.FieldDescriptorProto.Label;
        import protobuf.wellknown.DescriptorProto;
        import protobuf.wellknown.FileDescriptorProto;
        import protobuf.wellknown.Edition;
        import protobuf.ProtoCodeGen;

        // field with explicit presence
        FieldDescriptorProto explicitField = new FieldDescriptorProto();
        explicitField.name   = "name";
        explicitField.number = 1;
        explicitField.type   = TypeString;
        explicitField.label  = LabelOptional;
        FieldOptions explicitOpts = new FieldOptions();
        explicitOpts.features = new FeatureSet(fieldPresence=Explicit);
        explicitField.options = explicitOpts;

        // field with implicit presence
        FieldDescriptorProto implicitField = new FieldDescriptorProto();
        implicitField.name   = "tag";
        implicitField.number = 2;
        implicitField.type   = TypeString;
        implicitField.label  = LabelOptional;
        FieldOptions implicitOpts = new FieldOptions();
        implicitOpts.features = new FeatureSet(fieldPresence=Implicit);
        implicitField.options = implicitOpts;

        DescriptorProto msg = new DescriptorProto();
        msg.name  = "EditionsTest";
        msg.field = [explicitField, implicitField];

        FileDescriptorProto file = new FileDescriptorProto();
        file.name        = "test.proto";
        file.syntax      = "editions";
        file.edition     = Edition2023;
        file.messageType = [msg];

        Map<String, String> files = new ProtoCodeGen().generate(file);
        assert String source := files.get("EditionsTest.x");

        // explicit field has presence tracking
        assert source.indexOf("private String _name");
        assert source.indexOf("hasName()");

        // implicit field does not
        assert source.indexOf("String tag = \"\"");
        assert !source.indexOf("hasTag()");
    }

    // ----- helper --------------------------------------------------------------------------------

    private Map<String, String> generate(String protoSource) {
        FileDescriptorProto file = new ProtoParser(protoSource).parseFile("test.proto");
        return new ProtoCodeGen().generate(file);
    }

    /**
     * Test that the generated source code compiles successfully.
     *
     * @param sources  a map of source file names to source code
     */
    private void testCompile(Map<String, String> sources) {
        @Inject("testOutput") Directory testOutput;

        String moduleSource = $|module testModule \{
                               |
                               |    package protobuf import protobuf.xtclang.org;
                               |}
                               |
                               ;

        File moduleFile = testOutput.fileFor("testModule.x");
        moduleFile.contents = moduleSource.utf8();

        Directory packageDir = testOutput.dirFor("testModule").ensure();

        for (Map.Entry<String, String> entry : sources.entries) {
            String sourceFileName = entry.key;
            String source         = entry.value;
            if (!sourceFileName.endsWith(".x")) {
                sourceFileName += ".x";
            }
            File sourceFile = packageDir.fileFor(sourceFileName);
            sourceFile.contents = source.utf8();
        }

        @Inject Compiler         compiler;
        @Inject ModuleRepository repository;
        compiler.setLibraryRepository(repository);
        compiler.setResultLocation(testOutput);

        (Boolean success, String[] errors) = compiler.compile([moduleFile]);
        assert success as $"compilation failed:\n{errors}";
    }
}
