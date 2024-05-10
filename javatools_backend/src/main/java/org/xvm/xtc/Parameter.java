package org.xvm.xtc;

import org.xvm.XEC;
import org.xvm.xtc.cons.Annot;
import org.xvm.xtc.cons.StringCon;
import org.xvm.xtc.cons.TCon;

public class Parameter {
  // Parameter name
  public String _name;
  // Parameter type
  private final TCon _tcon;
  private XType _box, _raw;
  // Parameter annotations
  public final Annot[] _annots;
  // Default value
  public final TCon _def;
  // True IFF a condition-return or a type-parm parm
  public final boolean _special;
  // Parameter index, negative for returns
  //public final int _idx;

  private Parameter( Annot[] annos, boolean special, TCon tcon, XType xt, String name, TCon def ) {
    _name = name;
    _tcon = tcon;
    _box = xt==null ? null : xt.  box();
    _raw = xt==null ? null : xt.unbox();
    _annots = annos;
    _def = def;
    _special = special;
  }

  private static String str(CPool X) {
    StringCon str = (StringCon)X.xget();
    return str==null ? null : str._str;
  }
  Parameter( boolean special, TCon tcon, CPool X ) {
    this( Annot.xannos(X), special, (TCon)X.xget(), null, str(X), (TCon)X.xget());
  }
  Parameter( String name, TCon tcon) { this(null,false,tcon,null,name,null);  }
  Parameter( String name, XType xt)  { this(null,false,null, xt ,name,null);  }

  @Override public String toString() { return _name; }
  public TCon tcon() { assert _tcon != null; return _tcon; }
  public XType type(boolean box) {
    if( box ) return _box == null ? (_box = XType.xtype(_tcon,true )) : _box;
    else      return _raw == null ? (_raw = XType.xtype(_tcon,false)) : _raw;
  }

}
