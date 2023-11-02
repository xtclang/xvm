module TestSimple {
    @Inject Console console;

    void run() {
        for (Section s1 : B ..< C) { // this used to blow up the compiler
            console.print($"{s1=}");
        }

        for (Section s2 : A ..< C) { // this used to blow up the compiler
            console.print($"{s2=}");
        }

        Section start = A;
        Section end   = C;
        for (Section s3 : start ..< end) {  // this used to blow up at run-time with TypeMismatch
            console.print($"{s3=}");
        }

        for (Section s4 : start >.. end) {  // this used to blow up at run-time with TypeMismatch
            console.print($"{s4=}");
        }

        end = start;

        Range<Section> s3a = start ..< end;
        console.print($"{s3a.size=} {s3a.iterator().next()=}");

        Range<Section> s4a = start >.. end;
        console.print($"{s4a.size=} {s4a.iterator().next()=}");

//        for (Section s5 : start ..< end) {  // TODO this blows up at run-time with OutOfBounds
//            console.print($"{s5=}");
//        }
//        for (Section s6 : end ..< start) {  // TODO this blows up at run-time with OutOfBounds
//            console.print($"{s6=}");
//        }
    }

    enum Section {A, B, C}
}
