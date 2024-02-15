package org.xvm.xrun;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.Fun;
import org.xvm.xtc.ClzBldSet;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.XClz;

/**
   Java native implementation of the XTC Timer class
*/
public class NativeTimer {
  public Fun schedule( long lo, long hi, Fun alarm ) {
    throw XEC.TODO();
  }




  public static String make_timer(XClz xtimer) {
    ClzBuilder.add_import(xtimer);
    String qual = (XEC.XCLZ+"."+"NativeTimer").intern();
    String nnn = "new "+qual+"()";
    ClzBuilder.add_import(qual);
    if( ClzBldSet.find(qual) )
      return nnn;

    SB sb = new SB();
    sb.p("// ---------------------------------------------------------------").nl();
    sb.p("// Auto Generated by XRunClz from ");
    xtimer.clz(sb).nl().nl();
    sb.p("package ").p(XEC.XCLZ).p(";").nl().nl();
    sb.fmt("import %0;\n",xtimer.qualified_name());
    sb.fmt("import %0.ecstasy.temporal.Duration;\n",XEC.XCLZ);
    sb.fmt("import %0.ecstasy.numbers.Int128;\n",XEC.XCLZ);
    sb.fmt("import %0.Fun;\n",XEC.XCLZ);
    sb.fmt("import %0.XEC;\n",XEC.ROOT);
    sb.nl();
    sb.p("public class NativeTimer implements Timer {\n").ii();

    sb.ifmt("private final %0.xrun.NativeTimer _timer;",XEC.ROOT).nl();
    sb.ip("public NativeTimer() { _timer = XEC.CONTAINER.get().timer(); }").nl();
    sb.ip("public Duration elapsed$get() { throw XEC.TODO(); }").nl();
    sb.ip("public void elapsed$set( Duration p ) { throw XEC.TODO(); }").nl();
    sb.ip("public void stop(  )  { throw XEC.TODO(); }").nl();
    sb.ip("public Fun schedule( Duration delay, Fun alarm ) { \n").ii();
    sb.ip(  "Int128 pico = delay.picoseconds$get();\n");
    sb.ip(  "return _timer.schedule(pico._lo,pico._hi,alarm);\n").di();
    sb.ip("}").nl();
    sb.ip("public void start(  )  { throw XEC.TODO(); }").nl();
    sb.ip("public void reset(  )  { throw XEC.TODO(); }").nl();
    sb.ip("public Duration resolution$get() { throw XEC.TODO(); }").nl();
    sb.ip("public void resolution$set( Duration p ) { throw XEC.TODO(); }").nl();

    // Class end
    sb.di().ip("}").nl();
    sb.p("// ---------------------------------------------------------------").nl();
    ClzBldSet.add(qual,sb.toString());
    return nnn;
  }
}
