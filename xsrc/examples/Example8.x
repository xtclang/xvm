// the "global" namespace is composed of the union of the top-level namespace and the "inner"
// namespace of each component in the global namespace.
//
// modifiers for "top-level" namespace structures:
// - "top-level" means nested within a file, module, or package structure
// - static means "singleton"
// - public means visible outside of the module
// - protected means t.b.d.
// - private means no visibility outside of the module
//
//              public      protected   private     static
//              ----------  ----------  ----------  ----------
// module       (implicit)                          (implicit)
// package      x           x           x           (implicit)
// class        x           x           x
// interface    x           x           x
// service      x           x           x           x
// const        x           x           x           x
// enum         x           x           x           (implicit)
// trait        x           x           x
// mixin        x           x           x
//
// modifiers for "inner" namespace structures:
// - "inner" means nested within a class
// - static means "no ref to parent, no virtual new"; it only applies to something that can be new'd
//   or init'd from a constant, so it does not apply to interface, trait, or mixin
//
//              public      protected   private     static
//              ----------  ----------  ----------  ----------
// class        x           x           x           x
// interface    x           x           x
// service      x           x           x           x - required if parent is not const or service
// const        x           x           x           x - required if parent is not const
// enum         x           x           x           (implicit)
// - enum val                                       (implicit)
// trait        x           x           x
// mixin        x           x           x

// modifiers for "local" namespace structures:
// - "local" means declared within a method; items declared within a method are not visible outside
//   of (above on the hierarchy) the method
// - static means "no ref to the method frame", i.e. no ability to capture, not even the "this"
//   from the method
//
//              public      protected   private     static
//              ----------  ----------  ----------  ----------
// class                                            x
// interface
// service                                          x
// const                                            x
// enum                                             x
// - enum val
// trait
// mixin
//

Boolean global;
Boolean innerClass;
enum DeclarationZone {TopLevel, Class, Method}
Zone    zone;


Void foo()
    {
    Int i = 4;

    const Point(Int x, Int y)
        {
        Int adjustX()
            {
            return x + i;   // CANNOT CAPTURE "i" from within a const!!!
            }
        }
    }


module MyTopSecretCalc
    {
    package MyApp import:required MyApp;

    static Int calc(Int x, Int y)
        {
        return x * y - MyApp.OFFSET;
        }
    }

module MyApp
    {
    package Calc import:embedded MyTopSecretCalc;

    protected static Int OFFSET = 3;
    }

// annotations

public @AnnoType(parm) @AnnoType2 static class Dohickey
        extends Base
        implements Functionality
        // ...
    {
    // ...
    }

// translates to ...
class Dohickey
    extends Base
    implements Functionality
    incorporates AnnoType2
    incorporates AnnoType(parm)


class MyMap
    {
    static service MyEntry
        {
        // hidden
        MyEntry(MyMap parent)
            {
            this.parent = parent;
            }

        Void foo()
            {
            v = MyMap.this.bar();


            Int j = 4;

            class Thingie
                {
                print(v);
                print(j);
                }
            }

        Int k;
        Int v;
        }

    Int bar()
        {
        return ++i
        };

    Int i;
    }

// ----- imports

// simple
module MyApp
    {
    package Q import q.sourceforge.net;
    }

// multiple
module MyApp
    {
    package A
        {
        package Q1 import q.sourceforge.net;
        }

    package B
        {
        package Q2 import q.sourceforge.net;
        }
    }

// conditional
module MyApp
    {
    package Spring import:optional springframework.spring.io;

    package A
        {
        if (Spring.present)
            {
            package Q1 import q.sourceforge.net;
            }
        }

    package B
        {
        // since this is NOT conditional, it should override the other import
        // w.r.t. the import (but not w.r.t. the "Q1" name), i.e. Q1 will still
        // be conditional, but the fingerprint is NOT conditional
        package Q2 import q.sourceforge.net;
        }
    }

