class XDerived1<Element> extends XBase1<Element> {
    @Override String add(Element e) = $"D[{e=} " + super(e) + " ]D";
}
