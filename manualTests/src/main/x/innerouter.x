module TestInnerOuter {
    @Inject Console console;

    void run() {
        testSimple();
        testStaticIface();
        testAnonInner();
        testFunky();
        testChildNamedInner();
        testVirtualChildConstructorCache();
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
            Int inc() = 0;
        }

        Int i = 4;

        var inner = new Inner("hello") {
            void run() {
                console.print($"in run (i={i})");
                ++i;
            }
        };

        inner.run();

        console.print($"done ({i=})");

        class Outer {
            Inner inner;

            construct() {
                Int count = 0;
                var value = new Inner("") {
                    @Override
                    Int inc() {
                        return ++count;
                    }
                };

                inner = value;
                assert this.toString().size > 0;
            }

            @Override
            String toString() {
                assert as "should no be called";
            }
        }

        Outer o = new Outer();
        assert o.inner.inc() == 1;
        assert o.inner.inc() == 2;
    }

    interface FunkyOuter<Element extends Orderable> {
        String name;

        interface FunkyInner
                extends Orderable {
            @RO Element e;

            static <CompileType extends FunkyInner> Ordered
                    compare(CompileType value1, CompileType value2) {
                @Inject Console console;

                console.print($"{CompileType=}");
                console.print($"{CompileType.OuterType=}");
                console.print($"{CompileType.OuterType.Element=}");
                console.print($"{value1=}");
                console.print($"{value1.outer=}");
                console.print($"{value1.outer.name=}");
                console.print($"{value1.&outer.type=}");

                return (value1.e <=> value2.e).reversed;
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

    // a class with a virtual child implicitly implements ecstasy.reflect.Outer, whose own Inner
    // must not collide with a child of the class named Inner
    class Holder(String text) {
        class Inner {
            String textByOuter.get() {
                return outer.text;
            }
        }
    }

    class Shelf(String label) {
        static const Inner(Int size);

        class Drawer {
            String labelByOuter.get() {
                return outer.label;
            }
        }
    }

    // a subclass's @Override of an inherited Inner overrides it, as for any virtual child
    class Box {
        class Inner {
            String kind() {
                return "box";
            }
        }
    }

    class Crate extends Box {
        @Override
        class Inner {
            @Override
            String kind() {
                return "crate";
            }
        }
    }

    void testVirtualChildConstructorCache() {
        BaseParent.C1 base = new BaseParent().new C1(3);
        assert base.value == 3;
        assert base.order == "base,child";

        BaseParent parent = asBase(new DerivedParent());
        BaseParent.C1 child = parent.new C1(5);
        assert child.value == 5;
        assert child.order == "base,derived,child";
        assert asDerived(child).tag == "ready";

        BaseParent asBase(Object o) = o.as(BaseParent);
        DerivedParent.C0 asDerived(Object o) = o.as(DerivedParent.C0);

        class BaseParent {
            class C0 {
                construct(Int value) {
                    this.value = value;
                } finally {
                    assert this.value == value;
                    order += "base,";
                }

                Int value;
                String order = "";
            }

            class C1 extends C0 {
                construct(Int value) {
                    construct C0(value);
                } finally {
                    assert this.value == value;
                    order += "child";
                }
            }
        }

        class DerivedParent extends BaseParent {
            @Override
            class C0 {
                construct(Int value) {
                    construct BaseParent.C0(value);
                    tag = "ready";
                } finally {
                    assert this.value == value;
                    order += "derived,";
                }

                String tag = "unset";
            }
        }
    }

    void testChildNamedInner() {
        console.print("\n** testChildNamedInner()");

        Holder       holder = new Holder("Held");
        Holder.Inner inner  = holder.new Inner();
        assert inner.textByOuter == "Held";
        assert inner.outer.text == "Held";

        Shelf.Inner item = new Shelf.Inner(3);
        assert item.size == 3;

        Shelf        shelf  = new Shelf("Top");
        Shelf.Drawer drawer = shelf.new Drawer();
        assert drawer.labelByOuter == "Top";

        Box box = new Box();
        assert box.new Inner().kind() == "box";
        Box crate = new Crate();
        assert crate.new Inner().kind() == "crate";
    }
}
