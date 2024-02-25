module TestSimple {
    @Inject Console console;

    void run() {
    }

   class Test<Element> {
        construct(Element | function Element(Int, Int) supply) {
            Element[] array = supply.is(Element)
                  ? new Element[1](supply) // this used to fail to compile
                  : TODO
        }
    }
}
