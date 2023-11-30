package org.xvm.xtc.cons;

import org.xvm.XEC;
import org.xvm.xtc.*;
import org.xvm.util.SB;

/**
  Exploring XEC Constants
 */
public class TermTCon extends TCon implements ClzCon {
  Const _id;
  private Part _part;
  public TermTCon( CPool X ) { X.u31(); }
  @Override public SB str(SB sb) { return _id.str(sb.p("# -> ")); }
  @Override public ClassPart clz() { return (ClassPart)_part; }
  @Override public void resolve( CPool X ) { _id = X.xget(); }
  public Const id() { return _id; }
  public String name() { return ((IdCon)_id).name(); }
  
  @Override public Part link(XEC.ModRepo repo) {
    return _part==null ? (_part=_id.link(repo)) : _part;
  }
  // After linking, the part call does not need the repo.
  @Override public Part part() { return _part;  }

  @Override int _eq( TCon tc ) {
    // TODO: COMMENTED OUT PARTS are part of a proper XTC ISA test
    //if( tc instanceof UnionTCon utc )
    //  return Math.max(_eq(utc._con1),_eq(utc._con2));

    // TODO: This invariant is lost when I start checking parameterized types
    TermTCon ttc = (TermTCon)tc; // Invariant when called
    assert _part!=null && ttc._part!=null;
    return _part == ttc._part ? 1 : -1;
    //if( _part == ttc._part ) return 1;
    //if( _part instanceof ClassPart && ttc._part instanceof ClassPart )
    //  return ((ClassPart)_part).subclass( (ClassPart)ttc._part ) ? 1 : -1;
    //if( _part instanceof PropPart && ttc._part instanceof PropPart )
    //  return ((ClassPart)ttc._part._par).subclass( (ClassPart)_part._par ) ? 1 : -1;
    //// TODO: really requires tracking TVars
    //if( _part instanceof PropPart && ttc._part instanceof ClassPart )
    //  return 1;                 // Replace type var
    //
    //return -1;
  }
}
