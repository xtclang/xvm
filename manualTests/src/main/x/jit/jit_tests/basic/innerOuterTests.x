package innerOuterTests {

    void run() {
        testSimple();
        testOuterIdentity();
        testStaticIface();
        testAnonInner();
        testFunky();
        simpleChild.testChildOverride();
//        TODO: handle complex virtual child call chains
//        simpleChild.testInheritedChild();
//        derivedChild.testChildSuper();
        genericChild.testChildParameter();
        genericChild.testPrimitiveChildParameter();
    }

    void testSimple() {
        Base parent1 = new Base("Hello");
        Base parent2 = new Base("World");
        Base[] parents = [parent1, parent2];

        for (Base parent : parents) {
            Base.Child child = parent.new Child();
            assert child.textByOuter == parent.text;
            assert child.textByName == parent.text;
            assert child.outer == parent;
        }
    }

    interface IfaceOuter {
        void fnOuter();

        static interface IfaceInner {
            String fnInner();
        }
    }

    class Base(String text)
            implements IfaceOuter {
        class Child() {
            String textByName.get() = this.Base.text;

            String textByOuter.get() = outer.text;
        }

        @Override
        void fnOuter() {}

        static const StaticChild(String name)
                implements IfaceOuter.IfaceInner {
            @Override
            String fnInner() = name;
        }
    }

    void testOuterIdentity() {
        new IdentityParent().new Child().testOuter();
    }

    class IdentityParent {
        class Child() {
            void testOuter() {
                val   parent = outer;
                Outer named  = this.Outer;
                assert &parent == &named;
                assert &named == &outer;
            }
        }
    }

    void testStaticIface() {
        IfaceOuter.IfaceInner child = new Base.StaticChild("NonVirtual");
        assert child.fnInner() == "NonVirtual";
    }

    void testAnonInner() {
        class Inner {
            construct(String text) {}
        }

        Int count = 4;
        var inner = new Inner("hello") {
            void run() {
                ++count;
            }
        };

        inner.run();
        assert count == 5;
    }

    void testFunky() {
        // TODO: child construction throws NoSuchMethodError for
        // Child.$new(Ctx, TypeConstant, nObject, String)
        // Parent<String> parent = new Parent("P1");
        // Parent<String>.Child child1 = parent.new Child("hello");
        // Parent<String>.Child child2 = parent.new Child("world");
        //
        // assert child1.outer.name == "P1";
        // assert child1.e == "hello";
        // assert child2.e == "world";
        // // the child interface reverses the outer Element type's natural ordering
        // assert child1 > child2;
    }

    interface FunkyOuter<Element extends Orderable> {
        String name;

        interface FunkyInner
                extends Orderable {
            @RO Element e;

            static <CompileType extends FunkyInner> Ordered
                    compare(CompileType value1, CompileType value2) {
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
        }
    }

    package simpleChild {
        void testChildOverride() {
            assert new ParentBase().childReport() == "Base.Child";
            // the inherited method must construct the child belonging to the runtime parent
            assert new ParentDerived().childReport() == "Derived.Child";

            // direct construction also uses the base constructor contract, then narrows its result
            ParentDerived.Child child = new ParentDerived().new Child();
            assert child.report() == "Derived.Child";
        }

        void testInheritedChild() {
            // no Child declaration: construction must still select this runtime parent's child
            ParentInherited parent = new ParentInherited();
            ParentBase base = parent;
            ParentBase.Child child = base.createChild();
            // TODO: Ref.type is not yet implemented by nRef
            // assert &child.type == ParentInherited.Child;
            Outer outer = child.outer;
            assert &outer == &parent;
            assert child.report() == "Derived.Child";

            ParentInherited.Child direct = parent.new Child();
            outer = direct.outer;
            assert &outer == &parent;
            assert direct.report() == "Derived.Child";

            // the same declared child can be inherited through multiple parents
            ParentFurther further = new ParentFurther();
            child = further.createChild();
            outer = child.outer;
            assert &outer == &further;
            assert child.report() == "Derived.Child";

            // the runtime parent introduces a generic parameter absent from the child's declaration
            GenericParent<String> genericParent = new GenericParent();
            base = genericParent;
            child = base.createChild();
            // assert &child.type == GenericParent<String>.Child;
            outer = child.outer;
            assert &outer == &genericParent;
        }

        class ParentBase {
            String childReport() = new Child().report();

            Child createChild() = new Child();

            class Child {
                String report() = "Base.Child";
            }
        }

        class ParentDerived extends ParentBase {
            @Override
            class Child {
                @Override
                String report() = "Derived.Child";
            }
        }

        class ParentInherited extends ParentDerived {}

        class ParentFurther extends ParentInherited {}

        class GenericParent<Element> extends ParentBase {}
    }

    package derivedChild {
        // ParentBase (PB) declares C1 extending C0. ParentDerived (PD) overrides these virtual
        // children, so an inherited PB.C1 method runs in a child chain composed for PD.
        // Its super call must reach PD.C0 before PB.C0, not follow only PB's declared hierarchy.
        //
        // The trace suffix indicates which derived children override the method: the first
        // digit is C0, the second is C1. These combinations exercise the following chains:
        //   trace11: PD.C1 -> PB.C1 -> PD.C0 -> PB.C0
        //   trace10:          PB.C1 -> PD.C0 -> PB.C0
        //   trace01: PD.C1 -> PB.C1 ->          PB.C0
        // BoundC1 checks the same composition when super is bound to a function before calling it.
        //
        // ParentDerived2 omits C1 entirely; its inherited C1 must still reach its overridden C0.
        // ParentDerived3 omits C0 entirely; its overridden C1 must reach the inherited base C0.
        // Omitting a child declaration must behave like declaring it without a method override.
        void testChildSuper() {
            ParentBase    pb = new ParentBase();
            ParentDerived pd = new ParentDerived();
            assert pb.new C1().trace11() == "PB.C1->PB.C0->";
            assert pd.new C1().trace11() == "PD.C1->PB.C1->PD.C0->PB.C0->";
            assert pd.new C1().trace10() == "PB.C1->PD.C0->PB.C0->";
            assert pd.new C1().trace01() == "PD.C1->PB.C1->PB.C0->";

            assert pd.new BoundC1().trace11() == "PD.BC1->PB.BC1->PD.C0->PB.C0->";

            assert new ParentDerived2().new C1().trace10() == "PB.C1->PD.C0->PB.C0->";
            assert new ParentDerived3().new C1().trace01() == "PD.C1->PB.C1->PB.C0->";
        }

        class ParentBase {
            class C0 {
                String trace11() = "PB.C0->";
                String trace10() = "PB.C0->";
                String trace01() = "PB.C0->";
            }

            class C1 extends C0 {
                @Override
                String trace11() = "PB.C1->" + super();

                @Override
                String trace10() = "PB.C1->" + super();

                @Override
                String trace01() = "PB.C1->" + super();
            }

            class BoundC1 extends C0 {
                @Override
                String trace11() {
                    function String() next = super;
                    return "PB.BC1->" + next();
                }
            }
        }

        class ParentDerived extends ParentBase {
            @Override
            class C0 {
                @Override
                String trace11() = "PD.C0->" + super();

                @Override
                String trace10() = "PD.C0->" + super();
            }

            @Override
            class C1 {
                @Override
                String trace11() = "PD.C1->" + super();

                @Override
                String trace01() = "PD.C1->" + super();
            }

            @Override
            class BoundC1 {
                @Override
                String trace11() {
                    function String() next = super;
                    return "PD.BC1->" + next();
                }
            }
        }

        class ParentDerived2 extends ParentBase {
            @Override
            class C0 {
                @Override
                String trace10() = "PD.C0->" + super();
            }
        }

        class ParentDerived3 extends ParentBase {
            @Override
            class C1 {
                @Override
                String trace01() = "PD.C1->" + super();
            }
        }
    }

    package genericChild {
        void testChildParameter() {
            // this parent has no type parameters; the child itself supplies the type argument
            Holder parent = new Holder();
            Holder.Child<String> child = parent.new Child<String>("first");
            assert child.value == "first";
            assert child.echo("next") == "next";

            Outer outer = child.outer;
            assert &outer == &parent;
        }

        void testPrimitiveChildParameter() {
            Holder parent = new Holder();
            Holder.Child<Int> child = parent.new Child<Int>(17);
            assert child.value == 17;
            assert child.echo(21) == 21;

            Outer outer = child.outer;
            assert &outer == &parent;
        }

        class Holder {
            class Child<Element>(Element value) {
                Element echo(Element value) = value;
            }
        }
    }
}
