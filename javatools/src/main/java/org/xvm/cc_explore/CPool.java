package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;
import java.math.BigInteger;
import java.util.Arrays;


/**
  Exploring XEC Constant Pool
 */
public class CPool {
  // Constants by index
  private final Const[] _consts;
  public final int _magic, _major, _minor;

  // Parser state
  private final byte[] _buf;
  private int x;
  
  CPool( byte[] buf ) {
    _buf = buf;
    _magic = i32();
    _major = i32();
    _minor = i32();
    int len = u31();
    _consts = new Const[len];
  }
  void parse( ) {
    int len = _consts.length;
    int[] offs = new int[len];

    // Pass#1; load the constant pool from the stream; compute offsets and make empty objects
    for( int i = 0; i < len; i++ ) {
      Const.Format f = Const.Format.valueOf(u8());
      offs[i] = x;
      _consts[i] = switch( f ) {
        case AccessType    -> new AccessTCon(this);
        case AnnotatedType -> new AnnotTCon(this);
        case Annotation    -> new Annot(this);
        case AnonymousClassType -> new AnonClzTCon(this);
        case Any           -> new MatchAnyCon(this,f);
        case Array, Set, Tuple -> new AryCon(this,f);
        case Bit, CInt8, Int8, Nibble, CUInt8, UInt8 -> new ByteCon(this,f);
        case BindTarget    -> new MethodBindCon(this);
        case Char          -> new CharCon(this);
        case Class         -> new ClassCon(this);
        case ConditionNamed-> new NamedCondCon(this,f); 
        case Date, Duration, FPLiteral, IntLiteral, Path, Time, TimeOfDay -> new LitCon(this,f);
        case Dec           -> new DecACon(this);
        case DecN, FloatN  -> new FPNCon(this,f);
        case Dec32         -> new Dec32Con(this);
        case Dec64         -> new Dec64Con(this);
        case Dec128        -> new Dec128Con(this);
        case DecoratedClass -> new DecClzCon(this);
        case DifferenceType -> new DiffTCon(this);
        case DynamicFormal -> new DynFormalCon(this);
        case EnumValueConst-> new EnumCon(this,f);
        case FSDir, FSFile, FSLink -> new FSNodeCon(this,f);
        case FileStore     -> new FileStoreCon(this);
        case Float32, Float16, BFloat16 -> new Flt32Con(this,f);
        case Float64       -> new Flt64Con(this);
        case Float128      -> new Flt128Con(this);
        case Float8e4      -> new Flt8e4Con(this); 
        case Float8e5      -> new Flt8e5Con(this);
        case FormalTypeChild -> new FormalTChildCon(this);
        case ImmutableType -> new ImmutTCon(this);
        case InnerChildType -> new InnerDepTCon(this);
        case Int, UInt, CInt16, Int16, CInt32, Int32, CInt64, Int64, CInt128, Int128, CIntN, IntN, CUInt16, UInt16, CUInt32, UInt32, CUInt64, UInt64, CUInt128, UInt128, CUIntN, UIntN -> new IntCon(this,f);
        case IntersectionType -> new InterTCon(this);
        case IsConst, IsEnum, IsModule, IsPackage, IsClass -> new KeywordCon(f);
        case Map, MapEntry -> new MapCon(this,f);
        case Method        -> new MethodCon(this);
        case Module        -> new ModCon(this);
        case MultiMethod   -> new MMethodCon(this);
        case Package       -> new PackageCon(this);
        case ParameterizedType -> new ParamTCon(this);
        case ParentClass   -> new ParClzCon(this);
        case Property      -> new PropCon(this);
        case PropertyClassType -> new PropClzCon(this);
        case Range, RangeExclusive, RangeInclusive -> new RangeCon(this,f);
        case RecursiveType -> new RecurTCon(this);
        case Register      -> new RegCon(this);
        case ServiceType   -> new ServiceTCon(this);
        case Signature     -> new SigCon(this);
        case SingletonConst, SingletonService -> new SingleCon(this,f);
        case String        -> new StringCon(this);
        case TerminalType  -> new TermTCon(this);
        case ThisClass     -> new ThisClzCon(this);
        case TurtleType    -> new TSeqTCon();
        case Typedef       -> new TDefCon(this);
        case TypeParameter -> new TParmCon(this);
        case UInt8Array    -> new UInt8AryCon(this);
        case UnionType     -> new UnionTCon(this);
        case Version       -> new VerCon(this,f);
        case VirtualChildType -> new VirtDepTCon(this);
        default -> {
          System.err.println("Format "+f);
          throw XEC.TODO();
        }
        };
    }

    // Pass#2.  Convert indices into refs; fill in objects.
    int oldx = x;
    for( int i = 0; i < len; i++ ) {
      x = offs[i];              // Reset parsing state
      _consts[i].resolve(this);
    }
    x = oldx;
  }

