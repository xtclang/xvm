package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import java.util.ArrayList;

/**
  Exploring XEC Constant Pool
 */
public class CPool {
  // Constants by index
  private final ArrayList<Const> _consts = new ArrayList<>();
  
  CPool( FilePart X ) {

    // load the constant pool from the stream
    int len = X.u31();
    for( int i = 0; i < len; i++ ) {
      Const.Format f = Const.Format.valueOf(X.u8());
      _consts.add( switch( f ) {
        case AccessType    -> new AccessTCon(X);
        case AnnotatedType -> new AnnotTCon(X);
        case Annotation    -> new Annot(X);
        case Any           -> new MatchAnyCon(X,f);
        case Array         -> new AryCon(X,f);
        case Class         -> new ClassCon(X);
        case Date, Duration, Path, Time, TimeOfDay -> new LitCon(X,f);
        case EnumValueConst-> new EnumCon(X,f);
        case FSDir, FSFile, FSLink -> new FSNodeCon(X,f);
        case FileStore     -> new FileStoreCon(X);
        case ImmutableType -> new ImmutTCon(X);
        case Int           -> new IntCon(X,f);
        case Method        -> new MethodCon(X);
        case Module        -> new ModCon(X);
        case MultiMethod   -> new MMethodCon(X);
        case Package       -> new PackageCon(X);
        case ParameterizedType -> new ParamTCon(X);
        case Property      -> new PropCon(X);
        case Range, RangeExclusive, RangeInclusive -> new RangeCon(X,f);
        case Signature     -> new SigCon(X);
        case SingletonConst, SingletonService -> new SingleCon(X,f);
        case String        -> new StringCon(X);
        case TerminalType  -> new TermTCon(X);
        case ThisClass     -> new ThisClzCon(X);
        case Tuple         -> new AryCon(X,f);
        case TypeParameter -> new TParmCon(X);
        case UInt8Array    -> new UInt8AryCon(X);
        case UnionType     -> new UnionTCon(X);
        case Version       -> new VerCon(X,f);
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
