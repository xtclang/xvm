package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

public class Parameter {
  // Parameter annotations
  public final Annot[] _annots;
  // Parameter type
  public final TCon _type;
  // Parameter name
  public final StringCon _name;
  // Default value
  public final Const _def;
  // True IFF a condition-return or a type-parm parm
  public final boolean _special;
  // Parameter index, negative for returns
  public final int _idx;
  
  Parameter( boolean is_ret, int idx, boolean special, CPool X ) {    
    _annots = Annot.xannos(X);
    _type = (TCon)X.xget();
    _name = (StringCon)X.xget();
    _def  = X.xget();
    _idx = is_ret ? -1-idx : idx;
    _special = special;
  }

  @Override public String toString() {
    return _name==null ? "#"+_idx : _name.toString();
  }
}
