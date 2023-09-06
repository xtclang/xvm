// resolving outer class names and references
// - it's as if as we walk up the AST tree, that there are 2 pieces of information that we're
//   tracking: (1) what AST node we're on, and (2) if there is still an "instance link" from where
//   we started (once lost, it's lost)
class Outer
    {
    Int prop;

    class InnerInstance
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // ok because Outer.this is available
            }
        }

    static class InnerStatic
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // compiler error because Outer.this is NOT available
            }
        }
    }

// although there is a theoretical exception to this when we know the outer reference because it's
// a singleton
static service Outer
    {
    Int prop;

    class InnerInstance
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // ok because Outer.this is available
            }
        }

    static class InnerStatic
        {
        Void foo()
            {
            Property p = prop!;     // ok because we can resolve the name
            Int      i = prop;      // ok because Outer (singleton instance) is available
            }
        }
    }

// walking down is similar in that it has to keep both (1) an identity and (2) the knowledge of
// whether or not a reference is possible:
// - a "this"
// - an outer "this"
// - a singleton (const / service)
// - value of a property
// - return from a method

// so basically any type expression / class name / etc. that

// lambdas need a way to resolve names and "capture" references:
class Outer
    {
    Int iProp;

    class Inner
        {
        Int iProp2;

        Void foo(Int iParam)
            {
            Int iLocal = 4;

            function Int() f1 = () -> iLocal;   // adds & passes in a hidden parameter for iLocal
            // actually creates:
            // function Int() f1Unbound = (Int iLocalCopy) -> iLocalCopy;
            // function Int() f1 = f1Unbound(iLocal)!;
            //  FBIND f1Unbound 1 0:iLocal

            function Int() f2 = () -> iParam;   // adds & passes in a hidden parameter for iParam

            function Int() f3 = () -> iProp;    // adds & passes in a hidden parameter for Outer.this
            function Int() f4 = () -> iProp2;   // adds & passes in a hidden parameter for Inner.this
            }
        }
    }

// flattening class

class Object
    {
    protected Meta<Object:public, Object:protected, Object:private> meta.get()
    static Boolean equals(Object o1, Object o2)
    String toString()
    Object[] to<Object[]>()
    Tuple<Object> to<Tuple<Object>>()
    @Auto function Object() to<function Object()>()
    }

// how does a sub-class flatten?
class Simple
    {
    // whatever methods it has
    Void foo();
    // on top of Object's
    protected Meta<Simple:public, Simple:protected, Simple:private> meta.get()
    static Boolean equals(Object o1, Object o2) // function is located on Object
    String toString()
    Simple[] to<Simple[]>()
    Tuple<Simple> to<Tuple<Simple>>()
    @Auto function Simple() to<function Simple()>()
    }

// maybe something like: collect(properties, methods, functions, constants, classes)

1) go to super
2)

or ...

1) go through compositions 1 by 1
2)

// weird things to think about

class C<T1, T2>
        implements I<T1>, I<T2>     // ... but this **can't** happen because they share the same property for the type !!!
    {
    // ...
    }

// need to keep track of all of the constituent pieces, and some sort of lineage, kind of like what
// was done for formal params in isA()



interface I1<T extends Number> {T foo();}

interface I2<T extends IntNumber> {T bar();}

class C<T extends IntNumber>    // constraint is required (because at least one is "inherited")
    implements I1<T>, I2<T>     // (constituent pieces cannot be more specific than the this)

mixin M<T>  {...}

class B<T>
    incorporates conditional M<T extends IntNumber>

class D<T extends IntNumber>
    extends B<T>
    implements I1<T>, I2<T>


// so ...
C<Int> c1; // type of C here is parameterized, T is Int, which "isA" IntNumber and "isA" Number
C c2 = c1; // type of C here is not parameterized, but T is _implicitly_ "isA" IntNumber

// for consumer-only type C:
C<String> cs;
C<Object> co;
C c;

c = co;     // ok
c = cs;     // ok
cs = co;    // ok
co = cs;    // err; requires cast (fails at runtime if T is *not* Object, e.g. if T is String)
co = c;     // err; requires cast (fails at runtime if T is *not* Object, e.g. if T is String)
cs = c;     // err; requires cast (fails at runtime if String is not assignable to T, i.e. String or Object)

// for producer-consumer type PC:

PC<Object> pco;
PC<String> pcs;
PC pc;

c = pc;     // ok
c = pco;    // ok
c = pcs;    // ok

co = pc;    // err; requires cast (fails at runtime if T is *not* Object, e.g. if T is String)
co = pco;   // ok (fails at runtime if T is *not* Object, e.g. if T is String)
co = pcs;   // err; will not compile, even with a cast

cs = pc;    // err; requires cast (fails at runtime if String is not assignable to T, i.e. String or Object)
cs = pco;   // ok (fails at runtime if String is not assignable to T, i.e. String or Object)
cs = pcs;   // ok (fails at runtime if T is *not* String)

pco = cs;   // err; requires cast to PC or PC<Object> (fails if cs is not an instance of PC)
pco = co;   // err; requires cast to PC or PC<Object> (fails if co is not an instance of PC)

pcs = cs;   // err; requires cast to PC<String> (fails if cs is not an instance of PC and T is not String)
pcs = co;   // err; will not compile, even with a cast

// --

const Person(String name)
    {
    @Lazy Int nameCount.calc()
        {
        return name.count(' ') + 1;
        }
    }

Void foo(String[] names)
    {
    @Inject Console console;
    for (String name : names)
        {
        console.println(name);
        }
    }