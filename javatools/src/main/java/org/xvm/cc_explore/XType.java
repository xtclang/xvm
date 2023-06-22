package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Fakes as an XTC Type
*/
public class XType extends Part {
  XType( Part par, int nFlags, IdCon id, String name, CondCon cond, CPool X ) {
    super(par,nFlags,id,name,cond,X);
  }
  XType(Part par, String name, CPool X) { this(par,0,null,name,null,X);  }
}
