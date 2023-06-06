package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.ModCon;
import org.xvm.cc_explore.cons.Const;

import java.util.Arrays;

/**
     DAG structure containment of components
 */
public class FilePart extends Part {
  /**
   * The special sequence of bytes that identifies an XVM FileStructure.
   */
  static final int FILE_MAGIC = 0xEC57A5EE;

  /**
   * The current major version of the XVM FileStructure. This is the newest
   * version that can be read and/or written by this implementation.
   *
   * By convention, version 0 is the pre-production version: The language and
   * tool-chain are still in development.
   */
  static final int VERSION_MAJOR_CUR = 0;
  final int _major;
  /**
   * The current minor version of the XVM File structure. This is the newest
   * version that can be written by this implementation. (Newer minor versions
   * can be safely read.)
   *
   * By convention, as long as VERSION_MAJOR_CUR == 0, whenever a change is made to Ecstasy that
   * changes the persistent structure of an ".xtc" file in a manner that isn't both forwards and
   * backwards compatible, the minor version will be updated to the 8-digit ISO 8601 date (i.e.
   * the date string with the "-" characters having been removed). The result is that an error
   * will be displayed if there is a version mismatch, which should save some frustration -- since
   * otherwise the resulting error(s) can be very hard to diagnose.
   */
  static final int VERSION_MINOR_CUR = 20230504;
  final int _minor;

  // Constant pool...
  final CPool _pool;

  // Main module name
  final String _modName;
  
  // Parser state
  byte[] _buf;                  // Bits from the XTC file
  int x;                        // Cursor

  // Lazy parse kids
  final boolean _lazy;

  static final int NFLAGS =
    (Part.Format.FILE.ordinal() << Part.Format.FORMAT_SHIFT) |
    (Const.Access.PUBLIC.ordinal() << ACCESS_SHIFT) |
    ABSTRACT_BIT | 
    STATIC_BIT | 
    SYNTHETIC_BIT;

  

  // Constructor parses byte array, builds FileComponent
  FilePart( byte[] buf ) {
    super(null,NFLAGS,null,null,null);
    _lazy = true;
    
    _buf = buf;

    int magic = i32();
    if( magic!=FILE_MAGIC )
      throw new IllegalArgumentException("not an .xtc format file; invalid magic header: " + String.format("0x%x",magic));
    
    _major = i32();
    _minor = i32();
    if( !isFileVersionSupported(_major,_minor) )
      throw new IllegalArgumentException("unsupported version: " + _major + "." + _minor);

    // Build the constant pool
    _pool = new CPool(this);

    // Easy access to module name; will be non-null (TODO: or crash)
    _modName = ((ModCon)_pool.get(u31())).name();

    // Parse any children components
    parseKids(this);
  }


  // Can we handle this version?
  static boolean isFileVersionSupported(int major, int minor) {
    return major==VERSION_MAJOR_CUR && minor==VERSION_MINOR_CUR;
  }

  // ------------------------------------
  // File parser utilities
  public boolean u1 () { return _buf[x++]!=0; } // boolean read
  public int     u8 () { return _buf[x++]&0xFF; } // Unsigned byte read as an int
  public int     u16() { return (u8()<<8) | u8(); } // Unsigned short read as an int
  public int i32() { return (u8()<<24) | (u8()<<16) | (u8()<<8) | u8(); } // Signed 4-byte integer read
  public long pack64() {
    // See org.xvm.util.PackedInteger;
      
    // Tiny: For a value in the range -64..63 (7 bits), the value can be
    // encoded in one byte.  The least significant 7 bits of the value are
    // shifted left by 1 bit, and the 0x1 bit is set to 1.  When reading in a
    // packed integer, if bit 0x1 of the first byte is 1, then it's Tiny.      
    int b = _buf[x++];          // Signed byte read
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
      return x;
    }

    // Huge format
    throw XEC.TODO();
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
  public byte[] bytes() {
    int len = u31();
    return Arrays.copyOfRange(_buf,x,x+=len);
  }
  
  // Read an array of idxs
  public int[] idxAry() {
    int len = u31();      
    int[] txs = new int[len];
    for( int i=0; i<len; i++ )
      txs[i] = u31();
    return txs;
  }

  // Read a UTF8 char
  int utf8Char() {
    int b = u8();
    if( (b&0x80)==0 ) return (char)b; // ASCII single byte
    throw XEC.TODO();
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
    return SB.toString();
  }    
}
