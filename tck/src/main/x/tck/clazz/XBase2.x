class XBase2<Element> extends XSuper2<Element> incorporates XMixIn<Element> {
    @Override String add(Element e) = $"b[{e=} " + super(e) + " ]b";
}
