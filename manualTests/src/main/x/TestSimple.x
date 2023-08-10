module TestSimple {

    void run() {
        Object stringsC = ["a", "b"];
        assert stringsC.is(Const); // this used to fail at run-time

//        Object stringsM = ["c", "d"].reify(Mutable);
//        assert !stringsM.is(Const);  // this used to fail at run-time
    }
}