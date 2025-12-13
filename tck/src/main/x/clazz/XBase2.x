class XBase2<B2> extends XSuper2<B2> incorporates XMixIn<B2> {
    @Override String add(B2 e) = $"b[{e=} " + super(e) + " ]b";
}
