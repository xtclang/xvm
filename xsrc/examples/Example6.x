
// arrays

Int[] vals;             // what type is that?   Array<Int>
vals = new Int[];       // what type is that?   MutableArray<Int>
vals = new Int[5];      // what type is that?   FixedSizeArray<Int>
vals = new Int[5](0);
vals = new Int?[5];
vals = vals.ensurePersistent();              //  PersistentArray
vals = {1,2,3};
vals = Int:{1,2,3};                         //  ConstArray

s = "hello";
s = String:"hello";

i = 5;
i = (IntegerLiteral:"5").to<Int>();

// bad -  but you get the idea
map = {{x:5
        y:8
        z:9}}


// let's mock up a new Collection implementation
// I have a few methods already written in BaseColl

class MyColl
        extends BaseColl
//      implements Collection
        delegates Collection(throw new UnsupportedOperationException("I haven't yet implemented Collection");)
    {

    Collection foo.get()
        {
        TODO
        }
    }

// modules


module MyApp
    {
    package Spring import springframework.spring.org;
    package Spring import:embedded springframework.spring.org;
    package Spring import:required springframework.spring.org;               // required is default
    package Spring import:desired springframework.spring.org;
    package Spring import:optional springframework.spring.org;               // a.k.a. "supported"

    // avoid something like this
    package Spring import:if(...) springframework.spring.org;

    package Util
        {
        interface Map {...}
        class HashMap {...}
        }

    // module version is <=> some other version (with two versions potentially being none of <=>)
    // some name (module, package, class, method, property, ...) is present vs. not present
    //
    if (Module:"springframework.spring.org".version == Version:"1.2.3.4.5")
        {
        package Spring import springframework.spring.org;
        }
    else if (Module:"springframework.spring.org".version > Version:"1.2.3")
        {
        package Spring import springframework.spring.org;
        }
    else
        {
        package Spring import springframework.spring.org Version:"1.2.0";
        }

    // need to somehow simplify that though so that it's obvious FIRST that i need spring:
    package Spring import springframework.spring.org 1.2.0 prefer 1.2.3.4.5, 1.2.3 avoid 1.2.3.4.17


    package Spring import springframework.spring.org
        {
        case 1.2.3.4.5: true
        case 1.2.3.4.17: false
        case 1.2.3: true
        case 1.2.2:
            switch
                {
                case 1.2.3.4.17: AWFUL BAD NO DON'T USE IT
                case 1.2.3: PREFERRED
                }
        case 1.2.0: true
        }
    }

    1.4
    1.5.-4    1.5a
    1.5.-3    1.5b
    1.5.-3.1  1.5b2
    1.5.-2    1.5pr1
    1.5.-2.1  1.5pr2
    1.5.-1    1.5rc1
    1.5.-1.1  1.5rc2
    1.5

    version     ok  human-readable
    1.4         x
    1.5.ci1
    1.5.ci2
    1.5.ci(etc...)
    1.5.ci2985712
    1.5.alpha1
    1.5.beta1
    1.5.beta2
    1.5.rc1
    1.5.rc2
    1.5         x

    package Spring import springframework.spring.org 2.1 prefer ? avoid 2.1.0.0, 2.1.3.1

    package Spring import springframework.spring.org 2.1 -2.1.2 +2.1.3.1 -2.3.1

ModuleImport
    package Name import QualifiedName ModuleVersionReq-opt

ModuleVersionReq
    Version VersionOverrides-opt

VersionOverrides
    VersionOverride
    VersionOverrides VersionOverride

VersionOverride
    OverrideVerb Versions

OverrideVerb
    allow
    avoid
    prefer

Versions
    Version
    Versions, Version

Version
    TrailingVersionSegment
    Version . TrailingVersionSegment

TrailingVersionSegment:
    DigitsNoUnderscores
    NonGAVersionSegment

NonGAVersionSegment:
    NonGAVersionSpecifier DigitsNoUnderscores

NonGAVersionSpecifier:
    dev
    ci
    alpha
    beta
    rc

