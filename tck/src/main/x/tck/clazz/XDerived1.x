class XDerived1<D> extends XBase1<D> {
    @Override String add(D e) = $"D[{e=} " + super(e) + " ]D";
}
