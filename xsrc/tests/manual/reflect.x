module TestReflection.xqiz.it
    {
    @Inject Ecstasy.io.Console console;

    void run()
        {
        testInstanceOf();
        testMaskReveal();
        testForm();
        testProps();
        testFuncs();
        testFuncs2();
        }

    const Point(Int x, Int y);

    void testInstanceOf()
        {
        import Ecstasy.collections.HashMap;

        console.println("\n** testInstanceOf");

        Object o = new HashMap<Int, String>();
        assert &o.instanceOf(Map<Int, String>);
        assert !&o.instanceOf(Map<String, String>);

//        Point p = new Point(1, 1);
//        assert &p.implements_(Stringable);
//
//        const Point3(Int x, Int y, Int z) extends Point(x, y);
//
//        Point3 p3 = new Point3(1, 1, 1);
//        assert &p3.extends_(Point);
//
//        Range<Int> interval = 0..5;
//        assert &interval.incorporates_(Interval);
        }

    void testMaskReveal()
        {
        import Ecstasy.fs.Directory;

        console.println("\n** testMaskReveal");

        @Inject Directory tmpDir;

        // the implementation of tmpDir is a "const OSDirectory", which is definitely Stringable,
        // but they must not be able to use that fact when in a different container;
        // since the tests for now run "in-container", the revealAs() would work

        console.println($"tmpDir=" + tmpDir.toString());

        assert !tmpDir.is(Stringable);
        assert !&tmpDir.instanceOf(Stringable);

        try
            {
            Stringable str = tmpDir.as(Stringable);
            assert;
            }
        catch (Exception e)
            {
            console.println($"expected - {e.text}");
            }

        assert Stringable str := &tmpDir.revealAs<Stringable>();
        console.println($"str.length={str.estimateStringLength()}");

        Point p = new Point(1, 1);
        str = &p.maskAs<Stringable>();

        assert !&str.instanceOf(Point);
        try
            {
            p = str.as(Point);
            assert;
            }
        catch (Exception e)
            {
            console.println($"expected - {e.text}");
            }

        assert p := &str.revealAs<Point>();
        console.println($"p={p}");
        }

    void testForm()
        {
        console.println("\n** testForm");
        Type[] types = [String, Object, Char, Clock, Const, Date, Appender];
        for (Type t : types)
            {
            console.println($"t={t}, form={t.form}");
            }
        }

    void testProps()
        {
        console.println("\n** testProps");
        const Point(Int x, Int y)
            {
            static Int    ONE = 1;
            static String PI = foo();
            static String foo() {return "3.14";}
            }

        Point point = new Point(123, 456);
        console.println($"Point point={point}");

        Type<Point> t = Point;
        console.println($"Point type={t}");
        for (Property<Point> prop : t.properties)
            {
            console.println($"prop={prop}");
            console.println($"prop.get(point)={prop.get(point)}");

            Ref impl = prop.of(point);
            console.println($"Ref={impl}, type={impl.actualType}, get()={impl.get()}");
            }

        for (Property prop : t.constants)
            {
            console.println($"constant={prop}");
            if (Object o := prop.isConstant())
                {
                console.println($"value={o}");
                }
            else
                {
                console.println("error!");
                }
            }
        }

    void testFuncs()
        {
        console.println("\n** testFuncs");

        val f = testFuncs;
        console.println($"func name={f.name}");
        console.println($"func type={&f.actualType}");

        void foo(Int x, String s) {}

        val f2  = &foo(1, "hello");  console.println($"f2  = {f2 } -> {f2 ()}");
        val f3  =  foo;              console.println($"f3  = {f3 } -> {f3 (1, "hello")}");
        val f3b = &foo;              console.println($"f3b = {f3b} -> {f3b(1, "hello")}");
        val f4  =  foo(_, _);        console.println($"f4  = {f4 } -> {f4 (1, "hello")}");
        val f4b = &foo(_, _);        console.println($"f4b = {f4b} -> {f4b(1, "hello")}");
        val f5  =  foo(1, _);        console.println($"f5  = {f5 } -> {f5 ("hello")}");
        val f5b = &foo(1, _);        console.println($"f5b = {f5b} -> {f5b("hello")}");
        val f6  =  foo(_, "hello");  console.println($"f6  = {f6 } -> {f6 (1)}");
        val f6b = &foo(_, "hello");  console.println($"f6b = {f6b} -> {f6b(1)}");
        }

    void testFuncs2()
        {
        console.println("\n** testFuncs2");

        const Point<Num extends Number>(Num x, Num y)
            {
            construct(Num x, Num y, String s)
                {
                construct Point(x, y);
                console.println("hello: " + s);
                }

            Num sum.get()
                {
                return x + y;
                }

            Int foo()
                {
                return x.toInt() + y.toInt();
                }

            static String bar(Int n)
                {
                return n.toString();
                }
            }

        Point<Int> p = new Point(3, 4, "world");
        console.println($"Point p={p}, sum={p.sum}, foo()={p.foo()}");

        Type t = &p.actualType;
        console.println($"Type={t}, foo={p.&foo()}");
        console.println($"Type={t}");

        Function[] funcs = t.functions;
        console.println($"{funcs.size} functions:");
        for (Function f : funcs)
            {
            console.println($"func={f}");
            }

        // TODO CP
        console.println($"methods={t.methods}, properties={t.properties}");
        console.println($"constructors={t.constructors}");
        console.println($"multimethods={t.multimethods}");
        }
    }