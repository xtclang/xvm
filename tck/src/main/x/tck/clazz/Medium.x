// Tests working with Mixins and Derived
class Medium {
    @Inject Console console;

    void run() {
        //xincorp();
        //incorp();
        //ic();
        typevars();
        //annot();
    }


    //void xincorp() {
    //  String baseint1 = new XBase1   < Int  >().add( 123 );
    //  String basestr1 = new XBase1   <String>().add("abc");
    //  assert baseint1 ==         "B[e=123 MX[e=123 S[e=123]S ]MX ]B"   ;
    //  assert basestr1 ==         "B[e=abc MX[e=abc S[e=abc]S ]MX ]B"   ;
    //  String dervint1 = new XDerived1< Int  >().add( 123 );
    //  String dervstr1 = new XDerived1<String>().add("abc");
    //  assert dervint1 == "D[e=123 B[e=123 MX[e=123 S[e=123]S ]MX ]B ]D";
    //  assert dervstr1 == "D[e=abc B[e=abc MX[e=abc S[e=abc]S ]MX ]B ]D";
    //  String baseint2 = new XBase2   < Int  >().add( 123 );
    //  String basestr2 = new XBase2   <String>().add("abc");
    //  String dervint2 = new XDerived2< Int  >().add( 123 );
    //  String dervstr2 = new XDerived2<String>().add("abc");
    //  assert baseint2 ==         "b[e=123 MX[e=123 s[e=123]s ]MX ]b"   ;
    //  assert basestr2 ==         "b[e=abc MX[e=abc s[e=abc]s ]MX ]b"   ;
    //  assert dervint2 == "d[e=123 b[e=123 MX[e=123 s[e=123]s ]MX ]b ]d";
    //  assert dervstr2 == "d[e=abc b[e=abc MX[e=abc s[e=abc]s ]MX ]b ]d";
    //}
    //
    //// Standard "incorporates":  The MixIn is "before" Base in the class tree:
    ////        /- Super1 <-\       /-< Base1 <- Derived1
    //// Object               MixIn
    ////        \- Super2 <-/       \-< Base2 <- Derived2
    ////
    //// Clone theory:
    //// - Clone Mixin's code "above" the Base1's:
    ////            /- Super1 <- Mix$Base1 <- Base1 <- Derived1
    ////     Object
    ////            \- Super2 <- Mix$Base2 <- Base2 <- Derived2
    ////- The clones are abstract, and mostly there to allow "super" methods to call correctly.
    ////
    //
    //void incorp() {
    //    mixin MixIn<Q> into Base1<Q> | Base2<Q> {
    //        @Override String add(Q e) = $"MX[{e=} " + super(e) + " ]MX";
    //    }
    //
    //    class Super1<S> {
    //        String add(S e) = $"S[{e=}]S";
    //    }
    //    class Base1<B> extends Super1<B> incorporates MixIn<B> {
    //        @Override String add(B e) = $"B[{e=} " + super(e) + " ]B";
    //    }
    //    class Derived1<D> extends Base1<D> {
    //        @Override String add(D e) = $"D[{e=} " + super(e) + " ]D";
    //    }
    //
    //    String baseint1 = new Base1   < Int  >().add( 123 );
    //    String basestr1 = new Base1   <String>().add("abc");
    //    assert baseint1 ==         "B[e=123 MX[e=123 S[e=123]S ]MX ]B"   ;
    //    assert basestr1 ==         "B[e=abc MX[e=abc S[e=abc]S ]MX ]B"   ;
    //    String dervint1 = new Derived1< Int  >().add( 123 );
    //    String dervstr1 = new Derived1<String>().add("abc");
    //    assert dervint1 == "D[e=123 B[e=123 MX[e=123 S[e=123]S ]MX ]B ]D";
    //    assert dervstr1 == "D[e=abc B[e=abc MX[e=abc S[e=abc]S ]MX ]B ]D";
    //
    //    class Super2<Z> {
    //        String add(Z e) = $"s[{e=}]s";
    //    }
    //    class Base2<ZB> extends Super2<ZB> incorporates MixIn<ZB> {
    //        @Override String add(ZB e) = $"b[{e=} " + super(e) + " ]b";
    //    }
    //    class Derived2<D> extends Base2<D> {
    //        @Override String add(D e) = $"d[{e=} " + super(e) + " ]d";
    //    }
    //
    //    String baseint2 = new Base2   < Int  >().add( 123 );
    //    String basestr2 = new Base2   <String>().add("abc");
    //    String dervint2 = new Derived2< Int  >().add( 123 );
    //    String dervstr2 = new Derived2<String>().add("abc");
    //    assert baseint2 ==         "b[e=123 MX[e=123 s[e=123]s ]MX ]b"   ;
    //    assert basestr2 ==         "b[e=abc MX[e=abc s[e=abc]s ]MX ]b"   ;
    //    assert dervint2 == "d[e=123 b[e=123 MX[e=123 s[e=123]s ]MX ]b ]d";
    //    assert dervstr2 == "d[e=abc b[e=abc MX[e=abc s[e=abc]s ]MX ]b ]d";
    //}
    //
    //// Conditional incorporation: the Mixin is both *conditional* and also
    //// "after" the Base in the class tree:
    ////        /- Super1 <- Base1 \  <-   /-< Derived1
    //// Object                      MixIn
    ////        \- Super2 <- Base2 /  <-   \-< Derived2
    //
    //// Current theory:
    //// - Mixin cannot extend two different classes, Base1 and Base2.
    //// - Java: clone Mixin's code, extending Base1,Base2 each.
    ////        /- Super1 <- Base1 <- Mixin  <- Derived1
    //// Object
    ////        \- Super2 <- Base2 <- Mixin  <- Derived2
    ////
    //// All Mixin methods start with "if( !_mixin ) return super.add(e);"
    //// Where the _mixin is a final bool set at construction time, and
    //// implement the "conditional" part.
    //
    //// ------------------------------
    //// JAVA TRANSLATION CODE
    //// Super <- Base <- Mixin  <- DerivedMix
    //// class Super<E> { E add(E e) { ... } };
    ////
    //// class Base<E> extends Super<E> { @Override E add(E e) { ... super(e); ... } }
    ////
    //// class Mixin$Base<E> extends Base<E> {
    ////   private final boolean _mixin;
    ////   MixinBase( E gold ) { _mixin = gold instanceof String; }
    ////   @Override E add(E e) {
    ////     if( !_mixin ) return super.add(e);
    ////     e2 = (String)e; // Must up-cast for mixin logic, which gets assume String
    ////     ...mixin logic... super.add((E)e2); // Except for super calls, which go back to 'E' type
    ////   }
    //// }
    ////
    //// class Derived<E> extends MixinBase<E> { ... }
    //
    //void ic() {
    //    mixin MixIn<E extends String> into Base1<E> | Base2<E> {
    //        @Override String add(E e) = $"MX[{e=} " + super(e) + " ]MX";
    //    }
    //
    //    class Super1<E> {
    //        String add(E e) = $"S[{e=}]S";
    //    }
    //    class Base1<E> extends Super1<E> incorporates conditional MixIn<E extends String> {
    //        @Override String add(E e) = $"B[{e=} " + super(e) + " ]B";
    //    }
    //
    //    String baseint1 = new Base1   < Int  >().add( 123 );
    //    String basestr1 = new Base1   <String>().add("abc");
    //    assert baseint1 ==          "B[e=123 S[e=123]S ]B"    ;
    //    assert basestr1 == "MX[e=abc B[e=abc S[e=abc]S ]B ]MX";
    //
    //    class Derived1<E> extends Base1<E> {
    //        @Override String add(E e) = $"D[{e=} " + super(e) + " ]D";
    //    }
    //    String dervint1 = new Derived1< Int  >().add( 123 );
    //    Derived1<String> d1 = new Derived1<String>();
    //    String dervstr1 = d1.add("abc");
    //    assert dervint1 == "D[e=123 B[e=123 S[e=123]S ]B ]D";
    //    assert dervstr1 == "D[e=abc MX[e=abc B[e=abc S[e=abc]S ]B ]MX ]D";
    //
    //    class Super2<E> {
    //        String add(E e) = $"s[{e=}]s";
    //    }
    //    class Base2<E> extends Super2<E> incorporates conditional MixIn<E extends String> {
    //        @Override String add(E e) = $"b[{e=} " + super(e) + " ]b";
    //    }
    //    class Derived2<E> extends Base2<E> {
    //        @Override String add(E e) = $"d[{e=} " + super(e) + " ]d";
    //    }
    //
    //    String baseint2 = new Base2   < Int  >().add( 123 );
    //    String basestr2 = new Base2   <String>().add("abc");
    //    String dervint2 = new Derived2< Int  >().add( 123 );
    //    String dervstr2 = new Derived2<String>().add("abc");
    //    assert baseint2 ==          "b[e=123 s[e=123]s ]b"    ;
    //    assert basestr2 == "MX[e=abc b[e=abc s[e=abc]s ]b ]MX";
    //    assert dervint2 ==          "d[e=123 b[e=123 s[e=123]s ]b ]d" ;
    //    assert dervstr2 == "d[e=abc MX[e=abc b[e=abc s[e=abc]s ]b ]MX ]d";
    //}

