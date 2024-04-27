/**
 * Tests for comparisons.
 */
class Medium {

    public void run() {
        compareArraysFormal();
        compareComposite();
    }

    @Test
    void compareArraysFormal() {
        Int[] a1 = [1, 2];
        Int[] a2 = [2, 1];

        Collection<Int> c1 = a1;
        Collection<Int> c2 = a2;

        assert !checkArrayEquals(a1, a2);
        assert checkEquals(c1, c2);
    }

    @Test
    void compareComposite() {
        typedef String|Int as StrInt;

        StrInt si1 = get(True);
        StrInt si2 = get(False);

        assert si1 != si2;
        assert !checkEquals(si1, si2);

        StrInt get(Boolean int) = int ? 1 : "a";
    }

    <T> Boolean checkEquals(T t1, T t2) {
        return t1 == t2;
    }

    <T> Boolean checkArrayEquals(T[] a1, T[] a2) {
        return a1 == a2;
    }

}
