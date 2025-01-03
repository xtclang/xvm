package org.xvm.xrun;

import org.xvm.XEC;
import org.xvm.util.SB;
import org.xvm.xec.Fun;
import org.xvm.xtc.ClzBldSet;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.XClz;

import java.util.Timer;
import java.util.TimerTask;

/**
   Java native implementation of the XTC Timer class
*/
public class NativeTimer {
  // Time is in 128bit picoseconds
  public Fun schedule( long lo, long hi, Fun alarm, boolean keepAlive ) {
    if( keepAlive ) throw XEC.TODO();
    // shift lo/hi by 1e9 as binary, then correct with double.
    long T = 1L<<30;
    long loHalf = lo + (T>>1);   // Round up pico to milli
    long kmsec = (hi<<(64-30))|(loHalf>>>30);
    double dmsec = ((double)kmsec)*((double)T)/1e9;
    long msec = (long)dmsec;
    // debugging check
    long delta = msec - System.currentTimeMillis();

    // Make a timer object; it will self-cancel after running this one task
    Timer timer = new Timer();
    var task = new TimerTask() {
        final Timer _timer = timer;
        public void run() {
          alarm.call();
          _timer.cancel();
        }
      };
    timer.schedule(task, msec);

    return alarm;
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
    sb.ip(  "return _timer.schedule(pico._lo,pico._hi,alarm,false);\n").di();
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

  public static String make_clock(XClz xclock) {
    ClzBuilder.add_import(xclock);
    String qual = (XEC.XCLZ+"."+"NativeClock").intern();
    String nnn = "new "+qual+"()";
    ClzBuilder.add_import(qual);
    if( ClzBldSet.find(qual) )
      return nnn;

    SB sb = new SB();
    sb.p("// ---------------------------------------------------------------").nl();
    sb.p("// Auto Generated by XRunClz from ");
    xclock.clz(sb).nl().nl();
    sb.p("package ").p(XEC.XCLZ).p(";").nl().nl();
    sb.fmt("import %0;\n",xclock.qualified_name());
    sb.fmt("import %0.ecstasy.temporal.Duration;\n",XEC.XCLZ);
    sb.fmt("import %0.ecstasy.numbers.Int128;\n",XEC.XCLZ);
    sb.fmt("import %0.ecstasy.Boolean;\n",XEC.XCLZ);
    sb.fmt("import %0.ecstasy.temporal.Time;\n",XEC.XCLZ);
    sb.fmt("import %0.ecstasy.temporal.TimeZone;\n",XEC.XCLZ);
    sb.fmt("import %0.Fun;\n",XEC.XCLZ);
    sb.fmt("import %0.XEC;\n",XEC.ROOT);
    sb.nl();
    sb.p("public class NativeClock implements Clock {\n").ii();

    sb.ifmt("private final %0.xrun.NativeTimer _timer;",XEC.ROOT).nl();
    sb.ip("public NativeClock() { _timer = XEC.CONTAINER.get().timer(); }").nl();
    sb.ip("public Duration resolution$get() { throw XEC.TODO(); }").nl();
    sb.ip("public void resolution$set( Duration p ) { throw XEC.TODO(); }").nl();
    sb.ip("public boolean monotonic$get() { throw XEC.TODO(); }").nl();
    sb.ip("public void monotonic$set(boolean b) { throw XEC.TODO(); }").nl();

    sb.ip("public Time now$get() {").nl().ii();
    sb.ip(  "Int128 picos = new Int128(System.currentTimeMillis()).mul(new Int128(1000000000L));\n");
    sb.ip(  "return Time.construct(picos,TimeZone.UTC$get());\n").di();
    sb.ip("}").nl();

    sb.ip("public void now$set(Time t) { throw XEC.TODO(); }").nl();

    sb.ip("public Fun schedule( Time time, Fun alarm, boolean keepAlive ) { \n").ii();
    sb.ip(  "Int128 now = now$get().epochPicos$get();\n");
    sb.ip(  "Int128 pico = time.epochPicos$get();\n");
    sb.ip(  "Int128 delta = pico.sub(now);\n");
    sb.ip(  "return _timer.schedule(delta._lo,delta._hi,alarm,keepAlive);\n").di();
    sb.ip("}").nl();

    sb.ip("public Fun schedule( Time time, Fun alarm, Boolean keepAlive ) { throw XEC.TODO(); }").nl();
    sb.ip("public TimeZone timezone$get() { throw XEC.TODO(); }").nl();
    sb.ip("public void timezone$set(TimeZone t) { throw XEC.TODO(); }").nl();

    // Class end
    sb.di().ip("}").nl();
    sb.p("// ---------------------------------------------------------------").nl();
    ClzBldSet.add(qual,sb.toString());
    return nnn;
  }
}