// conditional #2
module MyApp
    {
    package Spring import:optional springframework.spring.io;
    package Hibernate import:optional hibernate.jboss.org;

    package A
        {
        if (Spring.present)
            {
            package Q1 import q.sourceforge.net;
            }
        }

    package B
        {
        // the resulting module fingerprint for "q" should be conditional on:
        // "Spring.present || Hibernate.present"
        if (Hibernate.present)
            {
            package Q2 import q.sourceforge.net;
            }
        }
    }
// fingerprint
if (Spring.present | Hibernate.present)
    {
    module q.sourceforge.net {...}
    }

// remapping conditions
class D
    if (C1) { extends B1 }
    else if (C2) { extends B2 }
    else { extends B3 }
// translates to
if (C1)
    {
    class D
        extends B1
    }
if (!C1 & C2)
    {
    class D
        extends B2
    }
if (!C1 & !C2)
    {
    class D
        extends B3
    }

// remapping multiple conditions
class D
    if (C1) { extends B1 }
    else if (C2) { extends B2 }
    else { extends B3 }
    if (C1) { implements I1 }
    else if (C2) { implements I2 }
    else { implements I3 }
// translates to
if (C1)
    {
    class D
        extends B1
        implements I1
    }
if (!C1 & C2)
    {
    class D
        extends B2
        implements I2
    }
if (!C1 & !C2)
    {
    class D
        extends B3
        implements I3
    }

// remapping multiple conditions but with different conditions
class D
    if (C1) { extends B1 }
    else { extends B2 }
    if (C2) { implements I1 }
    else { implements I2 }
// translates to
if (C1 & C2)
    {
    class D
        extends B1
        implements I1
    }
if (C1 & !C2)
    {
    class D
        extends B1
        implements I2
    }
if (!C1 & C2)
    {
    class D
        extends B2
        implements I1
    }
if (!C1 & !C2)
    {
    class D
        extends B2
        implements I2
    }


// import expansion

import a.b.c; // c -> a.b.c
import c.d.e; // e -> a.b.c.d.e

// ----- type methods, "this type", and type parameters ------------------------

// simple example
class B
    {
    Void foo()
    }
class D extends B
    {
    Void foo()
    Void bar()
    }
[17] TypeMethod{Void foo()}
[18] TypeMethod{Void bar()}

// return type auto-narrowing
class B
    {
    B foo()         // 17
    }
class D1 extends B
    {
    // D1 foo()     // 17
    }
class D2 extends B
    {
    D2 foo()        // 17
    }
class D3 extends B
    {
    B foo()         // 18
    }
class D4 extends B
    {
    D4! foo()       // 19
    }
[17] TypeMethod{this:type foo()}
[18] TypeMethod{B foo()}
[19] TypeMethod{D4 foo()}

// sub-class return type
class B
    {
    D foo()     // compiles as if it were D! foo()
    }
class D extends B
    {
    // implicit D! foo()
    }
class D2 extends D
    {
    // implicit D! foo()
    }

// sub-class return type with auto-narrowing
class B
    {
    D foo()     // compiles as if it were D! foo()
    }
class D extends B
    {
    D foo()     // compiles as if it were D foo() -- auto-narrowing
    }
class D2 extends D
    {
    // implicit D2 foo
    }

// explicitly non-narrowed sub-class return type
class B
    {
    D! foo()    // redundant. possibly a compiler warning or error?
    }
class D extends B
    {
    // implicit D foo
    }
class D2 extends D
    {
    // implicit D foo()
    }


