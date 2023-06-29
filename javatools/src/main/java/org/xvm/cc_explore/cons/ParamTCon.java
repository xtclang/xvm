package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.*;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.tvar.TVStruct;

/**
   Parameterized Type Constant.
 */
public class ParamTCon extends TCon {
  TCon _con;
  TCon[] _parms;
  TVStruct _clz;
  public final TVar[] _types;
  
  public ParamTCon( CPool X ) {
    X.u31();
    int len = X.skipAry();
    _types = len==0 ? null : new TVar[len];
  }
  @Override public SB str(SB sb) {
    if( _con instanceof TermTCon tt ) sb.p(tt.name());
    sb.p("<>");
    return _parms==null ? sb : _parms[0].str(sb.p(" -> "));
  }
  @Override public void resolve( CPool X ) {
    _con = (TCon)X.xget();
    _parms = TCon.tcons(X);
  }
  
  @Override TVar _setype( XEC.ModRepo repo ) {
    TVar clz_type = _con.setype(repo);
    if( _parms!=null ) {
      for( int i=0; i<_parms.length; i++ )
        _types[i] = _parms[i].setype(repo);
    }
    throw XEC.TODO();
    //
  //  // Make a fresh copy, so I can parameterize it
  //  TVStruct clz = (TVStruct) (_tvar = _clz.tvar().fresh());
  //  for( int i=0; i<_types.length; i++ ) {
  //    if( _parms[i] instanceof TSeqTCon ) {
  //      assert _clz._name.equals("Type");
  //      // A TSeqTCon marks a self-recursive type
  //      TVar tv = clz.arg("DataType");
  //      // Make tv/self a TVStruct
  //      TVStruct self = tv instanceof TVStruct self0 ? self0 : new TVStruct(true);
  //      if( !(tv instanceof TVStruct) ) tv.unify(self);
  //      // Make self, self-recursive
  //      self.add_fld("DataType",self);
  //    } else {
  //      // The generified type name is used as a field name.  Appender<Element>
  //      // has " Appender" (mangled class name) as first slot, and Element as
  //      // another field name.      
  //      clz.add_fld(_types[i]._name,_types[i].tvar());
  //    }
  //  }
  //
  //  return _clz;
  }

}
