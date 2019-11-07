module TestReflection.xqiz.it
    {
    @Inject Ecstasy.io.Console console;

    void run()
        {
        testFuncType();
        testTypeStrings();
        testInstanceOf();
        testMaskReveal();
        testForm();
        testProps();
        testFuncs();
        testFuncs2();
        }

    Function<<Int, String>, <Int>> foo()
        {
        TODO §
        }

    void testFuncType()
        {
        console.println("\n** testFuncType");

        static Int bar(Int n, String s) {return 0;}

        Function<<Int, String>, <Int>> f = bar;
        Function<<Int, String>, <Int>> f2 = bar.as(Function<<Int, String>, <Int>>);
        if (bar.is(Function<<Int, String>, <Int>>))
            {
            Int x = 1;
            }
        }

    void testTypeStrings()
        {
        console.println("\n** testTypeStrings");

        String[] names = [    "String",     "String?",     "String|Int",     "Ref",     "Ref<Int>",      "Var<Int?>",     "Int+Ref",     "Var-Ref"];
        Type[]   types = [Type<String>, Type<String?>, Type<String|Int>, Type<Ref>, Type<Ref<Int> >, Type<Var<Int?>>, Type<Int+Ref>, Type<Var-Ref>];
        Each: for (Type type : types)
            {
            console.println($"{names[Each.count]}={formatType(type.DataType)}");
            }
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

            Int x
                {
                void foo() {}
                }
            }

        Point point = new Point(123, 456);
        console.println($"Point point={point}");

        Type<Point> t = Point;
        console.println($"Point type={formatType(t)}");
        for (Property<Point> prop : t.properties)
            {
            console.println($"prop={prop}");
//            console.println($"prop.get(point)={prop.get(point)}");

            Ref impl = prop.of(point);
//            console.println($"Ref={impl}, type={impl.actualType}, get()={impl.get()}");

            Type typeImpl = &impl.actualType;
            if (Property prop2 := typeImpl.fromProperty())
                {
                console.println($"impl.fromProp={prop2}");
                // TODO val={prop2.get(point)}");
                }
            else
                {
                console.println("not from property?!?!");
                }
            }

        Ref impl = point.&x;
        console.println($"Ref={impl}, type={formatType(impl.actualType)}, get()={impl.get()}");

        Type typeImpl = &impl.actualType;
        if (Property prop2 := typeImpl.fromProperty())
            {
            console.println($"impl.fromProp={prop2}");
            // TODO val={prop2.get(point)}");
            }
        else
            {
            console.println("not from property?!?!");
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

        void foo(Int x, String s)
            {
            console.println($" -> in foo() x={x}, s={s}");
            }

        val f2  = &foo(1, "hello");  console.println($"f2  = {f2 } -> {f2 ()}");
        val f3  =  foo;              console.println($"f3  = {f3 } -> {f3 (1, "hello")}");
        val f3b = &foo;              console.println($"f3b = {f3b} -> {f3b(1, "hello")}");
        val f4  =  foo(_, _);        console.println($"f4  = {f4 } -> {f4 (1, "hello")}");
        val f4b = &foo(_, _);        console.println($"f4b = {f4b} -> {f4b(1, "hello")}");
        val f5  =  foo(1, _);        console.println($"f5  = {f5 } -> {f5 ("hello")}");
        val f5b = &foo(1, _);        console.println($"f5b = {f5b} -> {f5b("hello")}");
        val f6  =  foo(_, "hello");  console.println($"f6  = {f6 } -> {f6 (1)}");
        val f6b = &foo(_, "hello");  console.println($"f6b = {f6b} -> {f6b(1)}");

        f2.invoke(Tuple:());
        f4.invoke((42, "goodbye"));
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

        val t = &p.actualType.as(Type<Point<Int>>);
        console.println($"Type={t}, foo={p.&foo()}");
        console.println($"Type={t}");

        Function[] funcs = t.functions;
        console.println($"{funcs.size} functions:");
        for (Function f : funcs)
            {
            console.println($"func={f}");
            }

        console.println($"methods={t.methods}, properties={t.properties}");
        console.println($"constructors={t.constructors}");
        console.println($"multimethods={t.multimethods}");

        // TODO CP figure out how to make this work: val method = Point.foo;
        for (val method : t.methods)
            {
            if (method.name == "foo")
                {
                console.println($"method={method}");
                // console.println($"method.invoke(p)={method.as(TODO).invoke(p, Tuple:())}");
                }
            }

        for (val constructor : t.constructors)
            {
            switch (constructor.params.size)
                {
                case 2:
                    Tuple<Point<Int>> p2 = constructor.invoke((1, 2));
                    console.println($"construct(1,2)={p2[0]}");
                    break;

                case 3:
                    Tuple<Point<Int>> p3 = constructor.invoke((1, 2, "there"));
                    console.println($"construct(1,2,\"there\")={p3[0]}");
                    break;
                }
            }
        }

    String formatType(Type type)
        {
        Type.Form form = type.form;
        switch (form)
            {
            case Pure:
                break;

            case Class:
// TODO CP assert Class clz := type.fromClass();

//                Class.Composition cmp = clz.composition;
//                while ((Annotation annotation, cmp) := cmp.deannotate())
//                    {
//                    // print out annotation
//                    }
//                cmp.template.name;
                break;

            case Property:
            case Child:
            case FormalProperty:
            case FormalParameter:
            case FormalChild:
                break;

            case Intersection:
                assert (Type t1, Type t2) := type.relational();
                return $"{formatType(t1)} | {formatType(t2)}";

            case Union:
                assert (Type t1, Type t2) := type.relational();
                return $"{formatType(t1)} + {formatType(t2)}";

            case Difference:
                assert (Type t1, Type t2) := type.relational();
                return $"{formatType(t1)} - {formatType(t2)}";

            case Immutable:
                assert Type t1 := type.modifying();
                return $"immutable {formatType(t1)}";

            case Access:
                assert val access := type.accessSpecified();
                assert Type t1 := type.modifying();
                return $"{formatType(t1)}:{access.keyword}";

            case Annotated:
                break;

            case Parameterized:
                assert Type[] params := type.parameterized();
                assert Type   t1     := type.modifying();
                StringBuffer sb = new StringBuffer();
                sb.append(formatType(t1))
                  .append('<');
                EachParam: for (Type param : params)
                    {
                    if (!EachParam.first)
                        {
                        sb.append(", ");
                        }
                    sb.append(formatType(param));
                    }
                sb.append('>');
                return sb.toString();

            case Typedef:
            case Sequence:
                break;

            default:
                assert;
            }

        // TODO
        return type.toString();
        }
    }