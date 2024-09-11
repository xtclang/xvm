class XDerived2<D2> extends XBase2<D2> {
    @Override String add(D2 e) = $"d[{e=} " + super(e) + " ]d";
}
