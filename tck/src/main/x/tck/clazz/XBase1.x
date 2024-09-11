class XBase1<Element> extends XSuper1<Element> incorporates XMixIn<Element> {
    @Override String add(Element e) = $"B[{e=} " + super(e) + " ]B";
}
