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

