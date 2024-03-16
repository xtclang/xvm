package org.xvm.xec.ecstasy.annotations;

import org.xvm.XEC;
import org.xvm.xec.XTC;
import org.xvm.xrun.Never;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class FutureVar<Referent extends XTC> extends XTC {
  public FutureVar(Never n) {_future=null;}
  
  private final CompletableFuture<Referent> _future;
  public FutureVar() { _future = new CompletableFuture<>(); }
  public Referent $get() throws XTC.Exception {
    try {
      return _future.get();
    } catch( InterruptedException ie ) {
      throw XEC.TODO();         // Not expecting the XTC runtime to interrupt
    } catch( ExecutionException ee ) {
      throw (XTC.Exception)ee.getCause(); // Need to rethrow the completed exception
    }
  }
  public void $set(Referent ref) {  _future.complete(ref); }
}
