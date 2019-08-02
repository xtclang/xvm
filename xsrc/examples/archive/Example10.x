// part of Ecstasy runtime
interface RestClient
    {
    RestResult query(String request);
    }
interface ConsoleApp
    {
    Void onCommand(String command);
    @ro Console console;
    }


// client application
module MyApp
        implements ConsoleApp
    {
    Void onCommand(String command)
        {
        @Inject(domain="https://jsonplaceholder.typicode.com") RestClient client;
        @Future result = client.query(command);
        &result.passTo(onCompletion);
        &result.handle(onException);
        }

    Void onCompletion(RestResult result)
        {
        @Inject Console console;
        console.print("Query completed with result:");
        console.print(result);
        }

    Void onException(Exception e)
        {
        @Inject Console console;
        console.print("Query did not complete successfully:");
        console.print(e);
        console.result = 1;
        }
    }


// object as function

// some method foo
Void foo(function Object() provide)
    {
    if (...)
        {
        Object o = provide();
        }
    }

// calling foo with a long running process
foo(() -> for (Server server : Amazon.farm() {server.formatAllDisks();});

// calling foo with "hello"
foo("hello");

// async example

service VideoRenderer
    {
    enum Status {Initial, Running, Finishing, Completed, Failed}

    @Atomic Status status;
    }

class Cache
    {
    @Soft @Lazy @Future Video video.calc()
        {
        return new VideoRender.render(...);
        }
    }

// worst thing for a parser

x = 1;      // expression? or statement? (YES)

// C / C++ / Java / shit
int x = y = z = 0;      // Seriously .. WTF?!?!?
x = 1, 2;               // 1, 2 is an expression that evaluates to ... um ... 2
x = (1, 2);             // it's just precedence .. same as above

// x
x = (1, 2);             // x is a tuple of (Int, Int) containing (1, 2)
(Int, Int) foo() { return x; }

conditional Int size()  { if (err) {return false;} else {return true, count;}

if (Int c : o.size()}
    {
    // ...
    }
else
    {
    // error
    }

// -- assignability

class A
    {
    Void foo() {}
    }

class B
    {
    Void foo() {}
    }

A a1 = new A(); // ok
B b1 = new B(); // ok
A b2 = new B(); // NOT ok ...
B a2 = new A(); // NOT ok ...

class C
    {
    Void foo() {}
    A[] to<A[]>() {return to<C[]>();}
    Tuple<A> to<Tuple<A>>() {return to<Tuple<C>();}
    @Auto function A() to<function A()>() {return ()->this;}
    }

C c1 = new C(); // ok
C a3 = new A(); // NOT ok
A c2 = new C(); // still NOT ok

class D
        impersonates A
    {
    Void foo() {}
    }

A d1 = new D(); // ok

// -- mixin assignable

mixin M1
    {
    Void foo() {..};
    }

class C1
        incorporates M1
    {
    }

mixin M2
        into C2
    {
    Void bar() {..};
    }

class C2
    {
    }

M2 m2  = new C2();      // error
M2 m2b = new @M2 C2();  // type is annotated(M2) of C2
M2 m2  = (M2) c2;       // ok - might RTE
C2 c2  = m2;            // ok - all M2's are C2's

mixin M3
        into C3
    {
    Void bar() {..};
    }

class C3
        incorporates M3
    {
    }

M3 m3  = ...
C3 c3  = m3;
M3 m3b = c3;

@Serializable class Person {...}
class Person incorporates Serializable {...}    // not identical to the above

Kernel
 - Account Mgmt
 - I/O primitives
 - DB
 - Hosting
   - Cust1
     - cust1_DB -> injected with a "connection" that the Kernel newed from the DB sub-system
     - cust1_FS -> injected with a FS that the Kernel newed it from the I/O primitives sub-system
     - app1
   - Cust2
   - ...


// -- more assignability

interface Bag<ElementType>
    {
    Void add(ElementType e);
    Iterator<ElementType> iterate();
    }

mixin Summer<ElementType extends Number>
        into Iterable<ElementType>
    {
    ElementType sum(ElementType zero)       // note: there must be a better way to implement zero
        {
        ElementType nSum = zero;
        for (ElementType n : this)
            {
            nSum += n;
            }
        return nSum;
        }
    }

class SimpleBag<ElementType>
        implements Bag<ElementType>
        incorporates conditional Summer<ElementType extends Number>
    {
    // ...
    }

SimpleBag<String> bs = ...
SimpleBag<Int>    bi = ...

Bag         b0 = bs;            // ok
Bag<String> b1 = bs;            // ok
Summer      s0 = bs;            // compile-time error
Summer      s1 = bs.as(Summer); // compile-time warning, run-time error
Summer      s2 = bi;            // ok (type of bi is SimpleBag<Int>, which implies Summer<Int>)
Summer<Int> s3 = bi;            // ok (same as above)


// short-circuiting expressions

Int x = a?.b?.c : -1;
// NVAR Int x
// JMP_NULL a, DEFAULT
// VAR b_tmp
// P_GET "b" a b_tmp
// JMP_NULL b_tmp, DEFAULT
// P_GET "c" b_tmp x
// JMP EXIT
// DEFAULT:
// MOV -1 -> x
// EXIT:

foo(a?.b?.c);
// VAR Int              ; #7
// JMP_NULL a, EXIT
// VAR b_tmp
// P_GET "b" a b_tmp
// JMP_NULL b_tmp, EXIT
// P_GET "c" b_tmp #7
// NVOK_10 "foo" #7
// EXIT:


// assignable
a[6].b(c[d, foo(e)]).f.g = foo()


// while vs. do..while with conditional statement decl+asn

List<Int> list = ...
Iterator<Int> iter = list.iterator();
Int sum = 0;
while (Int n : iter.next())
    {
    sum += n;
    }

List<Int> list = ...
Iterator<Int> iter = list.iterator();
Int n = 0;
Int sum = 0;
do
    {
    sum += n;
    }
while (n : iter.next())

for (Int n : list)
    {
    sum += n;
    }


// why condition is at the bottom:

// while(cond)              do-while(cond)              do-while(declAndOrAssign)
//
//   JMP Continue
//   Repeat:                  Repeat:                     Repeat:
//   [body]                   [body]                      [body]
//   Continue:                Continue:                   Continue:
//                                                        ENTER
//   [cond]                   [cond]                      [declAndOrAssign]
//  +JMP_TRUE cond Repeat    +JMP_TRUE cond Repeat        JMP_TRUE cond Repeat
//   Break:                   Break:                      Break:

// while(cond)
//
//   Repeat:
//   [cond]
//   JMP_FALSE cond Break
//   [body]
//   Continue:
//   JMP Repeat
//   Break:


// for persistent operations

    // currently
    (set, Boolean fAdded) = set.add(x);

    // why not
    if (set : set.add(x))
        {
        // ...
        }

    // don't care about the boolean?
    if (set : set.add(x)) {}    // ugly

    // could have also done:
    (_, set) = set.add(x);      // uglier?

    // why not just ...
    set := set.union(x);

    list2 := list + x;

    List list2 = list + x;          // compiler error
    List list3 := list + x;         // no compiler error here
    int c = list3.size;             // compiler error (list3 is NOT definitely assigned)

    List list4 = list.removeAll(list3) : list;   // ok but ugly
    int c4 = list4.size;            // ok

    List list2 = list;
    list2 := list + x;

    List list2 = list;
    list2 += x;

    // compiles same as
    if (set : set.add(x)) {}

    // or just ...
    set += x;
    list += x;
    set -= x;
    set |= set2;    // union
    set &= set2;    // intersection
    set ^= set2;    // symmetric difference
    // implies that the compiler understands a conditional @Op return (!!!)

    // e.g.
    if (set :  x)
        {

        }

// l-value bi-expression

// obvious l-value:
(foo() ? a : b)[i] = c;

// is this an l-value?
(foo() ? a : b) = c;

// definitely legal:
a?.b = c;

// is this an l-value?
(a?.b : c) = d;


// String building
Int i = 7;

String s = "There are " + (i+2) + " people in the room.";

String s = {"There are {i+2} people in the room."};

String s = new StringBuilder()
            .append("There are ")
            .append(i+2)
            .append("people in the room.")
            .toString();
