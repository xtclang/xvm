module TestSimple {
    @Inject Console console;

    void run() {
        console.print(new test1.Base1<String>().add("hello1"));
        console.print(new test1.Base2<String>().add("hello2"));

        console.print("test2");
        console.print(new test2.Base1<String>().add("hello1"));
        console.print(new test2.Base2<String>().add("hello2"));
    }

    // a number of things below used to fail to compile (those are excerpts from the tck)
    package test1 {
        mixin MixIn<ElementM> into Base1<ElementM> | Base2<ElementM> {
            @Override String add(ElementM e) = $"MX[{e=} " + super(e) + " ]MX";
        }

        class Super1<Element> {
            String add(Element e) = $"S[{e=}]S";
        }

        class Base1<ElementM> extends Super1<ElementM> incorporates MixIn<ElementM> {
            @Override String add(ElementM e) = $"B[{e=} " + super(e) + " ]B";
        }

        class Super2<Element> {
            String add(Element e) = $"s[{e=}]s";
        }

        class Base2<ElementM> extends Super2<ElementM> incorporates MixIn<ElementM> {
            @Override String add(ElementM e) = $"b[{e=} " + super(e) + " ]b";
        }
    }

    package test2 {
        mixin MixIn<ElementM> into Base1<ElementM> | Base2<ElementM> {
            @Override String add(ElementM e) = $"MX[{e=} " + super(e) + " ]MX";
        }

        class Super1<ElementS1> {
            String add(ElementS1 e) = $"S[{e=}]S";
        }

        class Base1<ElementB1> extends Super1<ElementB1> incorporates MixIn<ElementB1> {
            @Override String add(ElementB1 e) = $"B[{e=} " + super(e) + " ]B";
        }

        class Super2<ElementS2> {
            String add(ElementS2 e) = $"s[{e=}]s";
        }

        class Base2<ElementB2> extends Super2<ElementB2> incorporates MixIn<ElementB2> {
            @Override String add(ElementB2 e) = $"b[{e=} " + super(e) + " ]b";
        }
    }
}
