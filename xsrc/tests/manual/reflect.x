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
        const Point(Int x, Int y);

        Type t = Point;
        console.println($"Point type={t}");
        for (Property prop : t.properties)
            {
            console.println($"prop={prop}");
            }
        }

    void testFuncs()
        {
        console.println("\n** testFuncs");

        val f = testFuncs;
        console.println($"func name={f.name}");
        console.println($"func type={&f.actualType}");

        // TODO GG - we regressed the "&" and "_" bind-but-do-not-invoke functionality
        void foo(Int x, String s) {}
        // val f2 = &foo(1, "hello");
        val f3  =  foo;
        val f3b = &foo;
        // val f4  =  foo(_, _);
        // val f4b = &foo(_, _);
        // val f5  =  foo(1, _);
        // val f5b = &foo(1, _);
        // val f6  =  foo(_, "hello");
        // val f6b = &foo(_, "hello");
        // val f7b = &foo(7, "hello");
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
            }

        Point p = new Point<Int>(3, 4, "world");
        console.println($"Point p={p}, sum={p.sum}, foo()={p.foo()}");

        Type t = &p.actualType;
        // TODO GG -> console.println($"Type={t}, methods={t.methods}, properties={t.properties}, foo={p.&foo()}");
        console.println($"Type={t}");

        // TODO GG
        console.println($"foo={p.foo}");

        // TODO CP
        console.println($"methods={t.methods}, properties={t.properties}");
        }
    }