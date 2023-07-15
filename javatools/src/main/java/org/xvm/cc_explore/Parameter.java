package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVar;

public class Parameter {
  // Parameter type
  public final TCon _con;
  private Part _part;
  // Parameter annotations
  public final Annot[] _annots;
  // Parameter name
  public final String _name;
  // Default value
  public final Const _def;
  // True IFF a condition-return or a type-parm parm
  public final boolean _special;
  // Parameter index, negative for returns
  public final int _idx;
  
  Parameter( boolean is_ret, int idx, boolean special, CPool X ) {    
    _annots = Annot.xannos(X);
    _con = (TCon)X.xget();
    StringCon str = (StringCon)X.xget();
    _name = str==null ? null : str._str;
    _def  = X.xget();
    _idx = is_ret ? -1-idx : idx;
    _special = special;
  }

  @Override public String toString() {
    return _name==null ? "#"+_idx : _name.toString();
  }

  // Specific internal linking
  public void link( XEC.ModRepo repo ) {
    if( _con.has_tvar() ) return;
    _con.setype(repo);
    
    if( _annots!=null ) throw XEC.TODO();
    if( _def!=null ) _def.con_link(repo);
  }

  public TVar tvar() { return _con.tvar(); }
  public TVar generic_tvar() {
    return ((ParamTCon)_con)._types[0];
  }
}
