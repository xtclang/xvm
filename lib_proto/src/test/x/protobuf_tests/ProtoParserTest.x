import protobuf.EnumDescriptor;
import protobuf.EnumValueDescriptor;
import protobuf.FieldDescriptor;
import protobuf.FieldType;
import protobuf.FileDescriptor;
import protobuf.MessageDescriptor;
import protobuf.MethodDescriptor;
import protobuf.OneofDescriptor;
import protobuf.ProtoParser;
import protobuf.ServiceDescriptor;

class ProtoParserTest {

    // ----- minimal file --------------------------------------------------------------------------

    @Test
    void shouldParseMinimalProto3() {
        FileDescriptor file = parse("syntax = \"proto3\";");
        assert file.syntax == "proto3";
        assert file.messages.size == 0;
    }

    @Test
    void shouldParseMinimalProto2() {
        FileDescriptor file = parse("syntax = \"proto2\";");
        assert file.syntax == "proto2";
    }

    // ----- package -------------------------------------------------------------------------------

    @Test
    void shouldParsePackage() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |package foo.bar;
                                    );
        assert file.packageName == "foo.bar";
    }

    // ----- imports -------------------------------------------------------------------------------

    @Test
    void shouldParseImports() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |import "other.proto";
                                     |import public "public.proto";
                                    );
        assert file.dependencies.size == 2;
        assert file.dependencies[0] == "other.proto";
        assert file.dependencies[1] == "public.proto";
        assert file.publicDependencies.size == 1;
        assert file.publicDependencies[0] == "public.proto";
    }

    // ----- options -------------------------------------------------------------------------------

    @Test
    void shouldParseFileOption() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |option java_package = "com.example";
                                    );
        assert String pkg := file.options.get("java_package");
        assert pkg == "com.example";
    }

    // ----- simple message ------------------------------------------------------------------------

    @Test
    void shouldParseSimpleMessage() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Person \{
                                     |  string name = 1;
                                     |  int32 id = 2;
                                     |}
                                    );
        assert file.messages.size == 1;
        MessageDescriptor msg = file.messages[0];
        assert msg.name == "Person";
        assert msg.fields.size == 2;

        FieldDescriptor name = msg.fields[0];
        assert name.name == "name";
        assert name.number == 1;
        assert name.type == TypeString;

        FieldDescriptor id = msg.fields[1];
        assert id.name == "id";
        assert id.number == 2;
        assert id.type == TypeInt32;
    }

    // ----- repeated fields -----------------------------------------------------------------------

    @Test
    void shouldParseRepeatedField() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message List \{
                                     |  repeated string items = 1;
                                     |}
                                    );
        FieldDescriptor field = file.messages[0].fields[0];
        assert field.isRepeated;
        assert field.type == TypeString;
    }

    // ----- nested messages -----------------------------------------------------------------------

    @Test
    void shouldParseNestedMessage() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Outer \{
                                     |  message Inner \{
                                     |    int32 value = 1;
                                     |  }
                                     |  Inner inner = 1;
                                     |}
                                    );
        MessageDescriptor outer = file.messages[0];
        assert outer.name == "Outer";
        assert outer.nestedMessages.size == 1;
        assert outer.nestedMessages[0].name == "Inner";
        assert outer.fields.size == 1;
        assert outer.fields[0].typeName == "Inner";
    }

    // ----- enum ----------------------------------------------------------------------------------

    @Test
    void shouldParseEnum() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |enum Status \{
                                     |  UNKNOWN = 0;
                                     |  ACTIVE = 1;
                                     |  INACTIVE = 2;
                                     |}
                                    );
        assert file.enums.size == 1;
        EnumDescriptor e = file.enums[0];
        assert e.name == "Status";
        assert e.values.size == 3;
        assert e.values[0].name == "UNKNOWN";
        assert e.values[0].number == 0;
        assert e.values[1].name == "ACTIVE";
        assert e.values[1].number == 1;
    }

    @Test
    void shouldParseEnumWithNegativeValue() {
        FileDescriptor file = parse($|syntax = "proto2";
                                     |enum Signed \{
                                     |  NEG = -1;
                                     |  ZERO = 0;
                                     |}
                                    );
        EnumDescriptor e = file.enums[0];
        assert e.values[0].number == -1;
        assert e.values[1].number == 0;
    }

    // ----- map fields ----------------------------------------------------------------------------

    @Test
    void shouldParseMapField() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Config \{
                                     |  map<string, int32> labels = 1;
                                     |}
                                    );
        FieldDescriptor field = file.messages[0].fields[0];
        assert field.name == "labels";
        assert field.isMapField;
        assert field.mapKeyType == TypeString;
        assert field.mapValueType == TypeInt32;
    }

    // ----- oneof ---------------------------------------------------------------------------------

    @Test
    void shouldParseOneof() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Result \{
                                     |  oneof value \{
                                     |    string name = 1;
                                     |    int32 code = 2;
                                     |  }
                                     |}
                                    );
        MessageDescriptor msg = file.messages[0];
        assert msg.oneofs.size == 1;
        assert msg.oneofs[0].name == "value";
        assert msg.oneofs[0].fieldNumbers.size == 2;
        assert msg.fields.size == 2;
        assert msg.fields[0].oneofIndex == 0;
        assert msg.fields[1].oneofIndex == 0;
    }

    // ----- reserved ------------------------------------------------------------------------------

    @Test
    void shouldParseReservedNumbers() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Msg \{
                                     |  reserved 2, 15, 9 to 11;
                                     |}
                                    );
        MessageDescriptor msg = file.messages[0];
        assert msg.reservedRanges.size == 3;
        assert msg.reservedRanges[0].start == 2;
        assert msg.reservedRanges[1].start == 15;
        assert msg.reservedRanges[2].start == 9;
        assert msg.reservedRanges[2].endExclusive == 12;
    }

    @Test
    void shouldParseReservedNames() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Msg \{
                                     |  reserved "foo", "bar";
                                     |}
                                    );
        MessageDescriptor msg = file.messages[0];
        assert msg.reservedNames.size == 2;
        assert msg.reservedNames[0] == "foo";
        assert msg.reservedNames[1] == "bar";
    }

    // ----- service -------------------------------------------------------------------------------

    @Test
    void shouldParseService() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Request \{}
                                     |message Response \{}
                                     |service Greeter \{
                                     |  rpc SayHello (Request) returns (Response);
                                     |}
                                    );
        assert file.services.size == 1;
        ServiceDescriptor svc = file.services[0];
        assert svc.name == "Greeter";
        assert svc.methods.size == 1;

        MethodDescriptor method = svc.methods[0];
        assert method.name == "SayHello";
        assert method.inputType == "Request";
        assert method.outputType == "Response";
        assert !method.clientStreaming;
        assert !method.serverStreaming;
    }

    @Test
    void shouldParseStreamingRpc() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Req \{}
                                     |message Res \{}
                                     |service S \{
                                     |  rpc BiDi (stream Req) returns (stream Res);
                                     |}
                                    );
        MethodDescriptor method = file.services[0].methods[0];
        assert method.clientStreaming;
        assert method.serverStreaming;
    }

    // ----- field options -------------------------------------------------------------------------

    @Test
    void shouldParseFieldOptions() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Msg \{
                                     |  repeated int32 ids = 1 [packed = true];
                                     |}
                                    );
        FieldDescriptor field = file.messages[0].fields[0];
        assert String packed := field.options.get("packed");
        assert packed == "true";
    }

    // ----- comments in proto ---------------------------------------------------------------------

    @Test
    void shouldHandleComments() {
        FileDescriptor file = parse($|// file-level comment
                                     |syntax = "proto3";
                                     |/* block comment */
                                     |message Foo \{
                                     |  // field comment
                                     |  int32 bar = 1;
                                     |}
                                    );
        assert file.messages.size == 1;
        assert file.messages[0].fields.size == 1;
    }

    // ----- complex proto -------------------------------------------------------------------------

    @Test
    void shouldParseComplexProto() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |package example.v1;
                                     |
                                     |import "google/protobuf/timestamp.proto";
                                     |
                                     |option java_package = "com.example.v1";
                                     |
                                     |enum Color \{
                                     |  RED = 0;
                                     |  GREEN = 1;
                                     |  BLUE = 2;
                                     |}
                                     |
                                     |message Person \{
                                     |  string name = 1;
                                     |  int32 id = 2;
                                     |  repeated string emails = 3;
                                     |  Color favorite_color = 4;
                                     |  map<string, string> attributes = 5;
                                     |
                                     |  message Address \{
                                     |    string street = 1;
                                     |    string city = 2;
                                     |  }
                                     |
                                     |  repeated Address addresses = 6;
                                     |
                                     |  oneof contact \{
                                     |    string phone = 7;
                                     |    string email = 8;
                                     |  }
                                     |
                                     |  reserved 10, 12 to 15;
                                     |  reserved "old_field";
                                     |}
                                     |
                                     |service PersonService \{
                                     |  rpc GetPerson (Person) returns (Person);
                                     |  rpc ListPeople (Person) returns (stream Person);
                                     |}
                                    );

        assert file.packageName == "example.v1";
        assert file.dependencies.size == 1;
        assert file.enums.size == 1;
        assert file.messages.size == 1;
        assert file.services.size == 1;

        MessageDescriptor person = file.messages[0];
        assert person.name == "Person";
        assert person.fields.size == 8;
        assert person.nestedMessages.size == 1;
        assert person.oneofs.size == 1;
        assert person.reservedRanges.size == 2;
        assert person.reservedNames.size == 1;

        ServiceDescriptor svc = file.services[0];
        assert svc.methods.size == 2;
        assert svc.methods[1].serverStreaming;
    }

    // ----- message type references ---------------------------------------------------------------

    @Test
    void shouldParseMessageTypeReference() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Inner \{}
                                     |message Outer \{
                                     |  Inner nested = 1;
                                     |}
                                    );
        FieldDescriptor field = file.messages[1].fields[0];
        assert field.type == TypeMessage;
        assert field.typeName == "Inner";
    }

    @Test
    void shouldParseDottedTypeReference() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message Outer \{
                                     |  google.protobuf.Timestamp created = 1;
                                     |}
                                    );
        FieldDescriptor field = file.messages[0].fields[0];
        assert field.type == TypeMessage;
        assert field.typeName == "google.protobuf.Timestamp";
    }

    // ----- all scalar types ----------------------------------------------------------------------

    @Test
    void shouldParseAllScalarTypes() {
        FileDescriptor file = parse($|syntax = "proto3";
                                     |message AllTypes \{
                                     |  double f1 = 1;
                                     |  float f2 = 2;
                                     |  int32 f3 = 3;
                                     |  int64 f4 = 4;
                                     |  uint32 f5 = 5;
                                     |  uint64 f6 = 6;
                                     |  sint32 f7 = 7;
                                     |  sint64 f8 = 8;
                                     |  fixed32 f9 = 9;
                                     |  fixed64 f10 = 10;
                                     |  sfixed32 f11 = 11;
                                     |  sfixed64 f12 = 12;
                                     |  bool f13 = 13;
                                     |  string f14 = 14;
                                     |  bytes f15 = 15;
                                     |}
                                    );
        MessageDescriptor msg = file.messages[0];
        assert msg.fields.size == 15;
        assert msg.fields[0].type == TypeDouble;
        assert msg.fields[1].type == TypeFloat;
        assert msg.fields[2].type == TypeInt32;
        assert msg.fields[3].type == TypeInt64;
        assert msg.fields[4].type == TypeUint32;
        assert msg.fields[5].type == TypeUint64;
        assert msg.fields[6].type == TypeSint32;
        assert msg.fields[7].type == TypeSint64;
        assert msg.fields[8].type == TypeFixed32;
        assert msg.fields[9].type == TypeFixed64;
        assert msg.fields[10].type == TypeSfixed32;
        assert msg.fields[11].type == TypeSfixed64;
        assert msg.fields[12].type == TypeBool;
        assert msg.fields[13].type == TypeString;
        assert msg.fields[14].type == TypeBytes;
    }

    // ----- helper --------------------------------------------------------------------------------

    private FileDescriptor parse(String source) =
        new ProtoParser(source).parseFile("test.proto");
}
