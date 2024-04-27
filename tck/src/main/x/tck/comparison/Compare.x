/**
 * Tests for comparisons.
 */
class Compare {

    public void run() {
        compareIntsDirect();
        compareIntsWithBjarn();
        compareIntsFormal();
        compareStringsWithBjarn();
        compareStringsFormal();
        compareConstantsDirect();
        compareConstantsWithBjarn();
        compareConstantsFormal();
        compareCustomDirect();
        compareCustomWithBjarn();
        compareCustomFormal();
        compareToNull();
    }

    @Test
    void compareIntsDirect() {
        Int i = 1;
        Int j = 2;

        assert i != j;
        assert i < j;
        assert i <= j;
        assert j > i;
        assert j >= i;
    }

    @Test
    void compareIntsWithBjarn() {
        Int i = 1;
        Int j = 2;

        assert !i.equals(j);
        assert i.compare(j) == Lesser;
    }

    @Test
    void compareIntsFormal() {
        Int i = 1;
        Int j = 2;

        assert !checkEquals(i, j);
        assert checkLess(i, j);
    }

    @Test
    void compareStringsWithBjarn() {
        String s1 = "a";
        String s2 = "b";

        assert !s1.equals(s2);
        assert s2.compare(s1) == Greater;
    }

    @Test
    void compareStringsFormal() {
        String s1 = "a";
        String s2 = "b";

        assert !checkEquals(s1, s2);
        assert checkLess(s1, s2);
    }

    @Test
    void compareConstantsDirect() {
        NamedPoint np1 = new NamedPoint("a", 0, 2);
        NamedPoint np2 = new NamedPoint("b", 0, 2);

        assert np1 != np2;
        assert np1 <=> np2 == Lesser;
        assert np1.as(Point) == np2.as(Point);
        assert np1.as(Point) <=> np2.as(Point) == Equal;
    }

    @Test
    void compareConstantsWithBjarn() {
        NamedPoint np1 = new NamedPoint("a", 0, 2);
        NamedPoint np2 = new NamedPoint("b", 0, 2);
        Point p1 = asPoint(np1);
        Point p2 = asPoint(np2);

        assert !np1.equals(np2);
        assert np1.compare(np2) == Lesser;
        assert p1.equals(p2);
        assert p1.compare(p2) == Equal;

        Point asPoint(Point p) = p;
    }

    @Test
    void compareConstantsFormal() {
        NamedPoint np1 = new NamedPoint("a", 0, 2);
        NamedPoint np2 = new NamedPoint("b", 0, 2);
        Point p1 = asPoint(np1);
        Point p2 = asPoint(np2);

        assert !checkEquals(np1, np2);
        assert checkEquals(p1, p2);

        Point asPoint(Point p) = p;
    }

    @Test
    void compareCustomDirect() {
        AnyValue a1 = new AnyValue(1, "foo");
        AnyValue a2 = new AnyValue(1, "bar");

        assert a1 == a2;
        assert a1 <=> a2 == Equal;
    }

    @Test
    void compareCustomWithBjarn() {
        AnyValue a1 = new AnyValue(1, "foo");
        AnyValue a2 = new AnyValue(1, "bar");

        assert a1.equals(a2);
        assert a1.compare(a2) == Equal;
    }

    @Test
    void compareCustomFormal() {
        AnyValue a1 = new AnyValue(1, "foo");
        AnyValue a2 = new AnyValue(1, "bar");

        assert checkEquals(a1, a2);
        assert !checkLess(a1, a2);
    }

    @Test
    void compareToNull() {
        String? s1 = get(False);
        String? s2 = get(True);

        assert s1 == Null;
        assert s2 != Null;

        String? get(Boolean notNull) = notNull ? "" : Null;
    }

    <T> Boolean checkEquals(T t1, T t2) {
        return t1 == t2;
    }

    <T extends Orderable> Boolean checkLess(T t1, T t2) {
        return t1 < t2;
    }

    static const Point(Int x, Int y);

    static const NamedPoint(String name, Int x, Int y)
        extends Point(x, y);

    static const AnyValue(Int key, String value) {
        @Override
        static <CompileType extends AnyValue> Boolean equals(CompileType value1, CompileType value2) {
            return value1.key == value2.key;
        }

        @Override
        static <CompileType extends AnyValue> Ordered compare(CompileType value1, CompileType value2) {
            return value1.key <=> value2.key;
        }
    }
}
