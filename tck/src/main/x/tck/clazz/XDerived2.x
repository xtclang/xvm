class XDerived2<Element> extends XBase2<Element> {
    @Override String add(Element e) = $"d[{e=} " + super(e) + " ]d";
}
