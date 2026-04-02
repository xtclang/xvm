import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.AbstractMessage;
import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.Oneof;
import protobuf.WireType;

class OneofTest {

    /**
     * A test message with a oneof group, equivalent to:
     * ```proto
     * message TestOneof {
     *   int32 id = 1;
     *   oneof result {
     *     string name  = 2;
     *     int32  code  = 3;
     *     bool   flag  = 4;
     *   }
     * }
     * ```
     */
    static class TestOneof
            extends AbstractMessage {

        construct() {
            construct AbstractMessage();
        }

        @Override
        construct(TestOneof other) {
            construct AbstractMessage(other);
            id     = other.id;
            result = other.result.duplicate();
        }

        Int32 id = 0;
        Oneof result = new Oneof();

        // ----- typed accessors for the oneof fields ----------------------------------------------

        conditional String getName() {
            if (Object v := result.get(2)) {
                return True, v.as(String);
            }
            return False;
        }

        void setName(String value) {
            result.set(2, value);
        }

        conditional Int32 getCode() {
            if (Object v := result.get(3)) {
                return True, v.as(Int32);
            }
            return False;
        }

        void setCode(Int32 value) {
            result.set(3, value);
        }

        conditional Boolean getFlag() {
            if (Object v := result.get(4)) {
                return True, v.as(Boolean);
            }
            return False;
        }

        void setFlag(Boolean value) {
            result.set(4, value);
        }

        // ----- AbstractMessage overrides ---------------------------------------------------------

        @Override
        Boolean parseField(CodedInput input, Int tag) {
            switch (WireType.getFieldNumber(tag)) {
            case 1:
                id = input.readInt32();
                return True;
            case 2:
                result.set(2, input.readString());
                return True;
            case 3:
                result.set(3, input.readInt32());
                return True;
            case 4:
                result.set(4, input.readBool());
                return True;
            }
            return False;
        }

        @Override
        void writeKnownFields(CodedOutput out) {
            if (id != 0) {
                out.writeInt32(1, id);
            }
            switch (result.activeFieldNumber) {
            case 2:
                if (String name := getName()) {
                    out.writeString(2, name);
                }
                break;
            case 3:
                if (Int32 code := getCode()) {
                    out.writeInt32(3, code);
                }
                break;
            case 4:
                if (Boolean flag := getFlag()) {
                    out.writeBool(4, flag);
                }
                break;
            }
        }

        @Override
        Int knownFieldsSize() {
            Int size = 0;
            if (id != 0) {
                size += CodedOutput.computeInt32Size(1, id);
            }
            switch (result.activeFieldNumber) {
            case 2:
                if (String name := getName()) {
                    size += CodedOutput.computeStringSize(2, name);
                }
                break;
            case 3:
                if (Int32 code := getCode()) {
                    size += CodedOutput.computeInt32Size(3, code);
                }
                break;
            case 4:
                if (Boolean flag := getFlag()) {
                    size += CodedOutput.computeBoolSize(4);
                }
                break;
            }
            return size;
        }
    }

    // ----- Oneof class tests ---------------------------------------------------------------------

    @Test
    void shouldStartUnset() {
        Oneof o = new Oneof();
        assert !o.isSet();
        assert o.activeFieldNumber == 0;
    }

    @Test
    void shouldSetField() {
        Oneof o = new Oneof();
        o.set(2, "hello");
        assert o.isSet();
        assert o.activeFieldNumber == 2;
        assert Object v := o.get(2);
        assert v.as(String) == "hello";
    }

    @Test
    void shouldClearPreviousOnSet() {
        Oneof o = new Oneof();
        o.set(2, "hello");
        Int32 val = 42;
        o.set(3, val);
        assert o.activeFieldNumber == 3;
        assert !o.get(2);
        assert Object v := o.get(3);
        assert v.as(Int32) == 42;
    }

    @Test
    void shouldClear() {
        Oneof o = new Oneof();
        o.set(2, "hello");
        o.clear();
        assert !o.isSet();
        assert !o.get(2);
    }

    @Test
    void shouldContains() {
        assert Oneof.contains(2, [2, 3, 4]);
        assert Oneof.contains(4, [2, 3, 4]);
        assert !Oneof.contains(1, [2, 3, 4]);
    }

    // ----- mutual exclusion ----------------------------------------------------------------------

