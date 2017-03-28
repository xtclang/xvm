typedef function ResultType () Callable<ResultType>;
typedef function Void (ValueType) Consumer<ValueType>;

String hello = "hello";
Callable<String> getGreeting = () -> hello;
Consumer<String> setGreeting = s -> {hello = s; &hello.set(s)};

setGreeting("bonjour");
String greeting = getGreeting();

@Inject Printer console;

class C
    {
    static Void out(String s)
        {
        console.print(s);
        }

    void foo(Int i, String s) {...}

    someMethod(Iterator iter)
        {
        iter.forEach(C.out);
        iter.foreach(v -> console.print(v));

        function Void (String) print = s -> console.print(s);
        function Void print(String) = s -> console.print(s);
        function Void print(String s) {console.print(s);}

        iter.forEach(print);
        }

    // a method that takes a consumer of String and returns a consumer of Int
    (function Void (Int)) foo(function Void (String) fn)
        {
        // ...
        }

    void foo2()
        {
        function (function void (Int)) (function void (String)) converter = foo;
        }
    }

// function Void fn(V))
typedef function Void (String) Consumer;
interface Consumer2<V> {void action(V value);}

function Void(String) fn =
Consumer c1 = v -> console.print(v);
Consumer2 c2 = v -> console.print(v); // disallow?
Consumer c12 = c2; // legal?
Consumer2 c21 = c1; // legal?
Consumer c3 = c2.action;

c1("hello");
c2.action("hello");

typedef List<Person> People;

Collector<E, A, R> co = Collector.of(() -> new E(), (a, e) -> {a.add(e);}, (a, a2) -> {a.addAll(a2); return a;}, (a) -> a.toR());
Collector<E, A, A> co = UniformCollector.of(() -> new E(), (a, e) -> {a.add(e);}, (a, a2) -> {a.addAll(a2); return a;});

static Builder<ElementType> createBuilder(ElementType.Type type)

static Builder<ElementType> builderOf(Type A, A a)
    {
    Type t = Runnable.Type;

    return new StreamBuilder<ElementType>();
    }


Stream<Int> s2 = builder(Int.Type);


**** Stream

Stage<InType, OutType> implements Stream
    {
    typedef function conditional OutType (InType) Mapper;

    Stage                   head;
    Stage<Object, InType>?  prev;
    Stage<OutType, Object>? next;
    Mapper?                 transform;

    construct()
        {
        this.head = this; // HOW to create a circular ref? could it be a deferred assignment? otherwise, it has to be Nullable
        this.prev = null;
        this.next = null;
        }

    // alternative
    Stage<OutType, next.OutType> link(Stage<OutType, Object> next, Mapper mapper)
        {
        next.head   = this.head;
        this.next   = next;
        next.prev   = this;
        this.mapper = mapper;

        return next;
        }

    conditional (ResultType) evaluateHead(TerminalOp<OutType, ResultType> op)
        {
        assert this.head == this;
        for (ElementType el : iterator())
            {
            if (!evaluate(op, this)
            }

    conditional (ResultType) evaluate(TerminalOp<OutType, ResultType> op, InType el)
        {
        Stage head = this.head;

        Iterator<OutType> iter = new Iterator<>()
            {
            Iterator iterOuter = head.iterator();

            @override
            conditional OutType next()
                {
                nextElement:
                for (Object el : iterOuter)
                    {
                    Stage stage = head;

                    while (stage != null)
                        {
                        if (el : stage.transform(el))
                            {
                            stage = stage.next;
                            }
                        else
                            {
                            // the element was filtered out; el becomes not definitely assigned
                            continue nextElement;
                            }
                        }
                    assert:debug(el.Type == OutType);
                    return (true, el);
                    }
                }
            }

        return (op.process(iter));
        }

    interface Step<ElementType, ResultType>
        {
        Void begin();
        Boolean process(ElementType el);
        ResultType finish();
        }
    interface TerminalOp<ElementType, ResultType>
        {
        Void begin();
        Boolean process(Iterator<ElementType> iter);
        ResultType finish();
        }

    class CollectorOp<ElementType, AccumulatorType, ResultType)
                (Collector<ElementType, AccumulatorType, ResultType) collector)
            implements TerminalOp<ElementType, ResultType>
        {
        @override
        conditional ResultType process(Iterator<ElementType> iter)
            {
            AccumulatorType container = collector.supply();
            for (ElementType element : iter)
                {
                if (!collector.accumulate(container, element))
                    {
                    break;
                    }
                }
            return (true, collector.finish(container));
            }
        }

    class AnyMatchOp<ElementType, Boolean)
                (function Boolean (ElementType) match)
            implements TerminalOp<ElementType, ResultType>
        {
        @override
        conditional ResultType process(Iterator<ElementType> iter)
            {
            for (ElementType element : iter)
                {
                if (match(element))
                    {
                    return (true, true);
                    }
                }
            return (false);
            }
        }


@future Image img1 = service.getAdvertisement(type1);
@future Image img2 = service.getAdvertisement(type1);

&img1.thenAccept(...)

if (oldValue : replaceFailed(oldValue, newValue))
    {
    return newValue;
    }

T v;
conditional T cv;

Boolean f = (v : cv);  ==> if (cv[0]) {v = cv[1]}; f = cv[0];
Boolean f = !(v : cv); ==> if (cv[0]) {v = cv[1]}; f = !cv[0];

Array<((String, String), Int)> array = ...
for (((String s1, String s2), Int iVal) : array using Int index)

Nullable vs. conditional:

String? s1 = ...

if (s1 != null)
    {
    print(s1.length());
    }

conditional String cs2 = stream.reduce(...);
String* cs2 = ...;

if (String s : cs2)
    {
    print(s.length());
    }

Iterator
    {
    E* next();
    }

Iterator<String> iter = ...;

String s;
if (s : iter.next())
   {
   Int i = s.length();
   }


function Int (Int) sq = n -> n*n;
print(sq(5));
print((function Int (Int)) (n -> n*n) (5));

service MyService
    {
    String prop1;

    Void shutdown() {...}
    }