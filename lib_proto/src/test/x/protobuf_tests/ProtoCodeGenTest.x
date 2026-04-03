import protobuf.FileDescriptor;
import protobuf.ProtoCodeGen;
import protobuf.ProtoParser;

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

        // imports for Maybe types
        assert source.indexOf("import protobuf.MaybeString;");
        assert source.indexOf("import protobuf.MaybeInt32;");
        assert source.indexOf("import protobuf.MaybeBoolean;");

        // class declaration
        assert source.indexOf("class Person");
        assert source.indexOf("extends AbstractMessage");

        // fields with Maybe types
        assert source.indexOf("MaybeString name = Unset");
        assert source.indexOf("MaybeInt32 id = Unset");
        assert source.indexOf("MaybeBoolean active = Unset");

        // parseField
        assert source.indexOf("input.readString()");
        assert source.indexOf("input.readInt32()");
        assert source.indexOf("input.readBool()");

        // writeKnownFields with .is() checks
        assert source.indexOf("name.is(String)");
        assert source.indexOf("id.is(Int32)");
        assert source.indexOf("active.is(Boolean)");

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
        assert source.indexOf("Array<String> items = new Array()");
        // repeated int32 uses packed encoding
        assert source.indexOf("Array<Int32> numbers = new Array()");
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
        assert source.indexOf("implements ProtoEnum");
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
        assert source.indexOf("ListMap<String, Int32> labels = new ListMap()");
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

        // oneof field
        assert source.indexOf("Oneof value = new Oneof()");

        // typed accessors
        assert source.indexOf("conditional String getName()");
        assert source.indexOf("void setName(String value)");
        assert source.indexOf("conditional Int32 getCode()");
        assert source.indexOf("void setCode(Int32 value)");

        // parseField uses oneof.set
        assert source.indexOf("value.set(2, input.readString())");
        assert source.indexOf("value.set(3, input.readInt32())");

        // writeKnownFields uses switch
        assert source.indexOf("switch (value.activeFieldNumber)");
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

        // typedef for inner message presence
        assert source.indexOf("typedef Presence | Inner as MaybeInner");

        // field uses Maybe type
        assert source.indexOf("MaybeInner inner = Unset");
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
        assert source.indexOf("ProtoEnum.byProtoValue(Color.values, input.readEnum())");
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

        // field declaration should use Array<Color>
        assert source.indexOf("Array<Color> colors");

        // parseField should convert packed varints to enum values
        assert source.indexOf("ProtoEnum.byProtoValue(Color.values");

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
        assert source.indexOf("import protobuf.Presence;");
        assert source.indexOf("typedef Presence | Inner as MaybeInner");
        assert source.indexOf("MaybeInner nested = Unset");
        assert source.indexOf("nested.is(Inner)");
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
        assert source.indexOf("construct AbstractMessage(other)");
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

        // field names should have underscore appended
        assert source.indexOf("MaybeString class_ = Unset");
        assert source.indexOf("MaybeInt32 import_ = Unset");
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
        assert !source.indexOf("switch (WireType");
    }

    // ----- dotted type name in typedef -------------------------------------------------------

    @Test
    void shouldSanitizeDottedTypedefName() {
        Map<String, String> files = generate($|syntax = "proto3";
                                              |message Msg \{
                                              |  google.protobuf.Timestamp ts = 1;
                                              |}
                                             );
        assert String source := files.get("Msg.x");

        // typedef alias should use underscores instead of dots
        assert source.indexOf("as Maybegoogle_protobuf_Timestamp");
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

        // Array is implicitly imported in Ecstasy
        assert !source.indexOf("ecstasy.collections.Array");
        // but the field should still use Array type
        assert source.indexOf("Array<String>");
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

        // Presence is needed for the Outer.inner typedef
        assert source.indexOf("import protobuf.Presence;");
        // MaybeInt32 is needed by the nested Inner message
        assert source.indexOf("import protobuf.MaybeInt32;");
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

        // parseField should use ProtoEnum.byProtoValue, not mergeFrom
        assert source.indexOf("ProtoEnum.byProtoValue(");
        assert source.indexOf("input.readEnum()");
        assert !source.indexOf("mergeFrom");

        // writeKnownFields should use writeEnum, not writeMessage
        assert source.indexOf("writeEnum(1,");
        assert !source.indexOf("writeMessage(1,");

        // knownFieldsSize should use computeEnumSize, not computeMessageSize
        assert source.indexOf("computeEnumSize(1,");
    }

    // ----- helper --------------------------------------------------------------------------------

    private Map<String, String> generate(String protoSource) {
        FileDescriptor file = new ProtoParser(protoSource).parseFile("test.proto");
        return new ProtoCodeGen().generate(file);
    }
}