    @Test
    void shouldOnlyHaveOneFieldSet() {
        TestOneof msg = new TestOneof();
        msg.setName("Alice");
        assert String n := msg.getName();
        assert n == "Alice";
        assert !msg.getCode();
        assert !msg.getFlag();

        // Setting code should clear name
        msg.setCode(42);
        assert !msg.getName();
        assert Int32 c := msg.getCode();
        assert c == 42;
        assert !msg.getFlag();

        // Setting flag should clear code
        msg.setFlag(True);
        assert !msg.getName();
        assert !msg.getCode();
        assert Boolean f := msg.getFlag();
        assert f == True;
    }

    // ----- serialization -------------------------------------------------------------------------

    @Test
    void shouldSerializeWithNameSet() {
        TestOneof msg = new TestOneof();
        msg.id = 1;
        msg.setName("Alice");

        immutable Byte[] expected = encode(o -> {
            o.writeInt32(1, 1);
            o.writeString(2, "Alice");
        });
        assert msg.toByteArray() == expected;
    }

    @Test
    void shouldSerializeWithCodeSet() {
        TestOneof msg = new TestOneof();
        msg.id = 1;
        msg.setCode(404);

        immutable Byte[] expected = encode(o -> {
            o.writeInt32(1, 1);
            o.writeInt32(3, 404);
        });
        assert msg.toByteArray() == expected;
    }

    @Test
    void shouldSerializeWithFlagSet() {
        TestOneof msg = new TestOneof();
        msg.id = 1;
        msg.setFlag(True);

        immutable Byte[] expected = encode(o -> {
            o.writeInt32(1, 1);
            o.writeBool(4, True);
        });
        assert msg.toByteArray() == expected;
    }

    @Test
    void shouldSerializeWithNoneSet() {
        TestOneof msg = new TestOneof();
        msg.id = 1;

        immutable Byte[] expected = encode(o -> {
            o.writeInt32(1, 1);
        });
        assert msg.toByteArray() == expected;
    }

    @Test
    void shouldOnlySerializeLastSet() {
        TestOneof msg = new TestOneof();
        msg.id = 1;
        msg.setName("Alice");
        msg.setCode(42);  // clears name

        immutable Byte[] expected = encode(o -> {
            o.writeInt32(1, 1);
            o.writeInt32(3, 42);
        });
        assert msg.toByteArray() == expected;
    }

    // ----- deserialization -----------------------------------------------------------------------

    @Test
    void shouldDeserializeNameField() {
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 1);
            o.writeString(2, "Alice");
        });

        TestOneof msg = new TestOneof();
        msg.mergeFromBytes(bytes);
        assert msg.id == 1;
        assert String n := msg.getName();
        assert n == "Alice";
        assert !msg.getCode();
    }

    @Test
    void shouldDeserializeCodeField() {
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 1);
            o.writeInt32(3, 404);
        });

        TestOneof msg = new TestOneof();
        msg.mergeFromBytes(bytes);
        assert msg.id == 1;
        assert Int32 c := msg.getCode();
        assert c == 404;
        assert !msg.getName();
    }

    @Test
    void shouldDeserializeLastOneofFieldWins() {
        // If the wire data has multiple oneof fields, last one wins
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 1);
            o.writeString(2, "Alice");
            o.writeInt32(3, 42);  // this should win
        });

        TestOneof msg = new TestOneof();
        msg.mergeFromBytes(bytes);
        assert msg.id == 1;
        assert !msg.getName();  // cleared by field 3
        assert Int32 c := msg.getCode();
        assert c == 42;
    }

    // ----- round-trip ----------------------------------------------------------------------------

    @Test
    void shouldRoundTripOneofMessage() {
        TestOneof original = new TestOneof();
        original.id = 42;
        original.setName("Bob");

        immutable Byte[] bytes = original.toByteArray();

        TestOneof restored = new TestOneof();
        restored.mergeFromBytes(bytes);
        assert restored.id == 42;
        assert String n := restored.getName();
        assert n == "Bob";
    }

    @Test
    void shouldComputeCorrectSize() {
        TestOneof msg = new TestOneof();
        msg.id = 1;
        msg.setName("hello");
        assert msg.serializedSize() == msg.toByteArray().size;
    }

    @Test
    void shouldComputeCorrectSizeWithNoneSet() {
        TestOneof msg = new TestOneof();
        msg.id = 1;
        assert msg.serializedSize() == msg.toByteArray().size;
    }

    // ----- helper --------------------------------------------------------------------------------

    private immutable Byte[] encode(function void(CodedOutput) writer) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput out = new CodedOutput(buf);
        writer(out);
        return buf.bytes.freeze(inPlace=True);
    }
}
