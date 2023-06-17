package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

public class Parameter {
  // Parameter type
  final TCon _clzcon;
  private ClassPart _clz;
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
    _clzcon = (TCon)X.xget();
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
  void link( XEC.ModRepo repo ) {
    if( _clzcon!=null ) return;
    _clz = (ClassPart)_clzcon.link(repo);
    if( _annots!=null ) throw XEC.TODO();
    if( _def!=null ) throw XEC.TODO();
  }

}
