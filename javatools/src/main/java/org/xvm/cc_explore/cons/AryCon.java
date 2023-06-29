package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class AryCon extends Const {
  final Format _f;
  private TCon _t;              // Type for whole array
  private Const[] _cons;        // Type for each element
  private ClassPart _clz;       // Array type
  
  public AryCon( CPool X, Const.Format f ) {
    _f = f;
    X.u31();                    // Type index for whole array
    X.skipAry();                // Index for each element
  }
  @Override public SB str(SB sb) { sb.p("[]");  return _t==null ? sb : _t.str(sb.p(" -> "));  }  
  @Override public void resolve( CPool X ) {
    _t = (TCon)X.xget();
    _cons = X.consts();
  }
  @Override public Const resolveTypedefs() { throw XEC.TODO(); }
  //@Override public ClassPart link(XEC.ModRepo repo) {
  //  if( _clz!=null ) return _clz;
  //  if( _cons!=null )
  //    for( Const c : _cons )
  //      c.link(repo);
  //  return (_clz = (ClassPart)_t.link(repo));
  //}
}
