// interface functions
interface I
    {
    // by declaring a function on an interface, it requires all implementors of the interface to
    // include an implementation of the function
    static Int foo();
    }

// this class is perfectly valid and it implements I
class B implements I
    {
    static Int foo() {...}
    }

// this class is valid BUT d.is(I)==False
class D extends B
    {
    }

// let there be a mixin
mixin M
    {
    static Int foo() {...}
    }

// this class is valid and d.is(I)==False
class B2 incorporates M
    {
    }


// so why not allow:
interface I
    {
    construct(String s, Int i);
    }

// which would allow:
interface I
    {
    construct(I:struct s);
    }

// which could even allow something like ...
interface I(I:struct)
    {
    }

// going back to the previous one, imagine its applicability to serialization
interface Serializable
    {
    static I:struct blank();    // step 1 for deserialization
    construct(I:struct s);      // step 2 for deserialization
    I:struct snapshot();        // for serialization
    }

// which corresponds to ...
class PersonSE // StupidExample
        implements Serializable
    {
    static PersonSE:struct blank()
        {
        // not sure how to write this code today ... maybe something like
        return new PersonSE:struct();
        }

    construct(PersonSE:struct s)
        {
        this.struct.copyFrom(s);
        }

    PersonSE:struct snapshot()
        {
        return this:struct;
        }
    }

// the two phases of construction correlate to what the runtime does with the constructor invocation
new Point(3,5);
// (1) creates a new struct for Point
// (2) calls the constructor, passing that struct
// (3) verifies that the struct got all of its fields filled in
//     - with various possible exceptions based on some strict rules
//     - with lots of complexity related to subclassing
// (4) finalizes the construction
//     - with lots of complexity related to subclassing

// so any type that is constructable, the class composition (Class.x) needs to expose both:
StructType allocateStruct();
(PublicType, ProtectedType, PrivateType) instantiate(Constructor);

// the second is provided today via Class.x (may need to be refined?):
    /**
     * A normal constructor is a function that operates on a read/write structure that will contain
     * the values of the newly constructed object.
     */
    typedef function void (StructType) Constructor;
    /**
     * Create a new instance of this class.
     */
    PublicType newInstance(Constructor | ConstructorFinally constructor);

// w.r.t. de-construction, the syntax
const Point(Int x, Int y) {}
// could also (in addition to constructor, properties, hash, ==/!=, <=>, toString() etc.) provide
// (Int x, Int y) elements();

// alternatively, it's just names i.e. there is a property x, a property y
// (so reflection is easy enough)


// ----- funky -----

interface Hashable
    {
    static Boolean equals(...);
    static Int hash(...);
    }

class B1
    {
    static Boolean equals(...) {..}
    static Int hash(...) {..}
    }

class B2 extends B1
    {
    static Boolean equals(...) {..}              // this COULD be a compiler error! (COULD -> SHOULD)
    }

class D1 extends B1
    {
    }

class D2 extends B2
    {
    }

class HashSet<ET extends Hashable>
        implements Set<ET>
    {
    // ...
    add(ET el)
        {
        Int i = ET.hash(el);        // this should work, but ...
        Int i = el.hash();          // Stroustrup strikes again (we choose to NEVER ALLOW THIS SHIT)
        }
    }

foo()
    {
    HashSet s1 = new HashSet<D1>();     // obvious and simple and correct, right?
    // what equals does it use? B1.equals()
    // what hash does it use? B1.hash()

    HashSet s2 = new HashSet<D2>();     // NOT AT ALL OBVIOUS AND SIMPLE AND CORRECT!!!!!!! FUCK FUCK FUCK
    // what equals does it use? B1.equals() (NOT B2.equals, because B2 does NOT "implement" Hashable)
    // what hash does it use? B1.hash() is all we have ...
    }


// ----- rotational operators ----

x |<< 3
x >>| 3

x |<<| 3
x |>>| 3

x >^ 3
x ^< 3

x >>^ 3
x ^<< 3;

x %> 3
x <% 3

// ----- object input/output -----

Object <-> JSON <-> Reader/Writer <-> String or Char[] or UTF8 byte[]

Object <-> Protobuf <-> DataInput/DataOutput <-> byte[]

ObjectOutput = Format.createOutput(???) // DataOutput? BinaryOutput? Writer? StringBuffer?


// ----- JSON default schema .. what can it do??

// assume ...
ObjectInput  in  = json.createObjectInput(reader);
ObjectOutput out = json.createObjectOutput(writer);

// this writes an IntLiteral as ... a number
out.write(3);
// 3

// this writes an FPLiteral as ... a number
out.write(3.14);
// 3.14

// this writes a String as ... a string
out.write("hello world")
// "hello world"

// these write each value as ... you would expect
out.write(True);
out.write(False);
out.write(Null);
// truefalsenull            <--- obviously this is shit


// ----- circular typedefs

typedef Int | Array<X> X;


// ----- modules and packages and classes (oh my!)

file Ecstasy.xtc
    {
    module Ecstasy.xtclang.org
        {
        package Ecstasy import Ecstasy.xtclang.org;

        interface Module {}
        interface Package {}
        const Class {}
        enum Nullable {}
        enum Boolean {}
        class Int64 {}
        ...
        }
    }

file "T.xtc"
    {
    module O
        {
        package Ecstasy import Ecstasy.xtclang.org;

        package OP
            {
            class OC
                {
                }
            }
        }

    module T
        {
        package Ecstasy import Ecstasy.xtclang.org;

        package OI import:embedded O;

        package TP
            {
            class TC
                {
                void foo()
                    {
                    OI.Ecstasy.Ecstasy.Ecstasy.Ecstasy.Ecstasy.Ecstasy.Int64 i = 0;

                    Class clz = OC;

                    OI.OP.OC oc = new OI.OP.OC();
                    Class clz2 = oc.class;

                    Type type = &oc.ActualType;
                    bar(type);
                    }

                void bar(Type t)
                    {
                    if (Class clz := t.originClass())
                        {
                        ...
                        }
                    }
                }
            }
        }
    }

// a "classy type" is a type for which the conditional originClass() (name TBD) returns True (and a
// class); it corresponds to a "class composition" in the proof of concept runtime

Type
 - RelationalType
   - UnionType
   - IntersectionType
   - DifferenceType
 - ClassyType

 // might not need:
 - AnnotatedType
@Unchecked Int  <-- this has an origin class even though it is annotated (but not all annotated types are classy)

String?         <-- this has two (!!!) origin classes, but it should still be deserializable,
                    so there must be something in addition to originClass(), e.g. "@RO Boolean classy;"
                    (GG calls it "class composable")