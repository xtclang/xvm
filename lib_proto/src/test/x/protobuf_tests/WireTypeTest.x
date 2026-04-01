import protobuf.WireType;

class WireTypeTest {

    @Test
    void shouldHaveCorrectIds() {
        assert WireType.VARINT.id == 0;
        assert WireType.I64.id    == 1;
        assert WireType.LEN.id    == 2;
        assert WireType.SGROUP.id == 3;
        assert WireType.EGROUP.id == 4;
        assert WireType.I32.id    == 5;
    }

    @Test
    void shouldMakeTag() {
        // field 1, VARINT: (1 << 3) | 0 = 8
        assert WireType.makeTag(1, WireType.VARINT) == 8;
        // field 2, LEN: (2 << 3) | 2 = 18
        assert WireType.makeTag(2, WireType.LEN) == 18;
        // field 3, I32: (3 << 3) | 5 = 29
        assert WireType.makeTag(3, WireType.I32) == 29;
    }

    @Test
    void shouldExtractFieldNumber() {
        assert WireType.getFieldNumber(8)  == 1;
        assert WireType.getFieldNumber(18) == 2;
        assert WireType.getFieldNumber(29) == 3;
    }

    @Test
    void shouldExtractWireType() {
        assert WireType.getWireType(8)  == WireType.VARINT;
        assert WireType.getWireType(18) == WireType.LEN;
        assert WireType.getWireType(29) == WireType.I32;
    }

    @Test
    void shouldRoundTripTagEncoding() {
        for (Int fieldNumber : [1, 2, 15, 16, 100, 536870911]) {
            for (WireType wireType : WireType.values) {
                Int tag = WireType.makeTag(fieldNumber, wireType);
                assert WireType.getFieldNumber(tag) == fieldNumber;
                assert WireType.getWireType(tag)    == wireType;
            }
        }
    }
}
