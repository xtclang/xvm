module TestSimple {
    @Inject Console console;

    void run() {
        Base    b = new Base(1);
        Derived d = new Derived(1);
        b.testAccess(d);
    }

    class Base(Int valBasePro) {
        protected Int valBasePro;
        protected Int fBasePro() = valBasePro;

        private Int valBasePri = valBasePro + 1;
        private Int fBasePri() = valBasePri;

        void testAccess(Derived node) {
            assert node.valBasePro == valBasePro; // this used to fail to compile
            assert node.fBasePro() == fBasePro(); // this used to fail to compile

            // these should not compile
//            assert node.valDerivedPro > 0;
//            assert node.fDerivedPro() > 0;
//            Method m = node.fDerivedPro;
//
//            assert node.valBasePri > 0;
//            assert node.fBasePri() > 0;
        }

        void testAccess(Mixed node) {
            assert node.valBasePro == valBasePro; // this used to fail to compile
            assert node.fBasePro() == fBasePro(); // this used to fail to compile

            // these should not compile
//            assert node.valMixedPro == valBasePro;
//            assert node.fMixedPro() == fBasePro();
        }
    }

    class Derived
            extends Base {
        construct(Int valBasePro) {
            valDerivedPro = valBasePro + 1;
            valDerivedPri = valBasePro + 2;
            construct Base(valBasePro);
        }
        private   Int valDerivedPri;
        protected Int valDerivedPro;
        protected Int fDerivedPro() = valDerivedPro;
    }

    class Derived2
            extends Base {
        construct(Int valBasePro) {
            construct Base(valBasePro);
        }

        void testAccess2(Derived node) {
            assert node.valBasePro == valBasePro; // this used to fail to compile
            assert node.fBasePro() == fBasePro(); // this used to fail to compile
        }
    }

    mixin Mixed(Int valMixedPro)
            into Base {
        protected Int valMixedPro;
        protected Int fMixedPro() = valMixedPro;
    }
}