VersionSegment:
    Digit

    prerelease
    rc
    DigitsNoUnderscores Digit

package
    {
    if (Spring.present && Spring.version == 1.2.3)
        {
        PotentialSpring = Spring.IO.Externalizable
        class Employee
            implements Spring.IO.Externalizable;
        }
    }

class Employee implements PotentialSpring
    {
    if (Spring.present && Spring.version == 1.2.3)
        {
        Void superSpringRead(Spring.IO.InStream) {...}
        Void superSpringWrite(Spring.IO.OutStream) {...}
        }
    }

if there is a module that you can get for me called "springframework.spring.org", i could use it,
***but*** only if its version is 1.2.3 or a derivative thereof, and i'd really prefer to use 1.2.3.4.5
and if you can find it, i'm going to refer to it within my module as "Spring"

how do you say:
* i require spring
* i require spring 1.2 "or more recent on the 1.2 line"
* i require spring 1.2 "and explicitly 1.2 not something more recent!"
* i desire ...
*
--

module.x:

    A
    module m
        {
        B
        C
        }

package.x:


    P
    package p
        {
        D
        Q
        }

MyClass.x:

    X
    class c
        {
        Y
        Z
        }

same as:
module.x

    A
    module m
        {
        B
        C
        { // begin package.x insertion:
        P
        package p
            {
            D
            Q
            { // begin MyClass.x insertion:
            X
            class c
                {
                Y
                Z
                }
            } // end MyClass.x insertion:
            }
        } // end package.x insertion:
        }

--

// what if?

// we can say:
Version ver = Version:"1.2.3";
Module spring = Module:"springframework.spring.org";
// so why can't we say:
Condition SPRING = Module:"springframework.spring.org".exists;
Condition NOSPRING = !Module:"springframework.spring.org".exists;

class Employee
        if (COND) delegates y
        if (!COND) delegates z

    {
    if (SPRING) {
        Void ...
        }

    }

--

// ?

1) ? : ternary operator

2) ?. operator (with optional trailing ':')

a?.b();
if (a != null) { a.b(); }

x = a?.b?.c?.d : e;
// translates to:
x = a != null && a.b != null && a.b.c != null ? a.b.c.d : e;

3) ?: elvis operator
  - applies to T? type (Nullable or T)
  - if not null, then use the T value, otherwise use the following expr of type T

String? s = foo();
print(s);   => "null" or "hello"
conditional String sc = foo();
print(sc);   => "(false)" or "(true, 'hello')"

String s2 = s ?: "hello";
String s2 = sc ?: "hello";

if (sc?)
    {
    String? sc3 = sc;
    String? sc4 = (true, sc); 
    String? sc5 = false; 
    String? sc6 = true;
    }
    
x = a.b ?: c;
// translates to:
x = a.b != null ? a.b : c;
// and has to work the same as:
x = a.b? : c;

4) ? operator
  - applies to T? type (Nullable or T)
  - produces a "conditional T" return, i.e. produces a boolean and a T

if (a?) {..}
// translates to:
if (a != null) {..}

<T> conditional T optionalToConditional(T? value)
    {
    return value?  ;
    }

// composite example ..

x = a?.b ?: c;
// translates to:
x = a != null && a.b != null ? a.b : c;


Boolean s2 = sc ?: true;

if (sc?)
    {
    Boolean? sc3 = sc;
    Boolean? sc4 = (true, sc); 
    Boolean? sc5 = false; 
    Boolean? sc6 = true;
    }

//
// first with string
//

// current version:
String? foo()
    {
    return null;
    return "hello";
    }

// current version:
conditional String foo()
    {
    return false;
    return true, "hello";
    }

// new version:
String? foo()
    {
    return false;
    // this has to work ...
    return true, "hello";

    // is this allowed?
    return "hello";
    }

//
// now with boolean
//

// current version:
Boolean? foo()
    {
    return null;
    return false;
    return true;
    }

// current version:
conditional Boolean foo()
    {
    return false;
    return true, false;
    return true, true;
    }

