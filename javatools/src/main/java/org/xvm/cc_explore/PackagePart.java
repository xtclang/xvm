package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   Package part
 */
public class PackagePart extends ClassPart {
  PackagePart( Part par, int nFlags, PackageCon id, CondCon cond, CPool X ) {
    super(par,nFlags,id,cond,X,Part.Format.PACKAGE);
  }  
}
