package org.xvm.javajit.intrinsic;


import org.xvm.javajit.Container;
import org.xvm.javajit.Xvm;


public class Ctx {
    public Ctx(Xvm xvm) {
        this.xvm = xvm;
    }

    // public static final ScopedValue<Ctx> $Context = ScopedValue.newInstance();

    public final Xvm xvm;

    public static Ctx get() {
//        return $Context.get();
        return null;
    }

    public void debit(int size) {}

    public Container container;

    // xSvc service;
    // etc.
    public int depth;    // call depth
    public int ra;
    public int ca;

    // multi return values
    public Object   o0;
    public Object   o1;
    public Object   o2;
    public Object   o3;
    public Object   o4;
    public Object   o5;
    public Object   o6;
    public Object   o7;
    public long     i0;
    public long     i1;
    public long     i2;
    public long     i3;
    public long[]   iN;
    public Object[] oN;
}
