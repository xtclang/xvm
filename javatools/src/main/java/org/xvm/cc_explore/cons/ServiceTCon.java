package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Annot;
import org.xvm.cc_explore.CPool;
import org.xvm.cc_explore.FilePart;

/**
  Exploring XEC Constants
 */
public class ServiceTCon extends TCon {
  private TCon _con;
  public ServiceTCon( FilePart X ) { X.u31(); }
  @Override public void resolve( FilePart X ) { _con = (TCon)X.xget(); }
}
