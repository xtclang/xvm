package org.xvm.xec.ecstasy;

import org.xvm.XEC;
import org.xvm.xrun.Never;
import org.xvm.xec.XTC;
import org.xvm.util.SB;
import org.xvm.xtc.ClzBuilder;
import org.xvm.xtc.MethodPart;
import org.xvm.xrun.Container;

import static org.xvm.XEC.TODO;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** The Java Service class, implementing an XTC hidden internal class.
    The XTC Service is an *XTC interface*, not an XTC class, but it has many class-like properties.

    The XTC Service interface is thus treated like this Java Class.

First cut Service impl comments:
- $sync Callers call normally; $async callers user alternate entry
- All internal calls just wrap args in a FutureTask & MethodHandle (meth ref);
- - FT is enqueued all in current thread
- $async version returns FT; $sync version returns FT.get()

- Example: a normal call "I64 foo(long arg0, String arg1) { return arg0+cache(arg1).get(); }"
- Entry point in Service:
    private I64   foo(int arg0, String arg1) { return arg0+cache(arg1).get(); } // Does the work, but in the ES thread
    I64          $foo(int arg0, String arg1) { return $$foo(arg0,arg1).get(); } // Blocking entry
    Future<I64> $$foo(int arg0, String arg1) { return $enqueue(new FT$foo(arg0,arg1)); } // Non-blocking entry; enqueues work
    static private class FT$foo extends FutureTask<I64> {
      private final int arg0;
      private final String arg1;
      FT$foo( int arg0, String arg1 ) {
        this.arg0 = arg0;
        this.arg1 = arg1;
      }
      I64 call() { return foo(arg0,arg1); }
    }



- ENqueue FT -
- - Make SvcQ if null
- - Add to SvcQ, get atomic Q size
- - If SvcQ was idle on add, then ALSO poke special FT to Main Q.
- - Main Q only takes FTs from Services, to awaken ES to that SvcQ

- All ES threads pull from Main Q, get a SvcQ.
- One thread works each SvcQ, in order, until empty or e.g. timeout.
- TImeout: re-Q the SvcQ to MainQ, but at the end.  Part of Fairness.
- Idle: no SvcQ on MainQ, so go fetch a different SvcQ
- MainQ is empty... ES manages threads down to min (e.g. 1).


"Logical Thread of Execution"
- Every calling thread maintains a stack of nested services.
- when calling into a service we do a "can nest" check & atomic acquire
- - if can nest, run the service in the current thread
- - if not nest, enqueue a FJ task and block
- - if running async ALWAYS fail "can nest" and enqueue
- When nesting increase current thread stack by self service
- "Can nest" if svc is already on stack, always "can nest"
- - If fail atomic acquire, enqueue

Plan C-
- Simple, bad
- 1 Thread per Svc, personal Q
- Returns are boxed



 */
public abstract class Service extends XTC
{
  public Service() {}             // Explicit no-arg-no-work constructor
  public Service(Never n) {}      // Forced   no-arg-no-work constructor


  // Service work Q.  Can be null for idle.
  private volatile LinkedBlockingQueue<XFT> Q; // Queue for this Service, to order callers

  // Self worker thread.
  private volatile SvcThread T;

  // Self container, has to be the same as the allocating thread
  final Container C = XEC.CONTAINER.get();

  private class SvcThread extends Thread {
    SvcThread() {
      setName(getClass().getSimpleName());
    }

    public void run() {
      XEC.CONTAINER.set(C);
      while( true ) {
        try {
          XFT xft = Q.poll(1000,TimeUnit.MILLISECONDS);
          if( xft==null )       // Idle queue
            synchronized(this) {
              xft = Q.poll();   // Check again under lock
              if( xft==null ) { // Queue is idle, kill queue and thread
                Q = null;
                T = null;
                break;          // Thread dies here
              }
            }
          xft.run();
        } catch( InterruptedException ee ) {
          System.err.println(ee);
          throw new RuntimeException(ee);
        }
      }
    }
  }


