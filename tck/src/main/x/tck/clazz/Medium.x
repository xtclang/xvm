// Tests working with Mixins and Derived
class Medium {
    @Inject Console console;

    void run() {
        incorp();
        //annot();
        //incorpConditional();
    }

    // Theory - can immediately expand these mixin classes into a set of Java classes.
    // Pre/post mixins get a mangled java name, and the method defs in the correct order.
    // Overloaded methods get all mangled names, so the "super" calls can be replaced
    // with direct calls to the correct mangled name.
    // Conditional mixins just immediately make one Java class per conditional.

    // Standard "incorporates":  The MixIn is "before" Base in the class tree:
    // Object <- Super1 <-\       /-< Base1 <- Derived1
    //                      MixIn
    // Object <- Super2 <-/       \-< Base2 <- Derived2
    void incorp() {
        mixin MixIn<Element> into Base1<Element> | Base2<Element> {
            String add(Element e) = $"MX[{e=} " + super(e) + " ]MX";
        }

        class Super1<Element> {
            String add(Element e) = $"S[{e=}]S";
        }
        class Base1<Element extends Const/*GG bug*/> extends Super1<Element> incorporates MixIn<Element> {
            @Override String add(Element e) = $"B[{e=} " + super(e) + " ]B";
        }
        class Derived1<Element> extends Base1<Element> {
            @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
        }

        class Super2<Element> {
            String add(Element e) = $"s[{e=}]s";
        }
        class Base2<Element extends Const/*GG bug*/> extends Super2<Element> incorporates MixIn<Element> {
            @Override String add(Element e) = $"b[{e=} " + super(e) + " ]b";
        }
        class Derived2<Element> extends Base1<Element> {
            @Override String add(Element e) = $"d[{e=} " + super(e) + " ]d";
        }

        String baseint1 = new Base1   < Int  >().add( 123 );
        String basestr1 = new Base1   <String>().add("abc");
        String dervint1 = new Derived1< Int  >().add( 123 );
        String dervstr1 = new Derived1<String>().add("abc");
        assert baseint1 ==         "B[e=123 MX[e=123 S[e=123]S]MX ]B"   ;
        assert basestr1 ==         "B[e=abc MX[e=abc S[e=abc]S]MX ]B"   ;
        assert dervint1 == "D[e=123 B[e=123 MX[e=123 S[e=123]S]MX ]B ]D";
        assert dervstr1 == "D[e=abc B[e=abc MX[e=abc S[e=abc]S]MX ]B ]D";

        String baseint2 = new Base2   < Int  >().add( 123 );
        String basestr2 = new Base2   <String>().add("abc");
        String dervint2 = new Derived2< Int  >().add( 123 );
        String dervstr2 = new Derived2<String>().add("abc");
        assert baseint2 ==         "b[e=123 MX[e=123 s[e=123]s]MX ]b"   ;
        assert basestr2 ==         "b[e=abc MX[e=abc s[e=abc]s]MX ]b"   ;
        assert dervint2 == "d[e=123 b[e=123 MX[e=123 s[e=123]s]MX ]b ]d";
        assert dervstr2 == "d[e=abc b[e=abc MX[e=abc s[e=abc]s]MX ]b ]d";

    }

    //// "Annotation": the Mixin is "after" the Base in the class tree:
    //// Object <- Base <- MixIn <- Derived
    //
    //// I searched the XTC lib and did not find instances of this that were not
    //// also specifically runtime-specific, and thus require some kind of custom
    //// work anyways.  Mixins like @Future (requires some kind of volatile) or
    //// @Synchronized (requires runtime interaction and locking) must be
    //// directly intercepted.  Mixins like @Auto represent auto conversions from
    //// the front end.
    //void annot() {
    //    @MixIn3 class Base3<Element> {
    //        String add(Element e) = $"B[{e=}]B";
    //    }
    //
    //    mixin MixIn3 into Base3 {
    //        @Override String add(Element e) = $"M[{e=} " + super(e) + " ]M";
    //    }
    //
    //    class Derived3<Element> extends Base3<Element> {
    //        @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
    //    }
    //
    //    String baseint = new Base3   < Int  >().add( 123 );
    //    String basestr = new Base3   <String>().add("abc");
    //    String derint  = new Derived3< Int  >().add( 123 );
    //    String derstr  = new Derived3<String>().add("abc");
    //    assert baseint ==         "M[e=123 B[e=123]B ]M"   ;
    //    assert basestr ==         "M[e=abc B[e=abc]B ]M"   ;
    //    assert derint  == "D[e=123 M[e=123 B[e=123]B ]M ]D";
    //    assert derstr  == "D[e=abc M[e=abc B[e=abc]B ]M ]D";
    //}
    //
    //// Conditional incorporation: the Mixin is both *conditional* and also
    //// "after" the Base in the class tree:
    //// Object <- Base <-          Derived
    //// Object <- Base <- Mixin <- Derived
    //void incorpConditional() {
    //    class Base2<Element> incorporates conditional MixIn2<Element extends String> {
    //        String add(Element e) = $"B[{e=}]B";
    //    }
    //
    //    mixin MixIn2<Element extends String> into Base2<Element> {
    //        @Override String add(Element e) = $"M[{e=} " + super(e) + " ]M";
    //    }
    //
    //    class Derived2<Element> extends Base2<Element> {
    //        @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
    //    }
    //
    //    String baseint = new Base2   < Int  >().add( 123 );
    //    String basestr = new Base2   <String>().add("abc");
    //    String derint  = new Derived2< Int  >().add( 123 );
    //    String derstr  = new Derived2<String>().add("abc");
    //    assert baseint ==                 "B[e=123]B"      ;
    //    assert basestr ==         "M[e=abc B[e=abc]B ]M"   ;
    //    assert derint  ==         "D[e=123 B[e=123]B ]D"   ;
    //    assert derstr  == "D[e=abc M[e=abc B[e=abc]B ]M ]D";
    //}

}
