package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.CPool;

/**
  Exploring XEC Constants
 */
public class ServiceTCon extends TCon {
  private TCon _con;
  public ServiceTCon( CPool X ) { X.u31(); }
  @Override public void resolve( CPool X ) { _con = (TCon)X.xget(); }
}
