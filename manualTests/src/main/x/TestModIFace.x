module TestModIFace {
    @Inject Console console;

    void run() {
        Point point1 = new Point(0, 2);
        console.print($"point1={point1} hypo={point1.hypo}");

        NamedPoint point2 = new NamedPoint("top-left", 1, 0);
        console.print($"point2={point2} hypo={point2.hypo}");

        //Hasher<Point>      hasherP = new NaturalHasher<Point>();
        //Hasher<NamedPoint> hasherN = new NaturalHasher<NamedPoint>();

        //assert Point.hashCode(point1)      == Point.hashCode(point2);
        //assert Point.hashCode(point1)      == hasherP.hashOf(point1);
        //assert Point.hashCode(point2)      == hasherP.hashOf(point2);
        //assert NamedPoint.hashCode(point2) == hasherN.hashOf(point2);
        
        // TODO: not structly true, but would expect the name.hashCode to
        // change the overall hash
        //assert hasherP.hashOf(point2) != hasherN.hashOf(point2);
    
        Point point3 = point2;

        assert point1 == point3;
        assert Point.equals(point1, point3);
        assert point1 <=> point3 == Equal;
        assert Point.compare(point1, point3) == Equal;
        
        AnyValue foo = new AnyValue(1, "foo");
        AnyValue bar = new AnyValue(1, "bar");
        assert foo == bar;
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

    
    const AnyValue(Int key, String value) {
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
