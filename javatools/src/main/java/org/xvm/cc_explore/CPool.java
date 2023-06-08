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
        case AnonymousClassType -> new AnonClzTCon(X);
        case Any           -> new MatchAnyCon(X,f);
        case Array, Tuple  -> new AryCon(X,f);
        case Bit, CInt8, Int8, Nibble, CUInt8, UInt8 -> new ByteCon(X,f);
        case BindTarget    -> new MethodBindCon(X);
        case Char          -> new CharCon(X);
        case Class         -> new ClassCon(X);
        case ConditionNamed-> new NamedCondCon(X,f); 
        case Date, Duration, FPLiteral, IntLiteral, Path, Time, TimeOfDay -> new LitCon(X,f);
        case Dec32         -> new Dec32Con(X);
        case Dec64         -> new Dec64Con(X);
        case DecoratedClass -> new DecClzCon(X);
        case DifferenceType -> new DiffTCon(X);
        case DynamicFormal -> new DynFormalCon(X);
        case EnumValueConst-> new EnumCon(X,f);
        case FSDir, FSFile, FSLink -> new FSNodeCon(X,f);
        case FileStore     -> new FileStoreCon(X);
        case Float16       -> new Flt16Con(X);
        case Float32       -> new Flt32Con(X);
        case Float64       -> new Flt64Con(X);
        case FormalTypeChild -> new FormalTChildCon(X);
        case ImmutableType -> new ImmutTCon(X);
        case Int, UInt, CInt16, Int16, CInt32, Int32, CInt64, Int64, CInt128, Int128, CIntN, IntN, CUInt16, UInt16, CUInt32, UInt32, CUInt64, UInt64, CUInt128, UInt128, CUIntN, UIntN -> new IntCon(X,f);
        case IntersectionType -> new InterTCon(X);
        case Map, MapEntry -> new MapCon(X,f);
        case Method        -> new MethodCon(X);
        case Module        -> new ModCon(X);
        case MultiMethod   -> new MMethodCon(X);
        case Package       -> new PackageCon(X);
        case ParameterizedType -> new ParamTCon(X);
        case Property      -> new PropCon(X);
        case PropertyClassType -> new PropClzCon(X);
        case Range, RangeExclusive, RangeInclusive -> new RangeCon(X,f);
        case RecursiveType -> new RecurTCon(X);
        case Register      -> new RegCon(X);
        case Signature     -> new SigCon(X);
        case SingletonConst, SingletonService -> new SingleCon(X,f);
        case String        -> new StringCon(X);
        case TerminalType  -> new TermTCon(X);
        case ThisClass     -> new ThisClzCon(X);
        case TurtleType    -> new TSeqTCon();
        case Typedef       -> new TDefCon(X);
        case TypeParameter -> new TParmCon(X);
        case UInt8Array    -> new UInt8AryCon(X);
        case UnionType     -> new UnionTCon(X);
        case Version       -> new VerCon(X,f);
        case VirtualChildType -> new VirtDepTCon(X);
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
