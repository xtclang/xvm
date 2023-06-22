package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class VirtDepTCon extends DepTCon {
  private final boolean _thisClz;
  private String _name;
  private ClassPart _part;
  public VirtDepTCon( CPool X ) {
    super(X);
    X.u31();
    _thisClz = X.u1();
  }
  @Override public SB str(SB sb) { return super.str(sb.p(_name)); }
  @Override public void resolve( CPool X ) {
    super.resolve(X);
    _name =((StringCon)X.xget())._str;
  }
  @Override public ClassPart link(XEC.ModRepo repo) {
    if( _part!=null ) return _part;
    ClassPart clz = (ClassPart)super.link(repo);
    return (_part = (ClassPart)clz._name2kid.get(_name));
  }
}
