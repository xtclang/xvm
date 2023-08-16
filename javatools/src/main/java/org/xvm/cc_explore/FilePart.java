package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.ModCon;
import org.xvm.cc_explore.cons.Const;
import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.tvar.TVLeaf;

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
   * By convention, version 0 is the pre-production version: The language and
   * tool-chain are still in development.
   */
  static final int VERSION_MAJOR_CUR = 0;
  final int _major;
  /**
   * The current minor version of the XVM File structure. This is the newest
   * version that can be written by this implementation. (Newer minor versions
   * can be safely read.)
   * By convention, as long as VERSION_MAJOR_CUR == 0, whenever a change is made to Ecstasy that
   * changes the persistent structure of an ".xtc" file in a manner that isn't both forwards and
   * backwards compatible, the minor version will be updated to the 8-digit ISO 8601 date (i.e.
   * the date string with the "-" characters having been removed). The result is that an error
   * will be displayed if there is a version mismatch, which should save some frustration -- since
   * otherwise the resulting error(s) can be very hard to diagnose.
   */
  static final int VERSION_MINOR_CUR = 20230815;
  final int _minor;

  // Main module
  final ModPart _mod;
  
  static final int NFLAGS =
    (Part.Format.FILE.ordinal() << Part.Format.FORMAT_SHIFT) |
    (Const.Access.PUBLIC.ordinal() << ACCESS_SHIFT) |
    ABSTRACT_BIT | 
    STATIC_BIT | 
    SYNTHETIC_BIT;
  

  // Constructor parses byte array, builds FileComponent
  FilePart( byte[] buf, String name ) {
    super(null,NFLAGS,null,name,null,null);

    // Constant pool and buffer parser
    CPool pool = new CPool(buf);

    if( pool._magic!=FILE_MAGIC )
      throw new IllegalArgumentException("not an .xtc format file; invalid magic header: " + String.format("0x%x",pool._magic));
    
    _major = pool._major;
    _minor = pool._minor;
    if( !isFileVersionSupported(_major,_minor) )
      throw new IllegalArgumentException("unsupported version: " + _major + "." + _minor);

    // Build the constant pool
    pool.parse();

    // Easy access to module name; will be non-null (TODO: or crash)
    ModCon mod = (ModCon)pool.xget();

    // Parse any children components
    parseKids(pool);

    _mod = (ModPart)child(mod.name());
  }

  @Override void link_innards( XEC.ModRepo repo ) { }
  @Override TVar _setype( ) { return new TVLeaf(); }

  // Can we handle this version?
  static boolean isFileVersionSupported(int major, int minor) {
    return major==VERSION_MAJOR_CUR && minor==VERSION_MINOR_CUR;
  }
}
