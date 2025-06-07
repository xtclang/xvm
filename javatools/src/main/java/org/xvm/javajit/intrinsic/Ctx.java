package org.xvm.javajit.intrinsic;


import org.xvm.javajit.Xvm;


public class Ctx {
    public Ctx(Xvm xvm) {
        this.xvm = xvm;
    }

//    public static final ScopedValue<Ctx> $Context = ScopedValue.newInstance();

    public final Xvm xvm;

    public static Ctx get() {
//        return $Context.get();
        return null;
    }

    // xCnt container;
    // xSvc service;
    // etc.
    int depth;    // call depth
    int ra;
    int ca;

    // multi return values
    Object   o0;
    Object   o1;
    Object   o2;
    Object   o3;
    Object   o4;
    Object   o5;
    Object   o6;
    Object   o7;
    long     i0;
    long     i1;
    long     i2;
    long     i3;
    long[]   iN;
    Object[] oN;
}
