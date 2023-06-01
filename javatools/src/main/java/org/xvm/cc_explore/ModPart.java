package org.xvm.cc_explore;

import org.xvm.cc_explore.cons.*;

import java.io.IOException;

/**
   Module component
 */
class ModPart extends ClassPart {
  public final ModuleType _t;
  public final LitCon _dir;
  public final LitCon _time;
  
  ModPart( Part par, int nFlags, ModCon con, CondCon cond, FilePart X ) throws IOException {
    super(par,nFlags,con,cond,X);

    // when the main module is created in the FileStructure, the name has not
    // yet been configured, so if this is being created and the file already
    // has a main module name, then this module is being created to act as a
    // fingerprint
    String sPrimary = par.getFileComp()._modName;
    if( sPrimary != null && !sPrimary.equals(con.name()) )
      throw XEC.TODO();

    _t = ModuleType.valueOf(X.u8());
    // modulestructure disassamble
    if( isFingerprint() ) {
      //throw XEC.TODO();
    } else {
      throw XEC.TODO();
    }
    _dir  = (LitCon)X._pool.get(X.u31());
    _time = (LitCon)X._pool.get(X.u31());

    throw XEC.TODO();           // time to parse
  }

  /**
   * Check if this is a fingerprint module, which is a secondary (not main) module in a file
   * structure that represents the set of external dependencies on a particular imported module
   * from the main module and any embedded modules.
   *
   * @return true iff this module represents the "fingerprint" of an external module dependency
   */
  public boolean isFingerprint() {
    return switch( _t ) {
    case Optional, Desired, Required -> true;
    case Primary, Embedded           -> false;
    };
  }


  // ----- ModuleType enumeration ----------------------------------------------------------------
  /**
   * A module serves one of three primary purposes:
   * <ul>
   * <li>The primary module is the module for which the FileStructure exists;</li>
   * <li>A fingerprint module represents an imported module;</li>
   * <li>An embedded module is an entire module that is embedded within the FileStructure in order
   *     to fully satisfy the dependencies of an import.</li>
   * </ul>
   * <p/>
   * A fingerprint module has three levels that indicate how desired or required it is:
   * <ul>
   * <li>Optional indicates that the dependency is supported, but leaves the decision regarding
   *     whether or not to import the module to the linker;</li>
   * <li>Desired also indicates that the dependency is supported, but even though the dependency
   *     is not required, the linker should make a best effort to obtain and link in the
   *     module;</li>
   * <li>Required indicates that the primary module can not be loaded unless the fingerprint
   *     module is obtained and linked in by the linker.</li>
   * </ul>
   */
  enum ModuleType {
    Primary, Optional, Desired, Required, Embedded;

    /** Look up a Format enum by its ordinal. */
    public static ModuleType valueOf(int i) { return MODULE_TYPES[i]; }
    private static final ModuleType[] MODULE_TYPES = ModuleType.values();
  }

}
