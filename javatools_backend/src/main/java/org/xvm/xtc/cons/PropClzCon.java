package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.xtc.ClassPart;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class PropClzCon extends DepTCon {
  private PropCon _prop;
  public PropClzCon( CPool X ) { super(X); X.u31(); }
  @Override public void resolve( CPool X ) { super.resolve(X); _prop = (PropCon)X.xget(); }  
  @Override public Part link( XEC.ModRepo repo ) {
    if( _part!=null ) return _part;
    ClassPart par = (ClassPart)_par.link(repo);
    return (_part = par.child(_prop._name));
  }
}