    void typevars() {
        interface IFaceRep<KeyIFace> {
            String foo(KeyIFace x) { return $"IFaceRep {x=}"; }
        }
        interface IFaceColl<ElemIFace> {
            String foo(ElemIFace x) { return $"IFaceColl {x=}"; }
        }

        static class Outer<Key,Value> implements IFaceRep<Key> {
            class InnerOut<Elem> implements IFaceColl<Elem> {
                @Override String foo(Elem x) { return $"Inner {x=} {Key=}"; }
            }
            //InnerOut<String> innerFactory() { return new InnerOut<String>(); }
            Derived derivedFactory() { return new Derived(); }

            class Derived extends InnerOut<Key> /*incorporates conditional CondMixin<Key extends Int>*/ {
                @Override String foo(Elem x) { return $"Derived {x=} {Key=}"; }
                //private static mixin CondMixin<Key extends Int> into Derived {
                //    @Override String foo(Key x) { return $"Mix {x=}"; }
                //}
            }
        }

        //Outer<String,Object> so = new Outer<String,Object>();
        Outer<Int   ,Object> io = new Outer<Int   ,Object>();
        //String sfoo = so.foo("abc");
        //String ifoo = io.foo(123);
        //assert sfoo == "IFaceRep x=abc";
        //assert ifoo == "IFaceRep x=123";
        //var si = so.innerFactory();
        //assert si.foo("def") == "Inner x=def Key=String";
        var di = io.derivedFactory();
        //assert di.foo(123) == "Derived x=123 Key=Int";
        //assert di.foo(123) == "Mix x=123";
    }

