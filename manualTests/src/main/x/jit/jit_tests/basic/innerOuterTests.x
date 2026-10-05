package innerOuterTests {

    void run() {
        testSimple();
        testChildOverride();
        testInheritedChild();
        testOuterIdentity();
        testStaticIface();
        testAnonInner();
        testFunky();
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

    void testChildOverride() {
        assert new ParentBase().childReport() == "Base.Child";
        // the inherited method must construct the child belonging to the runtime parent
        assert new ParentDerived().childReport() == "Derived.Child";

        // direct construction also uses the base factory signature, then narrows its result
        ParentDerived.Child child = new ParentDerived().new Child();
        assert child.report() == "Derived.Child";
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

    void testInheritedChild() {
        // no Child declaration: the inherited factory must still create this parent's child
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

        // the parent introduces a generic parameter, but the base factory has no type argument
        GenericParent<String> genericParent = new GenericParent();
        base = genericParent;
        child = base.createChild();
        // assert &child.type == GenericParent<String>.Child;
        outer = child.outer;
        assert &outer == &genericParent;
    }

    class ParentInherited extends ParentDerived {}

    class ParentFurther extends ParentInherited {}

    class GenericParent<Element> extends ParentBase {}

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
}
