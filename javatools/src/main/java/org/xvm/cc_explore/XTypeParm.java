package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

/**
   An XTC generic type: <T> T find(T[] ary, Predicate pred); // T is a generic type for a method
*/
public class XTypeParm extends XType {
  public final Parameter _parm;
  public final XType _type;     // Parent type, e.g. <T extends Number> vs <T extends Object>
  // Parent is the owning method for
  public XTypeParm(MethodPart meth, String name, Parameter parm, XEC.ModRepo repo) {
    super(meth,name,null);
    _parm = parm;
    // Expect Parameter._con to be a ParamTCon, with an array of _parms/_types.
    ParamTCon tparm = (ParamTCon)parm._con;
    tparm.link(repo);
    // Always slot 0
    _type = tparm._types[0];
  }

}
