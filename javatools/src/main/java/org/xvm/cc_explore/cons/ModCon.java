package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;

/**
  Exploring XEC Constants
 */
public class ModCon extends PartCon {
  String _name;
  public ModCon( CPool X ) {  X.u31(); }
  @Override public String name() { return _name; }
  @Override public SB str(SB sb) { return super.str(sb.p(_name)); }
  @Override public void resolve( CPool X ) { _name =((StringCon)X.xget())._str; }
  @Override public Part link( XEC.ModRepo repo ) {
    // Modules do direct lookup in the repo
    return _part==null ? (_part = repo.get(name())) : _part;
  }
}