  // Run by current thread, not service thread
  public <V> XFT<V> $enqueue(Callable<V> call) {
    XFT<V> xft = new XFT<>(call);
    if( Q==null )
      synchronized(this) {
        if( Q == null ) {
          Q = new LinkedBlockingQueue<>();
          T = new SvcThread();
          T.start();
        }
      };

    try {  Q.put(xft); }
    catch( InterruptedException ee ) { System.err.println(ee); throw new RuntimeException(ee); }
    return xft;
  }

  // A FutureTask with a no-checked-exception get.
  // Probably need e.g. CompletableFuture which doesn't throw exceptions.
  public static class XFT<V> extends FutureTask<V> {
    XFT(Callable<V> call) { super(call); }
    public V xget() {
      try {
        return get();
      } catch( InterruptedException | ExecutionException ie ) {
        throw new XTC.Exception(ie.toString());
      }
    }
  }

  // A named callable.  Nicer debugging.
  public abstract static class XCall<V> implements Callable<V> {
    public final String _name;
    public XCall(String name) { _name=name; }
    @Override public String toString() { return _name; }
  }

  static public void make_methods(MethodPart meth, String java_class_name, SB sb ) {
    ClzBuilder.add_import("java.util.concurrent.FutureTask");
    ClzBuilder.add_import("java.util.concurrent.Callable");

    // Blocking single-$ version.
    // Run by current thread, not service thread
    //RET $MNAME(ARGS) { return $$foo(ARGS).get(); }  // Blocking entry
    sb.ip("public ");
    ClzBuilder.ret_sig(meth,sb);
    sb.p("$").p(meth._name).p("( ");
    ClzBuilder.args(meth,sb);
    sb.p(" ) { ");
    if( meth.xrets()!=null ) sb.p("return ");
    sb.p("$$").p(meth._name).p("(");
    ClzBuilder.arg_names(meth,sb);
    sb.p(").xget(); }").nl();


    SB gsb = new SB().p("XFT");
    if( meth.xrets()!=null )
      ClzBuilder.ret_sig(meth,gsb.p("<")).p(">");
    String gen_ft = gsb.toString();

    // Non-blocking double-$ version
    // Run by current thread, not service thread
    //XFT<RET> $$MNAME(ARGS) { return $enqueue(new Call$MNAME(ARGS)) }
    sb.ip("public ").p(gen_ft);
    sb.p(" $$").p(meth._name).p("( ");
    ClzBuilder.args(meth,sb);
    sb.p(" ) { return $enqueue(new Call$").p(meth._name).p("( ");
    ClzBuilder.arg_names(meth,sb);
    sb.p(")); }").nl();

    // Now the wrapper class, one per every function call.
    //static private class Call$foo extends Callable<I64> {
    //  private final int arg0;
    //  private final String arg1;
    //  Call$foo( int arg0, String arg1 ) {
    //    this.arg0 = arg0;
    //    this.arg1 = arg1;
    //  }
    //  I64 call() { return foo(arg0,arg1); }
    //}

    sb.ifmt("private class Call$%0 extends XCall implements Callable {\n",meth._name).ii();
    // Final fields for args
    if( meth.xargs() != null )
      for( int i=0; i<meth.xargs().length; i++ )
        sb.ifmt("private final %0 %1;\n",meth.xarg(i).clz(),meth._args[i]._name);
    // Constructor
    sb.ifmt("Call$%0( ",meth._name);
    ClzBuilder.args(meth,sb);
    sb.p(") {").nl().ii();
    sb.ifmt("super(\"%0.%1\");\n",java_class_name,meth._name);
    if( meth.xargs() != null )
      for( int i=0; i<meth.xargs().length; i++ )
        sb.ifmt("this.%0 = %0;\n",meth._args[i]._name);
    sb.di().ip("}\n");          // End of constructor
    // Call
    sb.ip("public ");
    if( meth.xrets()!=null ) ClzBuilder.ret_sig(meth,sb);
    else sb.p("Object");
    sb.p(" call() { ");
    if( meth.xrets()!=null ) sb.p("return ");
    sb.p(meth._name).p("(");
    ClzBuilder.arg_names(meth,sb);
    sb.p("); ");
    if( meth.xrets()==null ) sb.p("return null; ");
    sb.p("}\n");                // End of call
    sb.di().ip("}\n");          // End of class
  }

}
