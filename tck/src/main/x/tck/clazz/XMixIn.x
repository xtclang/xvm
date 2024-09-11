
mixin XMixIn<Element> into XBase1<Element> | XBase2<Element> {
    @Override String add(Element e) = $"MX[{e=} " + super(e) + " ]MX";
}