    // "Annotation": the Mixin is "after" the Base in the class tree:
    // Object <- Base <- MixIn <- Derived

    // I searched the XTC lib and did not find instances of this that were not
    // also specifically runtime-specific, and thus require some kind of custom
    // work anyways.  Mixins like @Future (requires some kind of volatile) or
    // @Synchronized (requires runtime interaction and locking) must be
    // directly intercepted.  Mixins like @Auto represent auto conversions from
    // the front end.  Mixins like @Abstract match the Java abstract keyword.
    //
    // This mixin can be added at any `new` site and does not need to be
    // part of the original class definition.

    //void annot() {
    //    class Base3<Element> {
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
    //    String baseint = new @Mixin Base3   < Int  >().add( 123 );
    //    String basestr = new @Mixin Base3   <String>().add("abc");
    //    String derint  = new Derived3< Int  >().add( 123 );
    //    String derstr  = new Derived3<String>().add("abc");
    //    assert baseint ==         "M[e=123 B[e=123]B ]M"   ;
    //    assert basestr ==         "M[e=abc B[e=abc]B ]M"   ;
    //    assert derint  == "D[e=123 M[e=123 B[e=123]B ]M ]D";
    //    assert derstr  == "D[e=abc M[e=abc B[e=abc]B ]M ]D";
    //}

}
