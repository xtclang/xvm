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
  private XType _xtype;
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
    _xtype = xt;
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
  public XType type() {
    return _xtype == null ? (_xtype = XType.xtype(_tcon,false)) : _xtype;
  }

}
