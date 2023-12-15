/**
 * Very basic custom ops tests.
 */
class Basic {

    void testAddInt() {
        Point p = new Point(0, 0);
        p += 1;
        assert p.x == p.y == 1;
    }

    void testAddPoint() {
        Point p1 = new Point(1, 1);
        Point p2 = p1 + p1;
        assert p2.x == p2.y == 2;
    }

    static const Point(Int x, Int y) {
        @Op("+")
        Point add(Int scalar) {
            return new Point(x + scalar, y + scalar);
        }

        @Op("+")
        Point add(Point p) {
            return new Point(x + p.x, y + p.y);
        }
    }
}