// new version:
Boolean? foo()
    {
    // this would be like "conditional false"?
    return false;

    // this has to work ...
    return true, false;
    return true, true;
    }


//  "colon works"
Int? i = conditionalfoo();
if (Int j : i) {...}
print(i);

// ---- operator / parsing precedence

x = a ? b : c
x = a ?: b
x = a?.b : c

// x = (a != null)
x = a?;          // x is a boolean .. how to make x the value of a?

// if (a != null) x = a;
if (x : a?) {}  // ugly but true
x = a?[1];      // funny but true
x = a ?: x;     // true; short for:
x = a? ? a : x;

a?.b : d ?: c ? d : e

// TODO ?=
x ?= a;

// TODO ..


// ----- cast

// C style
y = (Type) x;

// how about ...
y = x.@(Type);  // cast
y = x.?(Type);  // instanceof?

y = x.as(Type);


//

x = a?.b : d

if (a != null)
    {
    x = a.b;
    }
else
    {
    x = d;
    }

x = a?.b?.c : d

if (a != null)
    {
    tmp = a.b;
    if (tmp != null)
        {
        x = tmp.c;
        }
    }
... else // for BOTH ifs
    {
    x = d;
    }

x = a?.b?.c : d : e     // compiler error!!!

x = a ? b ? c : d : e;
x = a ? b :

// multi-line character string

String s = {""
            ""
            ""}

// lambda problem

x.y(foo(3+z(name.name((somename.secondname<
                      ^^       ^^         ^
                      12       34         5

// 1. i know it's an expression, but i don't know what kind, e.g.
// (4)

// 2. it's an identifier. that could mean anything. type. prop. method. function. var. etc.

// 3. dot tells me it's a qualified name or a prop or a method or ...

// 4. ok what is it?

// 5. now what? it could be "less than", or i could be parsing a generic type expression

// it COULD be a less-than expression
x.y(foo(3+z(name.name((somename<name, name> othername)))));

// it COULD be a generic type, e.g. in a lambda
x.y(foo(3+z(name.name((somename.secondname<Int,String> x, Boolean y) -> ...


//

class Parent
    {
    class Child {} // not static
    }

c = new Parent.Child(); // compiler error!
p = Parent.new();
c = p.new Child();
c = new p.Child(); //?


// parsing generics again

name.name< ... >
// has to be some number of types
// 0: immediate ">"
// 1: a type, followed by ">"
// n: comma delimited types, followed by ">"
// so we're looking for ">"
// types can't contain {}
// types can't contain ()
// types CAN contain <>
// types CAN contain []


// type conditional

//
Boolean b = x instanceof (T);

// conditional T
Boolean b = x.instanceof(T);
Int i = x.instanceof(T).foo() : 4;

// or perhaps ..
// PostfixExpression ".instanceof" "(" TypeExpression ")" "?"-opt
Int i = x.instanceof(T)?.foo() : 4;

// inside a method

Void foo()
    {
    // i can declare an interface (it's private)
    interface Whatever {...}

    // i can declare a class (it's private)
    class SomeTest {...}

    // it could be static (no parent ref)
    static class MyClass {...}

    // what about methods?
    Void foo2() {...}

    // here's a function
    static Void foo3() {...}

    // here's a property
    static Int i;

    // i guess there is no such thing as a "named constant" .. that would just be some variable
    // that never gets changed, i.e. is "effectively final"
    }

// other valid uses of "static"

// singletons: only consts and services are allowed
static const CharPageLookupTables {...}
static service MyRegistryService {...}

class Parent
    {
    // like Java, a static child class is one that does not have a ref to its parent;  however,
    // unlike Java, you can NOT just new it .. you have to parent.new it because new is virtual for
    // child classes
    static class Child {..}

    // functions at the class level
    static Int max(Int n1, Int n2) { return n1 >= n2 ? n1 : n2; }

    // constants at the class level
    static Dec PI = 3.14;
    }

// type literals

foo(List<Person>:{foo1(), b, c});