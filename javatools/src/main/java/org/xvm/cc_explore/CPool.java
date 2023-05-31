package org.xvm.cc_explore;

import java.io.IOException;
import java.util.ArrayList;

/**
  Exploring XEC Constant Pool
 */
public class CPool {
  // Constants by index
  private final ArrayList<Const> _consts = new ArrayList<>();
  
  CPool( XEC.XParser X ) throws IOException {

    // load the constant pool from the stream
    int len = X.index();
    for( int i = 0; i < len; i++ ) {
      Const.Format f = Const.Format.valueOf(X.u8());
      _consts.add( switch( f ) {
        case AnnotatedType -> new AnnotTConst(X);
        case Annotation    -> new Annot(X);
        case Any           -> new MatchAnyConst(X,f);
        case Class         -> new ClassConst(X);
        case ImmutableType -> new ImmutTConst(X);
        case Int           -> new IntConst(X,f);
        case Method        -> new MethodConst(X);
        case Module        -> new ModConst(X);
        case MultiMethod   -> new MMethodConst(X);
        case ParameterizedType -> new ParamTConst(X);
        case Package       -> new PackageConst(X);
        case Path, Time    -> new LitConst(X,f);
        case Signature     -> new SigConst(X);
        case String        -> new StringConst(X);
        case TerminalType  -> new TermTConst(X);
        case Tuple         -> new AryConst(X,f);
        default -> {
          System.err.println("Format "+f);
          throw XEC.TODO();
        }
        });
    }

    // Convert indices into refs
    for( Const c : _consts )
      c.resolve(this);
  }

  Const get( int idx ) {
    return idx == -1 ? null : _consts.get(idx);
  }
}
