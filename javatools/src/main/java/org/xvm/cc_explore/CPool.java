package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
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
        case AnnotatedType -> new AnnotTCon(X);
        case Annotation    -> new Annot(X);
        case Any           -> new MatchAnyCon(X,f);
        case Class         -> new ClassCon(X);
        case ImmutableType -> new ImmutTCon(X);
        case Int           -> new IntCon(X,f);
        case Method        -> new MethodCon(X);
        case Module        -> new ModCon(X);
        case MultiMethod   -> new MMethodCon(X);
        case ParameterizedType -> new ParamTCon(X);
        case Package       -> new PackageCon(X);
        case Path, Time    -> new LitCon(X,f);
        case Signature     -> new SigCon(X);
        case String        -> new StringCon(X);
        case TerminalType  -> new TermTCon(X);
        case Tuple         -> new AryCon(X,f);
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

  public Const get( int idx ) {
    return idx == -1 ? null : _consts.get(idx);
  }
}