// parents and grandparents
class B<T>
    {
    // 1. how do we refer to "B"? ClassTypeConstant(ThisClassConstant()) or ThisTypeConstant() (which also carries type param info)
    // 2. how do we refer to "B!"? ClassTypeConstant(ClassConstant("B"))
    // 3. how do we refer to "T"?
    // 3.a - declaration:  ParameterTypeConstant(ThisTypeConstant(), "T")
    // 3.b - between the curlies: ParameterTypeConstant(ThisTypeConstant(), "T")
    // 3.c - between the curlies for a parameter named "that": ParameterTypeConstant(RegisterConstant("that"), "T")
    // 4. how do we refer to "B<T>"? ClassTypeConstant(ThisClassConstant(), ParameterTypeConstant(RegisterConstant("this"), "T"))
    // 5. how do we refer to "B!<T>"? ClassTypeConstant(ClassTypeConstant(ClassConstant("B")), ParameterTypeConstant(RegisterConstant("this"), "T"))
    // 6. how do we refer to "Node"? TODO
    // 7. how do we refer to "Node!"? TODO

    B fubar();              // what does "B" compile as? see (1) above: ClassTypeConstant(ThisClassConstant())

    Void foo(T t, B that, B<T> that2)   // type of "T" see (3) above: ParameterTypeConstant(ThisTypeConstant(), "T")
                            // type of "B" is ClassTypeConstant(ThisClassConstant)
                            // type of "B<T>" is ThisTypeConstant
        {
        B b0 = this;        // type of b0 is CTC("B") (not some auto-narrowing B)
        B b1 = that;

        // note: "T" is implicitly "this.T"
        T t2 = t;           // type of "t2" (spec'd in NVAR op) is ParameterTypeConstant(ThisTypeConstant, "T")
        that.T t3 = ...;    // type of "t3" (spec'd in NVAR op) is ParameterTypeConstant(RegisterConstant(1), "T")      // 1="that"
        }

    <Q> Q extract(Q[] array, Int i) // Q is RegisterTypeConstant(0)                                                     // 0="Q"
    // compiles as if it were (illegal source code): Q extract(Type Q, Q[] array, Int i)
        {
        Q someQ = array[i]; // Q here is a type, so someQ is NVAR of RegisterTypeConstant(0)


        Q q0 = q[0];
        Q q1 = q[1];
        if (q1 == q0) throw ISE();
        return array[i];
        }
    // ways to invoke the above method
    Animal[] animals = {new Dog(), new Cat(), new Pig()};
    Animal a = extract(animals, 5);
    Object o = extract(animals, 5);
    Object o = extract<Object>(animals, 5);


    Node bar();             // "this.type".Node

    class Node
        {
        B<T> f();           // ParentTypeConstant(ThisTypeConstant())
        B foo();            // ClassTypeConstant(ParentClassConstant(ThisClassConstant()))
        B<String> x;        // ClassTypeConstant(ParentClassConstant(ThisClassConstant()), CTC(CC("String")))
        T bar();            // ParameterTypeConstant(ParentTypeConstant(ThisTypeConstant()), "T")

        Child<String> y;    // ChildTypeConstant(ParentTypeConstant(ThisTypeConstant()), "Child", CTC(CC("String")))

        T biggerOf(B<T> that)   // what does "T" compile as? what does "B<T>" compile as?
            {
            // ...
            }

        Void doSomething(B    that1,   // "B" is a type constant of ... ???
                         B<T> that2)   // "B<T>" is a type constant "ParentTypeConstant(ThisTypeConstant)"
            {
            }

        Void doSomething(B that)
            {
            this.T t0 = this.bar();     // NVAR PTC("this:parent", "T"), "t0"
            that.T t1 = that.bar();     // NVAR PTC(register1, "T"), "t1"
            // ...
            foo(t0);        // OK
            foo(t1);        // error!!!!
            that.foo(t0);   // error!!!!
            that.foo(t1);   // OK

            that.T t2 = t1;
            that.foo(t2);

            // but it gets more complicated ...
            if (t2.instanceof(B<T>))
                {
                t2.T t3 = t2.bar().bar();
                }

            // "B.this" is of a compile-time type of "ParentTypeConstant(ThisTypeConstant)"
            // "B.this" is of a compile-time type of "ParentTypeConstant(RegisterTypeConstant("this"))"
            // "B.that" is of a compile-time type of "RegisterTypeConstant("that")"

            // "relational" type constant from "parent" type constant from "this:type"
            B.this.Child c1 = ...
            // what is this one? it's not the same type ...
            B.that.Child c2 = ...
            }
        }

    class Child<Z>
        {
        }
    }

class D<T> extends B<T>
    {
    class Node
        {
        // B foo();         // "this:parent"."this:type"
        // T bar();         // "this:parent"."T"
        }
    }
