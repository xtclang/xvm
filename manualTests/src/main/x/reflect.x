module TestReflection {
    import ecstasy.reflect.TypeTemplate;

    @Inject ecstasy.io.Console console;

    void run() {
        testFuncType();
        testTypeStrings();
        testInstanceOf();
        testMaskReveal();
        testForm();
        testProps();
        testMethods();
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

    void testFuncType() {
        console.print("\n** testFuncType");

        static Int bar(Int n, String s) {return 0;}

        Function<<Int, String>, <Int>> f = bar;
        Function<<Int, String>, <Int>> f2 = bar.as(Function<<Int, String>, <Int>>);
        assert f2(1, "") == 0;
    }

    void testTypeStrings() {
        console.print("\n** testTypeStrings");

        String[] names = [    "String",     "String?",     "String|Int",     "Ref",     "Ref<Int>",      "Var<Int?>",     "Int+Ref",     "Var-Ref"];
        Type[]   types = [Type<String>, Type<String?>, Type<String|Int>, Type<Ref>, Type<Ref<Int> >, Type<Var<Int?>>, Type<Int+Ref>, Type<Var-Ref>];
        Each: for (Type type : types) {
            console.print($"{names[Each.count]}={type}");
        }
    }

    const Point(Int x, Int y);

    void testInstanceOf() {
        import ecstasy.collections.HashMap;

        console.print("\n** testInstanceOf");

        Object o = new HashMap<Int, String>();
        assert &o.instanceOf(Map<Int, String>);
        assert !&o.instanceOf(Map<String, String>);
    }

    void testMaskReveal() {
        import ecstasy.fs.Directory;

        console.print("\n** testMaskReveal");

        @Inject Directory tmpDir;

        // the implementation of tmpDir is a "const OSDirectory", which is definitely Stringable,
        // but they must not be able to use that fact when in a different container;
        // since the tests for now run "in-container", the revealAs() would work

        console.print("tmpDir=" + tmpDir.toString());

        assert !tmpDir.is(Stringable);
        assert !&tmpDir.instanceOf(Stringable);

        try {
            Stringable str = tmpDir.as(Stringable);
            assert;
        } catch (Exception e) {
            console.print($"expected - {e.text}");
        }

        if (Stringable str := &tmpDir.revealAs(Stringable)) {
            assert;
        }
        console.print($"cannot be revealed: {&tmpDir.actualType}");

        Point      p   = new Point(1, 1);
        Stringable str = &p.maskAs<Stringable>(Stringable);

        assert !&str.instanceOf(Point);
        try {
            p = str.as(Point);
            assert;
        } catch (Exception e) {
            console.print($"expected - {e.text}");
        }

        assert p := &str.revealAs(Point);
        console.print($"p={p}");

        assert (struct Point) p2 := &str.revealAs((struct Point));
        console.print($"p2={p2}");
    }

    void testForm() {
        console.print("\n** testForm");
        Type[] types = [String, Object, Char, Clock, Const, Date, Appender];
        for (Type t : types) {
            console.print($"t={t}, form={t.form}");
        }
    }

    void testProps() {
        console.print("\n** testProps");
        const Point(Int x, Int y) {
            static Int    ONE = 1;
            static String PI = foo();
            static String foo() {return "3.14";}

            Int x {
                void foo() {}
            }
        }

        Point point = new Point(123, 456);
        console.print($"Point point={point}");

        Type<Point> t = Point;
        console.print($"Point type={t}");
        for (Property<Point> prop : t.properties) {
            console.print($"prop={prop} prop.get(point)={prop.get(point)}");

            Ref impl = prop.of(point);
            console.print($"Ref={impl}, type={impl.actualType}, get()={impl.get()}");

            Type typeImpl = &impl.actualType;
            assert Property prop2 := typeImpl.fromProperty();
            console.print($"impl.fromProp={prop2}");
        }

        Ref impl = point.&x;
        console.print($"Ref={impl}, type={impl.actualType}, get()={impl.get()}");

        Type typeImpl = &impl.actualType;
        assert Property propX := typeImpl.fromProperty();
        console.print($"impl.fromProp={propX} val={propX.get(point)}");

        for (Property prop : t.constants) {
            console.print($"constant={prop}");
            if (Object o := prop.isConstant()) {
                console.print($"value={o}");
            } else {
                console.print("error!");
            }
        }
    }

    void testMethods() {
        console.print("\n** testMethods");

        report(Inner.foo);
        report(Outer.foo);

        class Inner {
            void foo(Int i) {}
        }

        new Outer<String>().foo("");
    }

    class Outer<Element> {
        void foo(Element el) {
            TestReflection.report(foo);
            TestReflection.report(Child.bar);
        }

        class Child {
            void bar(Int i) {}
        }
    }

    void report(Method m) {
        console.print($"method={m}; target={m.Target}");
    }

    void testInvoke() {
        console.print("\n** testInvoke");

        val fnSelf = testInvoke;
        console.print($"func name={fnSelf.name}");
        console.print($"func type={&fnSelf.actualType}");

        void foo(Int x, String s) {
            console.print($" -> in foo() x={x}, s={s}");
        }

        val f2  = &foo(1, "hello");  console.print($"f2  = {f2 } -> {f2 ()}");
        val f3  =  foo;              console.print($"f3  = {f3 } -> {f3 (1, "hello")}");
        val f3b = &foo;              console.print($"f3b = {f3b} -> {f3b(1, "hello")}");
        val f4  =  foo(_, _);        console.print($"f4  = {f4 } -> {f4 (1, "hello")}");
        val f4b = &foo(_, _);        console.print($"f4b = {f4b} -> {f4b(1, "hello")}");
        val f5  =  foo(1, _);        console.print($"f5  = {f5 } -> {f5 ("hello")}");
        val f5b = &foo(1, _);        console.print($"f5b = {f5b} -> {f5b("hello")}");
        val f6  =  foo(_, "hello");  console.print($"f6  = {f6 } -> {f6 (1)}");
        val f6b = &foo(_, "hello");  console.print($"f6b = {f6b} -> {f6b(1)}");

        f2.invoke(Tuple:());
        f4.invoke((42, "goodbye"));
    }

    void testInvoke2() {
        console.print("\n** testInvoke2");

        const Point<Num extends Number>(Num x, Num y) {
            construct(Num x, Num y, String s) {
                construct Point(x, y);
                console.print("hello: " + s);
            }

            Num sum.get() {
                return x + y;
            }

            Int foo() {
                return x.toInt64() + y.toInt64();
            }

            static String bar(Int n) {
                return n.toString();
            }
        }

        Point<Int> p = new Point(3, 4, "world");
        console.print($"Point p={p}, sum={p.sum}, foo()={p.foo()}");

        val t = &p.actualType.as(Type<Point<Int>>);
        console.print($"Type={t}, foo={p.&foo()}");
        console.print($"Type={t}");

        Function[] funcs = t.functions;
        console.print($"{funcs.size} functions:");
        for (Function f : funcs) {
            console.print($"func={f}");
        }

        console.print($"methods={t.methods}, properties={t.properties}");
        console.print($"constructors={t.constructors}");
        console.print($"multimethods={t.multimethods}");

        Method method = Point.foo;
        console.print($"method={method}");
        console.print($|method.invoke()=\
                         |{method.as(Method<Point<Number>, Tuple<>, Tuple<Int>>).
                         |   invoke(p, Tuple:())[0]}
                        );

        for (val constructor : t.constructors) {
            switch (constructor.params.size) {
            case 1:
                Class<public Point, protected Point, private Point, struct Point> clz = Point;
                assert (struct Point) structure := clz.allocate();
                structure.x = Int:1;
                structure.y = Int:2;
                Tuple<Point<Int>> p2 = constructor.invoke(Tuple:(structure));
                console.print($"construct(structure)={p2[0]}");
                break;

            case 2:
                Tuple<Point<Int>> p2 = constructor.invoke((Int:1, Int:2));
                console.print($"construct(1,2)={p2[0]}");
                break;

            case 3:
                Tuple<Point<Int>> p3 = constructor.invoke((Int:1, Int:2, "there"));
                console.print($"construct(1,2,\"there\")={p3[0]}");
                break;
            }
        }
    }

    void testInvokeAsync() {
        console.print("\n** testInvokeAsync");

        DelayService svc = new DelayService();

        function Int (Duration) calc = svc.calcSomethingBig;

        console.print("calling sync");
        Tuple resultS = calc.invoke(Tuple:(Duration.ofMillis(10)));
        console.print(resultS[0]);

        console.print("calling sync &Future.get()");
        @Future Tuple resultS2 = calc.invoke(Tuple:(Duration.ofMillis(10)));
        console.print(&resultS2.get());

        console.print("calling async");
        @Future Tuple resultA = calc.invoke(Tuple:(Duration.ofMillis(20)));
        &resultA.whenComplete((t, e) -> {
            console.print($"complete {t?[0] : assert}");
        });
        console.print($"assigned={&resultA.assigned}, result={resultA[0]}, assigned={&resultA.assigned}");

        service DelayService {
            Int calcSomethingBig(Duration delay) {
                @Inject Clock clock;
                @Future Int   result;

                console.print($"delay {delay}");
                clock.schedule(delay, () -> {result=delay.milliseconds;});
                return result;
            }
        }
    }

    void testBind() {
        import ecstasy.collections.ListMap;
        import ecstasy.reflect.Parameter;

        console.print("\n** testBind");

        function void (Int, String, Boolean) log =
            (i, v, f) -> console.print($"[{i}] {v} {f}");

        Parameter<Int>     param0 = log.params[0].as(Parameter<Int>);
        Parameter<String>  param1 = log.params[1].as(Parameter<String>);
        Parameter<Boolean> param2 = log.params[2].as(Parameter<Boolean>);

        // single bind
        function void (Int, Boolean) hello = log.bind(param1, "hello").as(function void (Int, Boolean));
        hello(0, True);

        // multi partial bind
        Map<Parameter, Object> paramsPartial = new ListMap();
        paramsPartial.put(param0, 1);
        paramsPartial.put(param1, "world");

        function void (Boolean) fnPartial = log.bind(paramsPartial).as(function void (Boolean));
        fnPartial(True);

        // multi full bind
        Map<Parameter, Object> params = new ListMap();
        params.put(param0, 1);
        params.put(param1, "world");
        params.put(param2, True);

        function void () fn = log.bind(params);
        fn();
    }

    void testChildTypes() {
        console.print("\n** testChildTypes");

        Type[] types = [Nullable, Map, ecstasy.collections.HashMap, Type, Class];
        for (Type type : types) {
            console.print($"{type} children: {type.childTypes}");
        }
    }

    void testTypeTemplate() {
        console.print("\n** testTypeTemplate");

        Type t = String;
        TypeTemplate tt = t.template;
        console.print($"type={t}; template={tt}");
    }

    void testEnum() {
        console.print("\n** testEnum");

        console.print($"Boolean.count={Boolean.count}");
        console.print($"Boolean.values={Boolean.values}");
        console.print($"Boolean.names={Boolean.names}");
    }

    void testStruct() {
        console.print("\n** testStruct");

        Point p = new Point(3,4);
        analyzeStructure(p);

        const Point3D(Int x, Int y, Int z) extends Point(x, y);
        Point3D p3d = new Point3D(5,6,7);
        analyzeStructure(p3d);

        if (Point3D.StructType p3s := &p3d.revealAs(Point3D.StructType)) {
            Point3D p3d2 = Point3D.instantiate(p3s);
            assert p3d2 == p3d;
        }
    }

    void testClass() {
        console.print("\n** testClass");

        Class c1 = Map;
        analyzeClass(c1);

        Class c2 = ecstasy.collections.ListMap;
        analyzeClass(c2);

        Class c3 = Map<Int, String>;
        analyzeClass(c3);

        Class c4 = ecstasy.collections.ListMap<Date, TimeOfDay>;
        analyzeClass(c4);

        Class c7 = ecstasy.collections.ListMap<Date, TimeOfDay>.Entries;
        analyzeClass(c7);

        Map<Int, String> map = new ecstasy.collections.ListMap();
        analyzeStructure(map);

        Boolean f = True;
        Class   c = True;
        Type    t = True;
    }

    void analyzeClass(Class clz) {
        console.print($"Analyzing: {clz}");
    }


    void analyzeStructure(Object o) {
        console.print($"Analyzing: {o}");

        Type t = &o.actualType;
        console.print($"Type={t}");

        if (Class c := t.fromClass()) {
            console.print($"Class={c}");
            console.print($"PublicType={c.PublicType}");
            console.print($"StructType={c.StructType}");
            console.print($"formalTypes={c.formalTypes}");

            Type ts = c.StructType;
            for (val prop : ts.properties) {
                // property must have a field, must not be injected, not constant/formal
                console.print($|prop={prop.name}, constant={prop.isConstant()}, readOnly={prop.readOnly}
                                 |     hasUnreachableSetter={prop.hasUnreachableSetter}, formal={prop.formal}
                                 |     hasField={prop.hasField}, injected={prop.injected}, lazy={prop.lazy}
                                 |     atomic={prop.atomic}, abstract={prop.abstract},
                               );

                // need to get a Ref for the property:
                // - must be assigned
                // - actual type cannot be Service
                val ref = prop.of(o);
                console.print($|     assigned={ref.assigned}, peek()={ref.peek()}, actualType={ref.actualType}
                                 |     isService={ref.isService}, isConst={ref.isConst}
                                 |     isImmutable={ref.isImmutable}, hasName={{String name = "n/a"; name := ref.hasName(); return name;}}, var={ref.is(Var)}
                               );
            }

            if (val s := &o.revealAs(Struct)) {
                Object clone = c.instantiate(s);
                console.print($"clone={clone}");
            }
        }
    }

    void testTypeSystem() {
        console.print("\n** testTypeSystem");

        TypeSystem ts = this:service.typeSystem;
        console.print($"current TypeSystem={ts}");
        console.print($"modules              : {ts.modules              }");
        console.print($"sharedModules        : {ts.sharedModules        }");
        console.print($"moduleBySimpleName   : {ts.moduleBySimpleName   }");
        console.print($"moduleByQualifiedName: {ts.moduleByQualifiedName}");

        console.print("modules:");
        for (Module _module : ts.modules) {
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
                "@AutoFreezable Array<Int64>",
                "HashMap<String?, @AutoFreezable Array<Int64>>",
                "HashMap<String?, @AutoFreezable Array<Int64>>.Entry",
                ];

        for (String name : names) {
            try {
                if (Class clz := ts.classForName(name)) {
                    console.print($"class for \"{name}\"={clz}");
                } else {
                    console.print($"no such class: \"{name}\"");
                }
            } catch (Exception e) {
                console.print($"exception occurred lookup up class \"{name}\"; exception={e}");
            }
        }
    }

    void displayModule(Module _module) {
        console.print($"module \"{_module.simpleName}\" (\"{_module.qualifiedName}\")");
        val deps = _module.modulesByPath;
        if (!deps.empty) {
            console.print($" - dependencies:");
            for ((String path, Module dep) : deps) {
                console.print($"    - \"{path}\" => \"{dep.qualifiedName}\"");
            }
        }

        console.print(" - contents:");
        displayPackage(_module, "   ");
    }

    void displayPackage(Package pkg, String prefix = "") {
        prefix += " |-";
        for (Class child : pkg.classes) {
            console.print($"{prefix} {child.name}");
            if (child.implements(Package), Object instance := child.isSingleton()) {
                displayPackage(instance.as(Package), prefix);
            }
        }
    }

    class Container<A, B, C> {
        class Containee<D, E, F> {}
    }

    void testTypes() {
        console.print("\n** testTypes");
 {
        Type t1 = Map;
        Type t2 = Int;
        Type t3 = t1.parameterize([t2]);
        console.print($"{t1} < {t2} > = {t3}");
        assert t3 == Map<Int>;
        }
 {
        Type t1 = Map;
        Type t2 = String;
        Type t3 = Int;
        Type t4 = t1.parameterize([t2, t3]);
        console.print($"{t1} < {t2}, {t3} > = {t4}");
        assert t4 == Map<String, Int>;
        }
 {
        Type t1 = Map;
        Type t2 = Hashable;
        Type t3 = t1 + t2;
        console.print($"{t1} + {t2} = {t3}");
        assert t3 == Map + Hashable;
        }
 {
        Type t1 = Map;
        Type t2 = Set;
        Type t3 = t1 | t2;
        console.print($"{t1} | {t2} = {t3}");
        assert t3 == Map | Set;
        }
 {
        Type t1 = HashMap;
        Type t2 = Map;
        Type t3 = t1 - t2;
        console.print($"{t1} - {t2} = {t3}");
        assert t3 == HashMap - Map;
        }
 {
        val container = new Container<String,Int,Char>();
        val containee = container.new Containee<Char,String,Map<Int, String>>();
        console.print($"Container<String,Int,Char>.Containee<Char,String,Map<Int, String>> = {&containee.actualType}");
        }
    }
}