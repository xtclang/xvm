module TestSimple {
    @Inject Console console;

    void run() {
    }

    @MixinC
    class ClassA {
    }

    annotation MixinA {
    }

    @MixinB
    class ClassB extends ClassA {
    }

    @MixinC
    annotation MixinB extends MixinA { // used to blow up the compiler; a fatal error now
    }

    @MixinA
    class ClassC extends ClassB {
    }

    annotation MixinC extends MixinB {
    }
}