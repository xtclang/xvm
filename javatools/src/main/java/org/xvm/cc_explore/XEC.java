package org.xvm.cc_explore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
  Exploring XTC bytecodes.  Fakes as a XEC runtime translator to JVM bytecodes.
 */
public class XEC {
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

  // Constant pool...
  final CPool _pool;


  // Constructor just fills in fields
  XEC( CPool pool ) {
    _pool = pool;
  }

  
  // Main Launcher
  public static void main( String[] args ) throws IOException {
    // Check for sane file
    if( args.length!= 1 ) {
      System.err.println("Usage: xec file.xec");
      System.exit(1);
    }

    if( args[0].endsWith(".xec") ) {
      System.err.println("Error: "+args[0]+" does not end with .xec");
      System.exit(1);
    }

    // Load whole XTC file into buf
    File f = new File(args[0]);    
    byte[] buf = new byte[(int)f.length()];
    try( FileInputStream fis = new FileInputStream(f) ) { fis.read(buf); } finally {}

    XEC xec = new XParser(buf).parse();
    TODO();
  }

  // Can we handle this version?
  static boolean isFileVersionSupported(int major, int minor) {
    return major==VERSION_MAJOR_CUR && minor==VERSION_MINOR_CUR;
  }

  // -----------------------------------------------------------------------------
  static class XParser {
    final byte[] buf;
    int x;                      // Cursor
    XParser( byte[] buf ) { this.buf = buf; }

    // Top-level XEC file parser
    XEC parse() throws IOException {
      int magic = i64();
      if( magic!=FILE_MAGIC )
        throw new IOException("not an .xtc format file; invalid magic header: " + String.format("0x%x",magic));

      int major = i64(), minor = i64();
      if( !isFileVersionSupported(major,minor) )
        throw new IOException("unsupported version: " + major + "." + minor);

      CPool pool = new CPool(this);

      String modName = ((ModConst)pool.get(index())).name();

      // disassembleChildren(this);
      // return new XEC(pool,mod);
      throw TODO();
    }
    
    int  u8() { return buf[x++]&0xFF; } // Unsigned byte read as an int
    int i64() { return (u8()<<24) | (u8()<<16) | (u8()<<8) | u8(); } // Signed 4-byte integer read
    long pack64() {
      // See org.xvm.util.PackedInteger;
      
      // Tiny: For a value in the range -64..63 (7 bits), the value can be
      // encoded in one byte.  The least significant 7 bits of the value are
      // shifted left by 1 bit, and the 0x1 bit is set to 1.  When reading in a
      // packed integer, if bit 0x1 of the first byte is 1, then it's Tiny.      
      int x = u8();
      if( (x&1)==1 ) return x>>1; // Tiny
      
      // Small: For a value in the range -4096..4095 (13 bits), the value can
      // be encoded in two bytes. The first byte contains the value 0x2 (010)
      // in the least significant 3 bits, and bits 8-12 of the integer in bits
      // 3-7; the second byte contains bits 0-7 of the integer.
      if( (x&7)==0b010 )
        //return (u8()<<(24-3)) | (x>>3);
        throw TODO();           // Untested
      
      if( (x&7)==0b011 )
        throw TODO();           // Medium

      if( x != 0b11111100 ) {   // Large
        int b = (x>>2)+2-1;     // Minus one for the self byte
        if( b==1 ) return u8();
        throw TODO();
      }

      // Huge
      throw TODO();
    }


    int index() throws IOException {
      long n = pack64();
      // this is unsupported in Java; arrays are limited in size by their use
      // of signed 32-bit magnitudes and indexes
      if( n > Integer.MAX_VALUE )  throw new IOException("index (" + n + ") exceeds 32-bit maximum");
      if( n < -1 )                 throw new IOException("negative index (" + n + ") is illegal");
      return (int) n;
    }

    int[] idxAry() throws IOException {
      int len = index();      
      int[] txs = new int[len];
      for( int i=0; i<len; i++ )
        txs[i] = index();
      return txs;
    }

    // Read a UTF8 char
    char utf8Char() {
      int b = u8();
      if( (b&0x80)==0 ) return (char)b; // ASCII single byte
      throw XEC.TODO();
    }
    
    // Read a UTF8 string
    private static final StringBuilder SB = new StringBuilder();
    String utf8() throws IOException {
      SB.setLength(0);
      int len = index();
      int len2 = len - index();
      for( int i=0; i<len2; i++ ) {
        int ch = utf8Char();
        if( ch > 0xFFFF ) throw XEC.TODO();
        SB.append((char)ch);
      }
      return SB.toString();
    }
    
  }

  public static RuntimeException TODO() { return new RuntimeException("TODO"); }

}
