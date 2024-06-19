module CompilerBug {
    @Inject Console console;

    void run() {
        Base    b  = new Base(1);
        Derived d  = new Derived(2);
        Base    b2 = b.new(d);

        console.print($"{&b2.actualType=} {b2=}");
    }

    const Base(Int v) implements Duplicable {
        @Override // used to produce a weird error if Override was missing
        construct(Base b) {
            v = b.v;
        }
    }

    const Derived(Int v) extends Base(v) {
        @Override  // used to produce a weird error if Override was missing
        construct(Derived d) {
            v = d.v;
        }
    }
}
