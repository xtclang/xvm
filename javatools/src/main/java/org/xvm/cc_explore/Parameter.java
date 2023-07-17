package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import org.xvm.cc_explore.tvar.TVar;

public class Parameter {
  private boolean _linked;
  // Parameter name
  public final String _name;
  // Parameter type
  public final TCon _con;
  // Parameter annotations
  public final Annot[] _annots;
  // Default value
  public final TCon _def;
  // True IFF a condition-return or a type-parm parm
  public final boolean _special;
  // Parameter index, negative for returns
  public final int _idx;
  
  Parameter( boolean is_ret, int idx, boolean special, CPool X ) {    
    _annots = Annot.xannos(X);
    _con = (TCon)X.xget();
    StringCon str = (StringCon)X.xget();
    _name = str==null ? null : str._str;
    _def  = (TCon)X.xget();
    _idx = is_ret ? -1-idx : idx;
    _special = special;
  }

  @Override public String toString() {
    return _name==null ? "#"+_idx : _name.toString();
  }

  // Specific internal linking
  public void link( XEC.ModRepo repo ) {
    if( _linked ) return;
    _linked = true;
    _con.link(repo);
    
    if( _annots!=null ) throw XEC.TODO();
    if( _def != null ) _def.link(repo);
  }

  public TVar setype() { return _con.setype(null); }
  public TVar tvar() { return _con.tvar(); }
  public TVar generic_tvar() {
    return ((ParamTCon)_con)._types[0];
  }
}
