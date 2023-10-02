package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.tvar.TVLeaf;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.util.SB;
import org.xvm.cc_explore.XEC;

/**
   Near as I can tell, this is specifically for making a self-recursive Tuple
   with a single type member.
   e.g. Type<T extends Type<T>>
   e.g.  < ParamTypes extends Tuple< ParamTypes>>
   e.g.  <ReturnTypes extends Tuple<ReturnTypes>>
   e.g.  interface Function<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
         extends Signature<ParamTypes, ReturnTypes> {

 */
public class TSeqTCon extends TCon {
  @Override public SB str( SB sb ) { return sb.p("self_recur_type_marker"); }
}
