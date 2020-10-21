module TestReflection
    {
    import ecstasy.reflect.TypeTemplate;

    @Inject ecstasy.io.Console console;

    void run()
        {
        testFuncType();
        testTypeStrings();
        testInstanceOf();
        testMaskReveal();
        testForm();
        testProps();
        testInvoke();
        testInvoke2();
        testInvokeAsync();
        testBind();
        testChildTypes();
        testTypeTemplate();
        testEnum();
        testStruct();
        testClass();
        testTypeSystem();
        testTypes();
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
            console.println($"{names[Each.count]}={type.DataType}");
            }
        }

    const Point(Int x, Int y);

    void testInstanceOf()
        {
        import ecstasy.collections.HashMap;

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
        import ecstasy.fs.Directory;

        console.println("\n** testMaskReveal");

        @Inject Directory tmpDir;

        // the implementation of tmpDir is a "const OSDirectory", which is definitely Stringable,
        // but they must not be able to use that fact when in a different container;
        // since the tests for now run "in-container", the revealAs() would work

        console.println("tmpDir=" + tmpDir.toString());

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

        if (Stringable str := &tmpDir.revealAs(Stringable))
            {
            assert;
            }
        console.println($"cannot be revealed: {&tmpDir.actualType}");

        Point      p   = new Point(1, 1);
        Stringable str = &p.maskAs<Stringable>();

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

        assert p := &str.revealAs(Point);
        console.println($"p={p}");

        assert Point:struct p2 := &str.revealAs(Point:struct);
        console.println($"p2={p2}");
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
        console.println($"Point type={t}");
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
        console.println($"Ref={impl}, type={impl.actualType}, get()={impl.get()}");

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

    void testInvoke()
        {
        console.println("\n** testInvoke");

        val fnSelf = testInvoke;
        console.println($"func name={fnSelf.name}");
        console.println($"func type={&fnSelf.actualType}");

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

    void testInvoke2()
        {
        console.println("\n** testInvoke2");

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
                // TODO CP - splitting the multi-line before the "invoke" breaks the parser
                console.println($|method.invoke()=
                              + $|{method.as(Method<Point<Int>, Tuple<>, Tuple<Int>>).invoke(p, Tuple:())[0]}
                                 );
                }
            }

        for (val constructor : t.constructors)
            {
            switch (constructor.params.size)
                {
                case 1:
                    Class<Point, Point:protected, Point:private, Point:struct> clz = Point;
                    assert Point:struct structure := clz.allocate();
                    structure.x = Int:1;
                    structure.y = Int:2;
                    Tuple<Point<Int>> p2 = constructor.invoke(Tuple:(structure));
                    console.println($"construct(structure)={p2[0]}");
                    break;

                case 2:
                    Tuple<Point<Int>> p2 = constructor.invoke((Int:1, Int:2));
                    console.println($"construct(1,2)={p2[0]}");
                    break;

                case 3:
                    Tuple<Point<Int>> p3 = constructor.invoke((Int:1, Int:2, "there"));
                    console.println($"construct(1,2,\"there\")={p3[0]}");
                    break;
                }
            }
        }

    void testInvokeAsync()
        {
        console.println("\n** testInvokeAsync");

        DelayService svc = new DelayService();

        function Int (Duration) calc = svc.calcSomethingBig;

        console.println("calling sync");
        Tuple resultS = calc.invoke(Tuple:(Duration.ofMillis(10)));
        console.println(resultS[0]);

        console.println("calling sync &Future.get()");
        @Future Tuple resultS2 = calc.invoke(Tuple:(Duration.ofMillis(10)));
        console.println(&resultS2.get());

        console.println("calling async");
        @Future Tuple resultA = calc.invoke(Tuple:(Duration.ofMillis(20)));
        &resultA.whenComplete((t, e) ->
            {
            console.println($"complete {t?[0] : assert}");
            });
        console.println($"assigned={&resultA.assigned}, result={resultA[0]}, assigned={&resultA.assigned}");

        service DelayService
            {
            Int calcSomethingBig(Duration delay)
                {
                @Inject Clock clock;
                @Future Int   result;

                console.println($"delay {delay}");
                clock.schedule(delay, () -> {result=delay.milliseconds;});
                return result;
                }
            }
        }

    void testBind()
        {
        import ecstasy.collections.ListMap;
        import ecstasy.reflect.Parameter;

        console.println("\n** testBind");

        function void (Int, String) log =
            (i, v) -> console.println($"[{i}] {v}");

        Parameter<Int>    param0 = log.params[0].as(Parameter<Int>);
        Parameter<String> param1 = log.params[1].as(Parameter<String>);

        // single bind
        function void (Int) hello = log.bind(param1, "hello").as(function void (Int));
        hello(0);

        // multi bind
        Map<Parameter, Object> params = new ListMap();
        params.put(param0, 1);
        params.put(param1, "world");

        function void () world = log.bind(params);
        world();
        }

    void testChildTypes()
        {
        console.println("\n** testChildTypes");

        Type[] types = [Nullable, Map, ecstasy.collections.HashMap, Type, Class];
        for (Type type : types)
            {
            console.println($"{type} children: {type.childTypes}");
            }
        }

    void testTypeTemplate()
        {
        console.println("\n** testTypeTemplate");

        Type t = String;
        TypeTemplate tt = t.template;
        console.println($"type={t}; template={tt}");
        }

    void testEnum()
        {
        console.println("\n** testEnum");

        console.println($"Boolean.count={Boolean.count}");
        console.println($"Boolean.values={Boolean.values}");
        console.println($"Boolean.names={Boolean.names}");
        }

    void testStruct()
        {
        console.println("\n** testStruct");

        Point p = new Point(3,4);
        analyzeStructure(p);

        const Point3D(Int x, Int y, Int z) extends Point(x, y);
        Point3D p3d = new Point3D(5,6,7);
        analyzeStructure(p3d);

        if (Point3D.StructType p3s := &p3d.revealAs(Point3D.StructType))
            {
            Point3D p3d2 = Point3D.instantiate(p3s);
            assert p3d2 == p3d;
            }
        }

    void testClass()
        {
        console.println("\n** testClass");

        Class c1 = Map;
        analyzeClass(c1);

        Class c2 = ecstasy.collections.ListMap;
        analyzeClass(c2);

        Class c3 = Map<Int, String>;
        analyzeClass(c3);

        Class c4 = ecstasy.collections.ListMap<Date, Time>;
        analyzeClass(c4);

        Class c7 = ecstasy.collections.ListMap<Date, Time>.Entries;
        analyzeClass(c7);

        Map<Int, String> map = new ecstasy.collections.ListMap();
        analyzeStructure(map);

        Boolean f = True;
        Class   c = True;
        Type    t = True;
        }

    void analyzeClass(Class clz)
        {
        console.println($"Analyzing: {clz}");
        }


    void analyzeStructure(Object o)
        {
        console.println($"Analyzing: {o}");

        Type t = &o.actualType;
        console.println($"Type={t}");

        if (Class c := t.fromClass())
            {
            console.println($"Class={c}");
            console.println($"PublicType={c.PublicType}");
            console.println($"StructType={c.StructType}");
            console.println($"formalTypes={c.formalTypes}");

            Type ts = c.StructType;
            for (val prop : ts.properties)
                {
                // property must have a field, must not be injected, not constant/formal
                console.println($|prop={prop.name}, constant={prop.isConstant()}, readOnly={prop.readOnly}
                                 |     hasUnreachableSetter={prop.hasUnreachableSetter}, formal={prop.formal}
                                 |     hasField={prop.hasField}, injected={prop.injected}, lazy={prop.lazy}
                                 |     atomic={prop.atomic}, abstract={prop.abstract}
                               );

                // need to get a Ref for the property:
                // - must be assigned
                // - actual type cannot be Service
                val ref = prop.of(o);
                console.println($|assigned={ref.assigned}, peek()={ref.peek()}, actualType={ref.actualType}
                                 |     isService={ref.isService}, isConst={ref.isConst}
                                 |     isImmutable={ref.isImmutable}, hasName={{String name = "n/a"; name := ref.hasName(); return name;}}, var={ref.is(Var)}
                               );
                }

            if (val s := &o.revealAs(Struct))
                {
                Object clone = c.instantiate(s);
                console.println($"clone={clone}");
                }
            }
        }

    void testTypeSystem()
        {
        console.println("\n** testTypeSystem");

        TypeSystem ts = this:service.typeSystem;
        console.println($"current TypeSystem={ts}");
        console.println($"modules              : {ts.modules              }");
        console.println($"sharedModules        : {ts.sharedModules        }");
        console.println($"moduleBySimpleName   : {ts.moduleBySimpleName   }");
        console.println($"moduleByQualifiedName: {ts.moduleByQualifiedName}");

        console.println("modules:");
        for (Module _module : ts.modules)
            {
            displayModule(_module);
            }

        String[] names =
                [
                "String",                      // should use "implicit.x" to find it
                "ecstasy.text.String",         // should find it via package import
                "ecstasy.ecstasy.text.String", // should find it via package import (x2)
                "Map<String, Int>",            // type parameters (and implicit.x)
                "",                            // == test module
                "Point",                       // in test module
                "bob",                         // shouldn't find it
                "Point.Bob",                   // shouldn't find it
                "TestReflection:Point",        // with explicit module name
                "ecstasy:collections.HashMap",
                "ecstasy.xtclang.org:collections.HashMap",
                "TestReflection:",             // just explicit module name
                "ecstasy:",
                "ecstasy.xtclang.org:",
                "@Unchecked Int",
                "HashMap<String?, @Unchecked Int>",
                "HashMap<String?, @Unchecked Int>.Entry",
                ];

        for (String name : names)
            {
            try
                {
                if (Class clz := ts.classForName(name))
                    {
                    console.println($"class for \"{name}\"={clz}");
                    }
                else
                    {
                    console.println($"no such class: \"{name}\"");
                    }
                }
            catch (Exception e)
                {
                console.println($"exception occurred lookup up class \"{name}\"; exception={e}");
                }
            }
        }

    void displayModule(Module _module)
        {
        console.println($"module \"{_module.simpleName}\" (\"{_module.qualifiedName}\")");
        val deps = _module.modulesByPath;
        if (!deps.empty)
            {
            console.println($" - dependencies:");
            for ((String path, Module dep) : deps)
                {
                console.println($"    - \"{path}\" => \"{dep.qualifiedName}\"");
                }
            }

        console.println(" - contents:");
        displayPackage(_module, "   ");
        }

    void displayPackage(Package pkg, String prefix = "")
        {
        prefix += " |-";
        for (Class child : pkg.classes)
            {
            console.println($"{prefix} {child.name}");
            if (child.implements(Package), Object instance := child.isSingleton())
                {
                displayPackage(instance.as(Package), prefix);
                }
            }
        }

    class Container<A, B, C>
        {
        class Containee<D, E, F>
            {
            }
        }

    void testTypes()
        {
        console.println("\n** testTypes");

        {
        Type t1 = Map;
        Type t2 = Int;
        Type t3 = t1.parameterize([t2]);
        console.println($"{t1} < {t2} > = {t3}");
        assert t3 == Map<Int>;
        }

        {
        Type t1 = Map;
        Type t2 = String;
        Type t3 = Int;
        Type t4 = t1.parameterize([t2, t3]);
        console.println($"{t1} < {t2}, {t3} > = {t4}");
        assert t4 == Map<String, Int>;
        }

        {
        Type t1 = Map;
        Type t2 = Stringable;
        Type t3 = t1 + t2;
        console.println($"{t1} + {t2} = {t3}");
        assert t3 == Map + Stringable;
        }

        {
        Type t1 = Map;
        Type t2 = Set;
        Type t3 = t1 | t2;
        console.println($"{t1} | {t2} = {t3}");
        assert t3 == Map | Set;
        }

        {
        Type t1 = ecstasy.collections.HashMap;
        Type t2 = Map;
        Type t3 = t1 - t2;
        console.println($"{t1} - {t2} = {t3}");
        assert t3 == ecstasy.collections.HashMap - Map;
        }

        {
        val container = new Container<String,Int,Char>();
        val containee = container.new Containee<Char,String,Map<Int, String>>();
        console.println($"Container<String,Int,Char>.Containee<Char,String,Map<Int, String>> = {&containee.actualType}");
        }
        }
    }
