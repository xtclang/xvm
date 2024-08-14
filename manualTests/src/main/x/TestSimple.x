module TestSimple {
    @Inject Console console;

    void run() {
    }

    interface Cmp {
        Boolean equals(Cmp that) {
            Ref  ref    = &this;
            Type shared = ref.actualClass.PublicType;
            return this.is(shared.DataType)? == that.is(shared.DataType)? : False; // used to fail to compile
        }
    }
}