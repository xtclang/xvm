class XBase1<B> extends XSuper1<B> incorporates XMixIn<B> {
    @Override String add(B e) = $"B[{e=} " + super(e) + " ]B";
}
