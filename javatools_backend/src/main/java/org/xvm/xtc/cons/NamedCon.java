package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.CPool;
import org.xvm.xtc.Part;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public abstract class NamedCon extends PartCon {
  public String _name;
  NamedCon( CPool X ) {  X.u31(); X.u31(); }
  @Override public String name() { return _name; }
  @Override public SB str(SB sb) { return super.str(sb.p(_name)); }
  @Override public void resolve( CPool X ) {
    _par  = (PartCon)X.xget();  // Parent is parsed before name
    _name =((StringCon)X.xget())._str;
  }
}
