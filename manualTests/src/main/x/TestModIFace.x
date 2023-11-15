module TestModIFace {
    @Inject Console console;

    void run() {
        Point point1 = new Point(0, 2);
        console.print($"point1={point1} hypo={point1.hypo}");

        NamedPoint point2 = new NamedPoint("top-left", 1, 0);
        console.print($"point2={point2} hypo={point2.hypo}");

        Point point3 = point2;

        assert point1 == point3;
        assert Point.equals(point1, point3);
        assert point1 <=> point3 == Equal;
        assert Point.compare(point1, point3) == Equal;
    }

    const Point(Int x, Int y) {
        @Lazy(() -> x*x + y*y) Int hypo;
    }

    const NamedPoint(String name, Int x, Int y)
            extends Point(2*y, x + 1) {
        @Override
        Int estimateStringLength() {
            return super() + name.size;
        }
    
        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            name.appendTo(buf.add('('));
            x.appendTo(buf.addAll(": x="));
            y.appendTo(buf.addAll(", y="));
            return buf.add(')');
        }
    }
}