  // Get via index; -1 returns null
  public Const get( int idx ) { return idx == -1 ? null : _consts[idx]; }

  // Get from a pre-resolved constant pool
  public Const xget() { return get(u31()); }
  
  // ------------------------------------
  // File parser utilities
  public boolean u1 () { return _buf[x++]!=0; }     // boolean read
  public int     u8 () { return _buf[x++]&0xFF; }   // Unsigned byte  read as an int
  public int     i8 () { return _buf[x++]; }        //   Signed byte  read as an int
  public int     u16() { return (u8()<<8) | u8(); } // Unsigned short read as an int
  public int     i32() { return (u8()<<24) | (u8()<<16) | (u8()<<8) | u8(); } // Signed 4-byte integer read
  public long    i64() { return (((long)i32())<<32) | ((long)(i32()) & 0xFFFFFFFFL); }
  public void undo() { x--; }
  public long pack64() {
    // See org.xvm.util.PackedInteger;
      
    // Tiny: For a value in the range -64..63 (7 bits), the value can be
    // encoded in one byte.  The least significant 7 bits of the value are
    // shifted left by 1 bit, and the 0x1 bit is set to 1.  When reading in a
    // packed integer, if bit 0x1 of the first byte is 1, then it's Tiny.      
    int b = i8();               // Signed byte read
    // xxxxxxx1
    if( (b&1)!=0 ) return b>>1; // Tiny
    
    // Small: For a value in the range -4096..4095 (13 bits), the value can
    // be encoded in two bytes. The first byte contains the value 0x2 (010)
    // in the least significant 3 bits, and bits 8-12 of the integer in bits
    // 3-7; the second byte contains bits 0-7 of the integer.
    if( (b&2)!=0 ) {   // xxxxx?10
      int x = ((b & 0xFFFFFFF8) << 5) | u8();
      return (b&4)==0    // xxxxx?10
        ?  x             // xxxxx010
        : (x<<8) | u8(); // xxxxx110
    }

    // Large format: 1-8 trailing bytes
    if( (b&0xFF) != 0b11111100 ) { 
      int c = ((b&0xFC)>>>2)+2-1;  // Count of bytes; minus one for the self byte
      if( c==1 ) return u8();
      if( c>8 ) throw new IllegalArgumentException("# trailing bytes="+c);
      long x = 0;
      for( int i=0; i<c; i++ )
        x = (x<<8) | u8();
      if( c==2 ) return (short)x; // Sign extend as-if 2 bytes
      if( c<=4 ) return (int)x;   // Sign extend as-if 4 bytes
      return x;
    }

    // Huge format.  IntCon sizes this large use the isize/bigint API and so
    // don't call here.  In other cases, its an error.
    throw XEC.TODO();
  }

  // Return the number of packed bytes in this integer, advances the cursor 1
  // byte.  Returns -1 for a huge format, which requires another packed read to
  // get the actual size.
  public int isize() {
    int b = i8();               // Signed byte read
    // Tiny: xxxxxxx1
    if( (b&1)!=0 ) return 1;    // Tiny; 1 byte

    // Small/Medium: xxxxx?10
    if( (b&2)!=0 ) return (b&4)==0 ? 2 : 3;

    // Large format: 1-8 trailing bytes
    if( (b&0xFF) != 0b11111100 ) return ((b&0xFC)>>>2)+2;

    // Huge format
    return -1;
  }

