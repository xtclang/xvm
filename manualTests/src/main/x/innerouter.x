module TestInnerOuter {
    @Inject Console console;

    void run() {
        testSimple();
        testStaticIface();
        testAnonInner();
        testFunky();
    }

    interface IfaceOuter {
        void fnOuter();

        static interface IfaceInner {
            void fnInner();
        }
    }

    class Base(String text)
            implements IfaceOuter {
        class Child_V() {
            String textByName.get() {
                return this.Base.text;
            }

            String textByOuter.get() {
                return outer.text;
            }

            void testOuter() {
                val   o1 = outer;
                Outer o2 = this.Outer;
                assert &o1 == &o2;
                assert &o2 == &outer;
                console.print($"this=\"{this}\"; outer=\"{o1}\", type=\"{&o1.Referent}\"");
            }
        }

        @Override
        void fnOuter() {}

        static const Child_NV(String name)
                implements IfaceOuter.IfaceInner {
            @Override
            void fnInner() {
                console.print(" -> fnInner");
            }
        }

        @Override
        String toString() {
            return text;
        }
    }

    void testSimple() {
        console.print("\n** testSimple()");

        Base b1 = new Base("Hello");
        Base b2 = new Base("World");
        Base[] bases = [b1, b2];

        for (Base base : bases) {
            Base.Child_V child = base.new Child_V();
            console.print($"Obtaining text from \"{base}\" child:");
            console.print(" -> by-outer=" + child.textByOuter);
            console.print(" -> by-name=" + child.textByName);
        }

        b1.new Child_V().testOuter();
    }

    void testStaticIface() {
        console.print("\n** testStaticIface()");

        IfaceOuter.IfaceInner childNV = new Base.Child_NV("NonVirtual");
        console.print($"childNV={childNV}");
        childNV.fnInner();
    }

    void testAnonInner() {
        console.print("\n** testAnonInner()");

        class Inner {
            construct(String s) {}
        }

        Int i = 4;

        // var o = new Object()
        var o = new Inner("hello") {
            void run() {
                console.print($"in run (i={i})");
                ++i;
                // foo();
            }
        };

        o.run();

        console.print($"done (i={i})");
    }

    interface FunkyOuter<Element extends Orderable> {
        String name;

        interface FunkyInner
                extends Orderable {
            @RO Element e;

            static <CompileType extends FunkyInner> Ordered
                    compare(CompileType value1, CompileType value2) {
                @Inject Console console;

                console.print($"CompileType={CompileType}");
                console.print($"CompileType.OuterType={CompileType.OuterType}");
                console.print($"CompileType.OuterType.Element={CompileType.OuterType.Element}");
                console.print($"value1={value1}");
                console.print($"value1.outer={value1.outer}");
                console.print($"value1.outer.name={value1.outer.name}");
                console.print($"value1.&outer.actualType={value1.&outer.actualType}");

                // TODO GG: deferred; see explanation in NameExpression.planCodeGen()
                //  return (value1.e <=> value2.e).reversed;
                return CompileType.OuterType.Element.compare(value1.e, value2.e).reversed;
            }
        }
    }

    class Parent<Element extends Orderable>(String name)
            implements FunkyOuter<Element> {
        @Override
        String name;

        class Child(Element e)
                implements FunkyInner {
            @Override
            Element e;

            @Override
            String toString() {
                return "Child: " + e;
            }
        }

        @Override
        String toString() {
            return "Parent of " + Element;
        }
    }

    void testFunky() {
        Parent<String> p = new Parent("P1");
        Parent<String>.Child c1 = p.new Child("hello");
        Parent<String>.Child c2 = p.new Child("world");
        assert c1 > c2;
    }
}
