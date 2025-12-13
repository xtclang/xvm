mixin XMixIn<Q> into XBase1<Q> | XBase2<Q> {
    @Override String add(Q e) = $"MX[{e=} " + super(e) + " ]MX";
}
