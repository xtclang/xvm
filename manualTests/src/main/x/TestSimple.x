module TestSimple {
    @Inject Console console;

    void run() {

    S s = new S();
    foo(s, 0);
    }

    interface I {
        @Atomic Int ap;
        Int p;
        Int[] a;
    }

    service S implements I {
        @Override @Atomic Int ap;
        @Override Int p;
        @Override Int[] a = [0];

        void foo(Int k) {
//            Int r = 0;
//
//            ap++;    // &ap.postIncrement()
//            ap += k; // &ap.addAssign(k)
//
//            p++;     // p++
//            p += k;  // p += k ("add")
//
//            r++;     // r++
//            r += k;  // r += k ("add")
//
//            a[0] += k; // a[0] += k ("add")
            a    += [0, k];  // a += [0, k] ("addAll)
        }
    }

    void foo(I s, Int k) {
//        s.ap++; // s.&ap.postIncrement()
//        s.p++;  // s.p++
//
//        s.ap += k; // s.&ap.addAssign(k)
//        s.p  += k; // s.p += k ("add")

        s.a += [0, k];  // s.a[0] += k ("add)
    }
}
