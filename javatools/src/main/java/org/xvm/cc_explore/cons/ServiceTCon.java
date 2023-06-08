package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class ServiceTCon extends TCon {
  private final transient int _tx;
  private TCon _con;
  public ServiceTCon( FilePart X ) { _tx = X.u31(); }
  @Override public void resolve( CPool pool ) { _con = (TCon)pool.get(_tx); }
}
