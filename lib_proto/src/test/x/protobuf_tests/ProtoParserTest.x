import protobuf.ProtoParser;
import protobuf.wellknown.DescriptorProto;
import protobuf.wellknown.EnumDescriptorProto;
import protobuf.wellknown.EnumValueDescriptorProto;
import protobuf.wellknown.FieldDescriptorProto;
import protobuf.wellknown.FieldDescriptorProto.Label;
import protobuf.wellknown.FieldDescriptorProto.Type as FieldType;
import protobuf.wellknown.FileDescriptorProto;
import protobuf.wellknown.MethodDescriptorProto;
import protobuf.wellknown.OneofDescriptorProto;
import protobuf.wellknown.ServiceDescriptorProto;

class ProtoParserTest {

    // ----- minimal file --------------------------------------------------------------------------

    @Test
    void shouldParseMinimalProto3() {
        FileDescriptorProto file = parse("syntax = \"proto3\";");
        assert file.syntax == "proto3";
        assert file.messageType.size == 0;
    }

    @Test
    void shouldParseMinimalProto2() {
        FileDescriptorProto file = parse("syntax = \"proto2\";");
        assert file.syntax == "proto2";
    }

    // ----- package -------------------------------------------------------------------------------

    @Test
    void shouldParsePackage() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |package foo.bar;
                                         );
        assert file.package_ == "foo.bar";
    }

    // ----- imports -------------------------------------------------------------------------------

    @Test
    void shouldParseImports() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |import "other.proto";
                                          |import public "public.proto";
                                         );
        assert file.dependency.size == 2;
        assert file.dependency[0] == "other.proto";
        assert file.dependency[1] == "public.proto";
        assert file.publicDependency.size == 1;
        assert file.publicDependency[0] == 1;
    }

    // ----- simple message ------------------------------------------------------------------------

    @Test
    void shouldParseSimpleMessage() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Person \{
                                          |  string name = 1;
                                          |  int32 id = 2;
                                          |}
                                         );
        assert file.messageType.size == 1;
        DescriptorProto msg = file.messageType[0];
        assert msg.name == "Person";
        assert msg.field.size == 2;

        FieldDescriptorProto name = msg.field[0];
        assert name.name == "name";
        assert name.number == 1;
        assert name.type == TypeString;

        FieldDescriptorProto id = msg.field[1];
        assert id.name == "id";
        assert id.number == 2;
        assert id.type == TypeInt32;
    }

    // ----- repeated fields -----------------------------------------------------------------------

    @Test
    void shouldParseRepeatedField() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message List \{
                                          |  repeated string items = 1;
                                          |}
                                         );
        FieldDescriptorProto field = file.messageType[0].field[0];
        assert field.label == LabelRepeated;
        assert field.type == TypeString;
    }

    // ----- nested messages -----------------------------------------------------------------------

    @Test
    void shouldParseNestedMessage() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Outer \{
                                          |  message Inner \{
                                          |    int32 value = 1;
                                          |  }
                                          |  Inner inner = 1;
                                          |}
                                         );
        DescriptorProto outer = file.messageType[0];
        assert outer.name == "Outer";
        assert outer.nestedType.size == 1;
        assert outer.nestedType[0].name == "Inner";
        assert outer.field.size == 1;
        assert outer.field[0].typeName == "Inner";
    }

    // ----- enum ----------------------------------------------------------------------------------

    @Test
    void shouldParseEnum() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |enum Status \{
                                          |  UNKNOWN = 0;
                                          |  ACTIVE = 1;
                                          |  INACTIVE = 2;
                                          |}
                                         );
        assert file.enumType.size == 1;
        EnumDescriptorProto e = file.enumType[0];
        assert e.name == "Status";
        assert e.value.size == 3;
        assert e.value[0].name == "UNKNOWN";
        assert e.value[0].number == 0;
        assert e.value[1].name == "ACTIVE";
        assert e.value[1].number == 1;
    }

    @Test
    void shouldParseEnumWithNegativeValue() {
        FileDescriptorProto file = parse($|syntax = "proto2";
                                          |enum Signed \{
                                          |  NEG = -1;
                                          |  ZERO = 0;
                                          |}
                                         );
        EnumDescriptorProto e = file.enumType[0];
        assert e.value[0].number == -1;
        assert e.value[1].number == 0;
    }

    // ----- map fields ----------------------------------------------------------------------------

    @Test
    void shouldParseMapField() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Config \{
                                          |  map<string, int32> labels = 1;
                                          |}
                                         );
        DescriptorProto msg = file.messageType[0];
        // map field is represented as a repeated field with a synthetic entry message
        FieldDescriptorProto field = msg.field[0];
        assert field.name == "labels";
        assert field.label == LabelRepeated;
        assert field.type == TypeMessage;
        assert field.typeName == "LabelsEntry";
        // synthetic entry message
        assert msg.nestedType.size == 1;
        DescriptorProto entry = msg.nestedType[0];
        assert entry.name == "LabelsEntry";
        assert entry.field.size == 2;
        assert entry.field[0].name == "key";
        assert entry.field[0].type == TypeString;
        assert entry.field[1].name == "value";
        assert entry.field[1].type == TypeInt32;
    }

    // ----- oneof ---------------------------------------------------------------------------------

    @Test
    void shouldParseOneof() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Result \{
                                          |  oneof value \{
                                          |    string name = 1;
                                          |    int32 code = 2;
                                          |  }
                                          |}
                                         );
        DescriptorProto msg = file.messageType[0];
        assert msg.oneofDecl.size == 1;
        assert msg.oneofDecl[0].name == "value";
        assert msg.field.size == 2;
        assert msg.field[0].oneofIndex == 0;
        assert msg.field[1].oneofIndex == 0;
    }

    // ----- reserved ------------------------------------------------------------------------------

    @Test
    void shouldParseReservedNumbers() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Msg \{
                                          |  reserved 2, 15, 9 to 11;
                                          |}
                                         );
        DescriptorProto msg = file.messageType[0];
        assert msg.reservedRange.size == 3;
        assert msg.reservedRange[0].start == 2;
        assert msg.reservedRange[1].start == 15;
        assert msg.reservedRange[2].start == 9;
        assert msg.reservedRange[2].end == 12;
    }

    @Test
    void shouldParseReservedNames() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Msg \{
                                          |  reserved "foo", "bar";
                                          |}
                                         );
        DescriptorProto msg = file.messageType[0];
        assert msg.reservedName.size == 2;
        assert msg.reservedName[0] == "foo";
        assert msg.reservedName[1] == "bar";
    }

    // ----- service -------------------------------------------------------------------------------

    @Test
    void shouldParseService() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Request \{}
                                          |message Response \{}
                                          |service Greeter \{
                                          |  rpc SayHello (Request) returns (Response);
                                          |}
                                         );
        assert file.service_.size == 1;
        ServiceDescriptorProto svc = file.service_[0];
        assert svc.name == "Greeter";
        assert svc.method.size == 1;

        MethodDescriptorProto method = svc.method[0];
        assert method.name == "SayHello";
        assert method.inputType == "Request";
        assert method.outputType == "Response";
        assert !method.clientStreaming;
        assert !method.serverStreaming;
    }

    @Test
    void shouldParseStreamingRpc() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Req \{}
                                          |message Res \{}
                                          |service S \{
                                          |  rpc BiDi (stream Req) returns (stream Res);
                                          |}
                                         );
        MethodDescriptorProto method = file.service_[0].method[0];
        assert method.clientStreaming;
        assert method.serverStreaming;
    }

    // ----- comments in proto ---------------------------------------------------------------------

    @Test
    void shouldHandleComments() {
        FileDescriptorProto file = parse($|// file-level comment
                                          |syntax = "proto3";
                                          |/* block comment */
                                          |message Foo \{
                                          |  // field comment
                                          |  int32 bar = 1;
                                          |}
                                         );
        assert file.messageType.size == 1;
        assert file.messageType[0].field.size == 1;
    }

    // ----- complex proto -------------------------------------------------------------------------

    @Test
    void shouldParseComplexProto() {
        FileDescriptorProto file = parse($|syntax = "proto3";
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

        assert file.package_ == "example.v1";
        assert file.dependency.size == 1;
        assert file.enumType.size == 1;
        assert file.messageType.size == 1;
        assert file.service_.size == 1;

        DescriptorProto person = file.messageType[0];
        assert person.name == "Person";
        // 8 user fields + map field is the repeated entry ref
        assert person.field.size == 8;
        // Address + AttributesEntry (synthetic map entry)
        assert person.nestedType.size == 2;
        assert person.oneofDecl.size == 1;
        assert person.reservedRange.size == 2;
        assert person.reservedName.size == 1;

        ServiceDescriptorProto svc = file.service_[0];
        assert svc.method.size == 2;
        assert svc.method[1].serverStreaming;
    }

    // ----- message type references ---------------------------------------------------------------

    @Test
    void shouldParseMessageTypeReference() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Inner \{}
                                          |message Outer \{
                                          |  Inner nested = 1;
                                          |}
                                         );
        FieldDescriptorProto field = file.messageType[1].field[0];
        assert field.type == TypeMessage;
        assert field.typeName == "Inner";
    }

    @Test
    void shouldParseDottedTypeReference() {
        FileDescriptorProto file = parse($|syntax = "proto3";
                                          |message Outer \{
                                          |  google.protobuf.Timestamp created = 1;
                                          |}
                                         );
        FieldDescriptorProto field = file.messageType[0].field[0];
        assert field.type == TypeMessage;
        assert field.typeName == "google.protobuf.Timestamp";
    }

    // ----- all scalar types ----------------------------------------------------------------------

    @Test
    void shouldParseAllScalarTypes() {
        FileDescriptorProto file = parse($|syntax = "proto3";
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
        DescriptorProto msg = file.messageType[0];
        assert msg.field.size == 15;
        assert msg.field[0].type == TypeDouble;
        assert msg.field[1].type == TypeFloat;
        assert msg.field[2].type == TypeInt32;
        assert msg.field[3].type == TypeInt64;
        assert msg.field[4].type == TypeUint32;
        assert msg.field[5].type == TypeUint64;
        assert msg.field[6].type == TypeSint32;
        assert msg.field[7].type == TypeSint64;
        assert msg.field[8].type == TypeFixed32;
        assert msg.field[9].type == TypeFixed64;
        assert msg.field[10].type == TypeSfixed32;
        assert msg.field[11].type == TypeSfixed64;
        assert msg.field[12].type == TypeBool;
        assert msg.field[13].type == TypeString;
        assert msg.field[14].type == TypeBytes;
    }

    // ----- helper --------------------------------------------------------------------------------

    private FileDescriptorProto parse(String source) =
        new ProtoParser(source).parseFile("test.proto");
}
