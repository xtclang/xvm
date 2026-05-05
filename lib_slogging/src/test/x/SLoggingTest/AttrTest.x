import slogging.Attr;

/**
 * Tests for `Attr` — the single carrier for structured data.
 */
class AttrTest {

    @Test
    void shouldCarryStringValue() {
        Attr a = Attr.of("user", "alice");
        assert a.key == "user";
        assert a.value == "alice";
    }

    @Test
    void shouldCarryIntegerValue() {
        Attr a = Attr.of("count", 42);
        assert a.key == "count";
        assert a.value == 42;
    }

    @Test
    void shouldCarryBooleanValue() {
        Attr a = Attr.of("audit", True);
        assert a.key == "audit";
        assert a.value == True;
    }

    @Test
    void shouldCarryNestedGroup() {
        Attr group = Attr.group("user", [
                Attr.of("id",   "u_1"),
                Attr.of("role", "admin"),
        ]);
        assert group.key == "user";
        assert group.value.is(Attr[]);
        Attr[] children = group.value.as(Attr[]);
        assert children.size == 2;
        assert children[0].key == "id"   && children[0].value == "u_1";
        assert children[1].key == "role" && children[1].value == "admin";
    }
}
