package innerOuterTests {

    void run() {
        testSimple();
        testOuterIdentity();
        testStaticIface();
        testAnonInner();
        testFunky();
        simpleChild.testChildOverride();
        simpleChild.testInheritedChild();
        derivedChild.testChildSuper();
        funkyChild.testInheritedFunction();
        funkyChild.testInheritedFunctionHandle();
        genericFunkyChild.testInheritedFunction();
        composedState.testState();
        composedState.testStructField();
        composedProperties.testAccessors();
        deeperChild.testComposition();
        genericChild.testComposition();
        genericChild.testChildParameter();
        genericChild.testPrimitiveChildParameter();
        abstractChild.testImplementation();
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
        Parent<String> parent = new Parent("P1");
        Parent<String>.Child child1 = parent.new Child("hello");
        Parent<String>.Child child2 = parent.new Child("world");

        assert child1.outer.name == "P1";
        assert child1.e == "hello";
        assert child2.e == "world";
        // the child interface reverses the outer Element type's natural ordering
        assert child1 > child2;
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
            assert &child.type == ParentInherited.Child;

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

    package funkyChild {
        void testInheritedFunction() {
            Derived parent = new Derived();
            Derived.C1 child = parent.new C1();

            // CompileType is Derived.C1, but measure() is declared on Derived.C0;
            // Java Derived_C1 does not extend Derived_C0; the call must select Derived_C0 directly
            assert Derived.C1.measure(child) == 2;
            assert measureFormal(child) == 2;
        }

        void testInheritedFunctionHandle() {
            function String() label = Derived.C1.label;
            assert label() == "Base.C0";

            function Int(Int) twice = Derived.C1.twice;
            assert twice(21) == 42;

            // a widened return uses the boxed entry point instead of the optimized long return
            Object result = invokeBoxed(twice, 21);
            assert result.is(Int);
            assert result == 42;
        }

        <Value extends Measurable> Int measureFormal(Value value) = Value.measure(value);

        Object invokeBoxed(function Object(Int) fn, Int value) = fn(value);

        interface Measurable {
            static <CompileType extends Measurable> Int measure(CompileType value);
        }

        class Base {
            class C0 implements Measurable {
                static String label() = "Base.C0";

                static Int twice(Int value) = value * 2;

                @Override
                static <CompileType extends C0> Int measure(CompileType value) = 1;
            }

            class C1 extends C0 {}
        }

        class Derived extends Base {
            @Override
            class C0 {
                @Override
                static <CompileType extends C0> Int measure(CompileType value) {
                    assert CompileType == Derived.C1;
                    return 2;
                }
            }

            @Override
            class C1 {}
        }
    }

    package genericFunkyChild {
        void testInheritedFunction() {
            // the inherited measure() call must preserve the enclosing parent's type argument
            Derived<Int>    parent = new Derived();
            Derived<Int>.C1 child  = parent.new C1();
            assert Derived<Int>.C1.measure(child) == 2;

            Derived<String>    textParent = new Derived();
            Derived<String>.C1 textChild  = textParent.new C1();
            assert Derived<String>.C1.measure(textChild) == 2;
        }

        interface Measurable {
            static <CompileType extends Measurable> Int measure(CompileType value);
        }

        class Base<Element> {
            class C0 implements Measurable {
                @Override
                static <CompileType extends C0> Int measure(CompileType value) = 1;
            }

            class C1 extends C0 {}
        }

        class Derived<Element> extends Base<Element> {
            @Override
            class C0 {
                @Override
                static <CompileType extends C0> Int measure(CompileType value) = 2;
            }

            @Override
            class C1 {}
        }
    }

    package composedState {
        void testState() {
            Derived parent = new Derived();
            Derived.C1 child = parent.new C1(10);
            Base.C1 baseView = child;
            Derived.C0 refinedView = child;

            // both views and a returned "this" must denote the very same object, not delegates
            assert &baseView == &refinedView;
            val self = child.self();
            assert &child == &self;
            Outer outer = child.outer;
            assert &outer == &parent;

            // D.C0 adds storage that D.C1 cannot inherit through its Java superclass B.C1
            assert refinedView.extra == 8;
            assert child.advance(2) == 20;
            assert baseView.value == 12;
            refinedView.extra = 20;
            assert baseView.advance(3) == 35;
            assert child.value == 15;
            // an ordinary virtual call from a body restarts dispatch; private calls remain bound
            // to their declarations on C0 and C1
            assert refinedView.callAdvance(1) == 36;
            assert child.localAdjustment() == -1000;

            // construction and validators must also enter D.C0 on the same D.C1 object
            Boolean rejected = False;
            try {
                parent.new C1(-1);
            } catch (IllegalState e) {
                rejected = True;
            }
            assert rejected as "Construction should fail";
        }

        void testStructField() {
            // the refined C0 constructor accesses B.C0's field through an interface receiver
            StructDerived.C0 child = new StructDerived().new C0(5);
            assert child.value == 6;

            // inherited C1 construction must also enter the derived C0 constructor
            StructDerived.C1 inherited = new StructDerived().new C1(5);
            assert inherited.value == 6;
        }

        class Base {
            class C0 {
                construct(Int seed) {
                    value = seed;
                }

                Int value;

                assert() {
                    assert value >= 0;
                }

                Int advance(Int step) {
                    value += step;
                    return value;
                }

                C0 self() = this;
            }

            class C1 extends C0 {
                construct(Int seed) {
                    construct C0(seed);
                }
            }
        }

        class Derived extends Base {
            @Override
            class C0 {
                construct(Int seed) {
                    construct Base.C0(seed);
                    ++extra;
                }

                Int extra = initialExtra();

                assert() {
                    assert extra > 0;
                }

                private static Int initialExtra() = 7;

                @Override
                Int advance(Int step) {
                    // a child function handle targets its implementation class, not its interface
                    function Int() initial = initialExtra;
                    assert initial() == 7;

                    // a refined body binds its fixed predecessor, not the virtual head
                    function Int(Int) next   = super;
                    function Int()    adjust = adjustment;

                    // a nested lambda must accept this D.C1 object through the D.C0 interface
                    function Int() readExtra = () -> extra;
                    assert readExtra()  == adjust();
                    assert adjustment() == adjust();

                    // an ordinary bound method must still enter the callable interface
                    function C0() readSelf = self;
                    C0 viaMethod = readSelf();

                    assert &viaMethod == &this;
                    return next(step) + adjust();
                }

                Int callAdvance(Int step) = advance(step);

                @Override
                C0 self() = super();

                private Int adjustment() = extra;
            }

            @Override
            class C1 {
                construct(Int seed) {
                    construct Base.C1(seed);
                }

                Int localAdjustment() = adjustment();

                private Int adjustment() = -1000;
            }
        }

        class StructDerived extends Base {
            @Override
            class C0 {
                construct(Int seed) {
                    construct Base.C0(seed);

                    Int initial = value;
                    value = initial + 1;
                }
            }
        }
    }

    package composedProperties {
        void testAccessors() {
            Derived.C1 child = new Derived().new C1();
            Derived.C0 refined = child;

            // a getter super call traverses the same composition as an ordinary method
            assert child.score == 111;
            refined.score = 20;
            assert child.raw == 25;
            assert child.score == 135;

            // multi-slot values introduced by C0 are stored on the very same C1 receiver
            refined.wide = 0x10000000000000000;
            assert child.wide == 0x10000000000000000;
            refined.optional = Null;
            assert child.optional == Null;
            refined.optional = 0x20000000000000000;
            assert child.optional == 0x20000000000000000;

            // optimized results use Ctx's extra return slots even in static bodies with a receiver
            (Int128 wide, Int? optional) = child.measure(0x30000000000000000, Null);
            assert wide == 0x30000000000000001;
            assert optional == Null;
            assert Int found := child.find(True);
            assert found == 25;
            assert !child.find(False);
        }

        class Base {
            class C0 {
                Int raw = 1;
                Int score {
                    Int get() = raw;
                    void set(Int value) {
                        raw = value;
                    }
                }
                (Int128, Int?) measure(Int128 wide, Int? optional) = (wide, optional);
                conditional Int find(Boolean present) {
                    if (present) {
                        return True, raw;
                    }
                    return False;
                }
            }
            class C1 extends C0 {
                @Override
                Int score.get() = super() + 100;

                @Override
                (Int128, Int?) measure(Int128 wide, Int? optional) = super(wide, optional);

                @Override
                conditional Int find(Boolean present) = super(present);
            }
        }

        class Derived extends Base {
            @Override
            class C0 {
                @Override
                Int score {
                    Int get() = super() + 10;
                    void set(Int value) {
                        super(value + 5);
                    }
                }
                Int128  wide     = 0;
                Int128? optional = Null;

                @Override
                (Int128, Int?) measure(Int128 wide, Int? optional) = (wide + 1, optional);
            }
            @Override
            class C1 {}
        }
    }

    package deeperChild {
        void testComposition() {
            // the third child adds a hard-left boundary; the third parent adds fixed predecessors
            Further parent = new Further();
            Base.C2 child = parent.new C2();
            assert child.foo() == "B.C2->E.C1->D.C1->B.C1->E.C0->D.C0->B.C0";

            Outer outer = child.outer;
            assert &outer == &parent;

            // C2 is not declared on either refined parent; its inherited body must still compose
            // C1 and C0 using the runtime parent's refinements
            assert new Derived().new C2().foo() == "B.C2->D.C1->B.C1->D.C0->B.C0";
        }

        class Base {
            class C0 {
                String foo() = "B.C0";
            }

            class C1 extends C0 {
                @Override
                String foo() = "B.C1->" + super();
            }

            class C2 extends C1 {
                @Override
                String foo() = "B.C2->" + super();
            }
        }

        class Derived extends Base {
            @Override
            class C0 {
                @Override
                String foo() = "D.C0->" + super();
            }

            @Override
            class C1 {
                @Override
                String foo() = "D.C1->" + super();
            }
        }

        class Further extends Derived {
            @Override
            class C0 {
                @Override
                String foo() = "E.C0->" + super();
            }

            @Override
            class C1 {
                @Override
                String foo() = "E.C1->" + super();
            }
        }
    }

    package genericChild {
        void testComposition() {
            Derived<String> strings = new Derived();
            Derived<String>.C1 stringChild = strings.new C1("first");
            assert stringChild.echo("next") == "next";
            assert stringChild.last == "next";
            assert stringChild.calls == 1;

            // the same generic child topology is also compiled with optimized primitive signatures
            Derived<Int> ints = new Derived();
            Derived<Int>.C1 intChild = ints.new C1(1);
            assert intChild.echo(7) == 7;
            assert intChild.last == 7;
            assert intChild.calls == 1;
        }

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

        class Base<Element> {
            class C0 {
                construct(Element initial) {
                    last = initial;
                }
                Element last;
                Element echo(Element value) = value;
            }
            class C1 extends C0 {
                construct(Element initial) {
                    construct C0(initial);
                }
                @Override
                Element echo(Element value) = super(value);
            }
        }

        class Derived<Element> extends Base<Element> {
            @Override
            class C0 {
                construct(Element initial) {
                    construct Base.C0(initial);
                }
                Int calls = 0;
                @Override
                Element echo(Element value) {
                    ++calls;
                    return super(remember(value));
                }

                private Element remember(Element value) {
                    last = value;
                    return value;
                }
            }
            @Override
            class C1 {
                construct(Element initial) {
                    construct Base.C1(initial);
                }
                @Override
                Element echo(Element value) = super(value);
            }
        }
    }

    package abstractChild {
        void testImplementation() {
            // the hard-left child contract remains abstract; a refined C0 supplies the body
            assert new Derived().new C1().value() == 42;
        }

        @Abstract
        class Base {
            class C0 {
                Int value();
            }
            class C1 extends C0 {}
        }

        class Derived extends Base {
            @Override
            class C0 {
                @Override
                Int value() = 42;
            }
            @Override
            class C1 {}
        }
    }
}