  // Read a BigInteger
  public BigInteger bigint(int c) {
    assert c > 8;               // Use the packed format for smaller stuff
    if( c > 1024 ) throw new IllegalArgumentException("integer size of " + c + " bytes; maximum is 1024");
    return new BigInteger(_buf,(x+=c)-c,c);
  }

  // Unsigned 31 bit, but might read from packed as larger.
  public int u31() {
    long n = pack64();
    // this is unsupported in Java; arrays are limited in size by their use
    // of signed 32-bit magnitudes and indexes
    if( n > Integer.MAX_VALUE )  throw new IllegalArgumentException("index (" + n + ") exceeds 32-bit maximum");
    if( n < -1 )                 throw new IllegalArgumentException("negative index (" + n + ") is illegal");
    return (int) n;
  }

  // Read a byte array
  public byte[] bytes() { return bytes(u31()); }
  public byte[] bytes(int len) {
    return Arrays.copyOfRange(_buf,x,x+=len);
  }
  
  // Skip an array of idxs
  public int skipAry() {
    int len = u31();      
    for( int i=0; i<len; i++ )  u31();
    return len;
  }
  
  private int utf8Byte() {
    int n = u8();
    if( (n & 0b11000000) != 0b10000000 )
      throw new IllegalArgumentException("trailing unicode byte does not match 10xxxxxx");
    return n & 0b00111111;
  }

  // Parse an array of Const from a pre-filled constant pool
  public Const[] consts() {
    int len = u31();
    if( len==0 ) return null;
    Const[] as = new Const[len];
    for( int i=0; i<len; i++ )  as[i] = xget();
    return as;
  }

  
  // Read a UTF8 char
  public int utf8Char() {
    int b = u8();
    if( (b&0x80)==0 ) return (char)b; // ASCII single byte

    // otherwise the format is based on the number of high-order 1-bits:
    // #1s first byte  trailing  # trailing  bits  code-points
    // --- ----------  --------  ----------  ----  -----------------------
    //  2  110xxxxx    10xxxxxx      1        11   U+0080    - U+07FF
    //  3  1110xxxx    10xxxxxx      2        16   U+0800    - U+FFFF
    //  4  11110xxx    10xxxxxx      3        21   U+10000   - U+1FFFFF
    //  5  111110xx    10xxxxxx      4        26   U+200000  - U+3FFFFFF
    //  6  1111110x    10xxxxxx      5        31   U+4000000 - U+7FFFFFFF
    return switch( Integer.highestOneBit( ~(0xFFFFFF00 | b) ) ) {
    case 0b00100000 -> (b & 0b00011111) <<  6 | utf8Byte();
    case 0b00010000 -> (b & 0b00001111) << 12 | utf8Byte() <<  6 | utf8Byte();
    case 0b00001000 -> (b & 0b00000111) << 18 | utf8Byte() << 12 | utf8Byte() <<  6 | utf8Byte();
    case 0b00000100 -> (b & 0b00000011) << 24 | utf8Byte() << 18 | utf8Byte() << 12 | utf8Byte() <<  6 | utf8Byte();
    case 0b00000010 -> (b & 0b00000001) << 30 | utf8Byte() << 24 | utf8Byte() << 18 | utf8Byte() << 12 | utf8Byte() << 6 | utf8Byte();
    default -> throw new IllegalArgumentException( "initial byte: " + Integer.toHexString( b ) );
    };
  }
  
  // Read a UTF8 string
  private static final StringBuilder SB = new StringBuilder();
  public String utf8() {
    SB.setLength(0);
    int len = u31();
    int len2 = len - u31();
    for( int i=0; i<len2; i++ ) {
      int ch = utf8Char();
      if( ch > 0xFFFF ) throw XEC.TODO();
      SB.append((char)ch);
    }
    return SB.toString().intern();
  }    
}
