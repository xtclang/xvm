// Tests working with Mixins and Derived
class Medium {
    @Inject Console console;

    void run() {
        incorp();
        annot();
        incorpConditional();
    }


    // Standard "incorporates":  The MixIn is "before" Base in the class tree:
    // Object <- MixIn <- Base <- Derived
    void incorp() {
        class Base1<Element extends Const/*GG bug*/> incorporates MixIn1<Element> {
            @Override String add(Element e) = $"B[{e=} " + super(e) + " ]B";
        }

        mixin MixIn1<Element> into Base1<Element> {
            String add(Element e) = $"M[{e=}]M";
        }

        class Derived1<Element> extends Base1<Element> {
            @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
        }

        String baseint = new Base1   < Int  >().add( 123 );
        String basestr = new Base1   <String>().add("abc");
        String derint  = new Derived1< Int  >().add( 123 );
        String derstr  = new Derived1<String>().add("abc");
        assert baseint ==         "B[e=123 M[e=123]M ]B"   ;
        assert basestr ==         "B[e=abc M[e=abc]M ]B"   ;
        assert derint  == "D[e=123 B[e=123 M[e=123]M ]B ]D";
        assert derstr  == "D[e=abc B[e=abc M[e=abc]M ]B ]D";
    }

    // "Annotation": the Mixin is "after" the Base in the class tree:
    // Object <- Base <- MixIn <- Derived
    void annot() {
        @MixIn3 class Base3<Element> {
            String add(Element e) = $"B[{e=}]B";
        }

        mixin MixIn3 into Base3 {
            @Override String add(Element e) = $"M[{e=} " + super(e) + " ]M";
        }

        class Derived3<Element> extends Base3<Element> {
            @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
        }

        String baseint = new Base3   < Int  >().add( 123 );
        String basestr = new Base3   <String>().add("abc");
        String derint  = new Derived3< Int  >().add( 123 );
        String derstr  = new Derived3<String>().add("abc");
        assert baseint ==         "M[e=123 B[e=123]B ]M"   ;
        assert basestr ==         "M[e=abc B[e=abc]B ]M"   ;
        assert derint  == "D[e=123 M[e=123 B[e=123]B ]M ]D";
        assert derstr  == "D[e=abc M[e=abc B[e=abc]B ]M ]D";
    }

    // Conditional incorporation: the Mixin is both *conditional* and also
    // "after" the Base in the class tree:
    // Object <- Base <-          Derived
    // Object <- Base <- Mixin <- Derived
    void incorpConditional() {
        class Base2<Element> incorporates conditional MixIn2<Element extends String> {
            String add(Element e) = $"B[{e=}]B";
        }

        mixin MixIn2<Element extends String> into Base2<Element> {
            @Override String add(Element e) = $"M[{e=} " + super(e) + " ]M";
        }

        class Derived2<Element> extends Base2<Element> {
            @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
        }

        String baseint = new Base2   < Int  >().add( 123 );
        String basestr = new Base2   <String>().add("abc");
        String derint  = new Derived2< Int  >().add( 123 );
        String derstr  = new Derived2<String>().add("abc");
        assert baseint ==                 "B[e=123]B"      ;
        assert basestr ==         "M[e=abc B[e=abc]B ]M"   ;
        assert derint  ==         "D[e=123 B[e=123]B ]D"   ;
        assert derstr  == "D[e=abc M[e=abc B[e=abc]B ]M ]D";
    }

}
