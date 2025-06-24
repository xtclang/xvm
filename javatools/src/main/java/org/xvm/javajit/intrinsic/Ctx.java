package org.xvm.javajit.intrinsic;

import org.xvm.javajit.Container;
import org.xvm.javajit.Xvm;

public class Ctx {
    public Ctx(Xvm xvm, Container container) {
        this.xvm = xvm;
        this.container = container;
    }

    public static final ScopedValue<Ctx> $Context = ScopedValue.newInstance();

    public final Xvm xvm;

    public final Container container;

    public static Ctx get() {
        return $Context.get();
    }

    public void debit(int size) {}

    // xSvc service;
    // etc.
    public int depth;    // call depth
    public int ra;
    public int ca;

    // multi return values (value "zero" is returned naturally)
    public Object   o1;
    public Object   o2;
    public Object   o3;
    public Object   o4;
    public Object   o5;
    public Object   o6;
    public Object   o7;
    public long     i1;
    public long     i2;
    public long     i3;
    public long[]   iN;
    public Object[] oN;
}
