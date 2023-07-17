package org.xvm.cc_explore.tvar;

import org.xvm.cc_explore.util.*;
import org.xvm.cc_explore.XEC;
import java.util.IdentityHashMap;

/** Type variable base class
 *
 * Type variables can unify (ala Tarjan Union-Find), and can have structure
 * such as "{ A -> B }" or "@{ x = A, y = A }".  Type variables includes
 * polymorphic structures and fields (structural typing not duck typing),
 * and may support cyclic types.
 *
 * BNF for the "core AA" pretty-printed types:
 *    T = Vnnn                | // Leaf number nnn
 *        Xnnn>>T             | // Unified; lazily collapsed with 'find()' calls
 *        base                | // any ground term
 *        { T* -> Tret }      | // Lambda
 *        @{ (label = TO;)* }   // ';' is a field-separator not a field-end
 *    TO = T                  | // Normal OR
 *         (T+)               | // Set of overload functions, requires resolution
 */

abstract public class TVar implements Cloneable {
  
  private static int CNT=1;
  public int _uid=CNT++; // Unique dense int, used in many graph walks for a visit bit

  // Disjoint Set Union set-leader.  Null if self is leader.  Not-null if not a
  // leader, but pointing to a chain leading to a leader.  Rolled up to point
  // to the leader in many, many places.  Classic U-F cost model.
  TVar _uf;

  // Outgoing edges for structural recursion.
  TVar[] _args;

  //
  TVar() { this((TVar[])null); }
  TVar( TVar... args ) { _args = args; }

  // True if this a set member not leader.  Asserted for in many places.
  public boolean unified() { return _uf!=null; }

  // Find the leader, without rollup.  Used during printing.
  public TVar debug_find() {
    if( _uf==null ) return this; // Shortcut
    TVar u = _uf._uf;
    if( u==null ) return _uf; // Unrolled once shortcut
    while( u._uf!=null ) u = u._uf;
    return u;
  }

  // Find the leader, with rollup.  Used in many, many places.
  public TVar find() {
    if( _uf    ==null ) return this;// Shortcut, no rollup
    if( _uf._uf==null ) return _uf; // Unrolled once shortcut, no rollup
    return _find();                 // No shortcut
  }
  // Long-hand lookup of leader, with rollups
  private TVar _find() {
    TVar leader = _uf._uf.debug_find();    // Leader
    TVar u = this;
    // Rollup.  Critical to the O(lg lg n) running time of the UF algo.
    while( u!=leader ) { TVar next = u._uf; u._uf=leader; u=next; }
    return leader;
  }

  // Fetch a specific arg index, with rollups
  public TVar arg( int i ) {
    assert !unified();          // No body nor outgoing edges in non-leaders
    TVar arg = _args[i];
    if( arg==null ) return null;
    TVar u = arg.find();
    return u==arg ? u : (_args[i]=u);
  }

  // Fetch a specific arg index, withOUT rollups
  public TVar debug_arg( int i ) { return _args[i].debug_find(); }

  public int len() { return _args.length; }

  public  long dbl_uid(TVar t) { return dbl_uid(t._uid); }
  private long dbl_uid(long uid) { return ((long)_uid<<32)|uid; }
  
  // -----------------
  // U-F union; this becomes that; returns 'that'.
  // No change if only testing, and reports progress.
  final int union(TVar that) {
    if( this==that ) return 0;
    assert !unified() && !that.unified(); // Cannot union twice
    _union_impl(that); // Merge subclass specific bits into that
    // Actually make "this" into a "that"
    _uf = that;                 // U-F union
    return 0;
  }

  // Merge subclass specific bits
  abstract public void _union_impl(TVar that);

  
  // -------------------------------------------------------------
  // Classic Hindley-Milner structural unification.
  // Returns null if ok.
  // Returns error string if a unification error happens.
  static final NonBlockingHashMapLong<TVar> DUPS = new NonBlockingHashMapLong<>();
  static final SB ERRS = new SB();
  public String unify( TVar that ) {
    if( this==that ) return null;
    assert DUPS.isEmpty() && ERRS.len()==0;
    _unify(that);
    String msg = ERRS.len()>0 ? ERRS.toString() : null;
    DUPS.clear();  ERRS.clear();
    return msg;
  }

  // Structural unification, 'this' into 'that'.  No change if just testing and
  // returns a progress flag.  If updating, both 'this' and 'that' are the same
  // afterward.
  final int _unify( TVar that ) {
    assert !unified() && !that.unified();
    if( this==that ) return 0;

    // Any leaf immediately unifies with any non-leaf; triangulate
    if( !(this instanceof TVLeaf) && that instanceof TVLeaf ) return that._unify_impl(this);
    if( !(that instanceof TVLeaf) && this instanceof TVLeaf ) return this._unify_impl(that);

    // If 'this' and 'that' are different classes, unify both into an error
    if( getClass() != that.getClass() ) {
      ERRS.p("Cannot unify "+this+" and "+that).nl();
      return 1;
    }

    // Cycle check
    long luid = dbl_uid(that);  // long-unique-id formed from this and that
    TVar rez = DUPS.get(luid);
    if( rez==that ) return 0; // Been there, done that
    assert rez==null;
    DUPS.put(luid,that);        // Close cycles

    // Same classes.   Swap to keep uid low.
    // Do subclass unification.
    if( _uid > that._uid ) { this._unify_impl(that);  find().union(that.find()); }
    else                   { that._unify_impl(this);  that.find().union(find()); }
    return 0;
  }

  // Must always return null; used in flow-coding in many places
  abstract int _unify_impl(TVar that);

  // -------------------------------------------------------------
  // Make a (lazy) fresh copy of 'this' and unify it with 'that'.  This is
  // the same as calling 'fresh' then 'unify', without the clone of 'this'.
  // Returns an error msg.
  static private final IdentityHashMap<TVar,TVar> VARS = new IdentityHashMap<>();
  static private TVar[] NONGEN;

  public final String fresh_unify( TVar that, TVar[] nongen ) {
    if( this==that ) return null;
    assert VARS.isEmpty() && DUPS.isEmpty() && ERRS.len()==0 && NONGEN==null;
    NONGEN = nongen;
    _fresh_unify(that);
    String msg = ERRS.len()>0 ? ERRS.toString() : null;
    VARS.clear();  DUPS.clear();  ERRS.clear();
    NONGEN = null;
    return msg;
  }

  final int _fresh_unify( TVar that ) {
    assert !unified() && !that.unified();

    // Check for cycles
    TVar prior = VARS.get(this);
    if( prior!=null )                   // Been there, done that
      return prior.find()._unify(that); // Also, 'prior' needs unification with 'that'

    // Must check this after the cyclic check, in case the 'this' is cyclic
    if( this==that ) return 0;

    // Famous 'occurs-check': In the non-generative set, so do a hard unify,
    // not a fresh-unify.
    if( nongen_in() ) return vput(that,_unify(that));

    // LHS leaf, RHS is unchanged but goes in the VARS
    if( this instanceof TVLeaf ) return vput(that,0);
    // RHS is a tvar; union with a deep copy of LHS
    if( that instanceof TVLeaf ) return vput(that,that.union(_fresh()));

    // Two unrelated classes usually make an error
    if( getClass() != that.getClass() ) {
      ERRS.p("Cannot unify "+this+" and "+that).nl();
      return 1;
    }

    // Early set, to stop cycles
    vput(that,0);

    // Do subclass unification.
    return _fresh_unify_impl(that);
  }

  // Generic field by field
  int _fresh_unify_impl( TVar that ) {
    assert !unified() && !that.unified();
    if( _args != null ) {
      for( int i=0; i<_args.length; i++ ) {
        if( _args[i]==null ) continue; // Missing LHS is no impact on RHS
        assert !unified();      // If LHS unifies, VARS is missing the unified key
        TVar lhs = arg(i);
        TVar rhs = i<that._args.length ? that.arg(i) : null;
        if( rhs==null ) _fresh_missing_rhs(that,i);
          else lhs._fresh_unify(rhs);
        that = that.find();
      }
      // Extra args in RHS
      for( int i=_args.length; i<that._args.length; i++ )
        throw XEC.TODO();
    } else assert that._args==null;
    return 0;
  }

  private int vput(TVar that, int x) {
    VARS.put(this,that);
    VARS.put(that,that);
    return x;                   // Flow coding
  }

  // This is fresh, and RHS is missing.  Possibly Lambdas with missing arguments
  int _fresh_missing_rhs(TVar that, int i) {
    //if( !that.unify_miss_fld(key,work) )
    //  return false;
    //add_deps_work(work);
    //return true;
    throw XEC.TODO();
  }

  // -----------------
  // Return a fresh copy of 'this'
  public TVar fresh() {
    assert VARS.isEmpty();
    TVar rez = _fresh();
    VARS.clear();
    return rez;
  }

  TVar _fresh() {
    assert !unified();
    TVar rez = VARS.get(this);
    if( rez!=null ) return rez.find(); // Been there, done that
    // Unlike the original algorithm, to handle cycles here we stop making a
    // copy if it appears at this level in the nongen set.  Otherwise, we'd
    // clone it down to the leaves - and keep all the nongen leaves.
    // Stopping here preserves the cyclic structure instead of unrolling it.
    if( nongen_in() ) {
      vput(this,0);
      return this;
    }

    // Structure is deep-replicated
    TVar t = copy();
    vput(t,0);                  // Stop cyclic structure looping
    if( _args!=null )
      for( int i=0; i<t.len(); i++ )
        if( _args[i]!=null )
          t._args[i] = arg(i)._fresh();
    assert !t.unified();
    return t;
  }

  // -----------------
  static final VBitSet ODUPS = new VBitSet();
  boolean nongen_in() {
    if( NONGEN==null ) return false;
    ODUPS.clear();
    for( int i=0; i<NONGEN.length; i++ ) {
      TVar tv3 = NONGEN[i];
      if( tv3.unified() ) NONGEN[i] = tv3 = tv3.find();
      if( _occurs_in_type(tv3) )
        return true;
    }
    return false;
  }

  // Does 'this' occur anywhere inside the nested 'x' ?
  boolean _occurs_in_type(TVar x) {
    assert !unified() && !x.unified();
    if( x==this ) return true;
    if( ODUPS.tset(x._uid) ) return false; // Been there, done that
    if( x._args!=null )
      for( int i=0; i<x.len(); i++ )
        if( x._args[i]!=null && _occurs_in_type(x.arg(i)) )
          return true;
    return false;
  }


  // -------------------------------------------------------------

  // Do a trial unification between this and that.
  // Report back -1 for hard-no, +1 for hard-yes, and 0 for maybe.
  // No change to either side, this is a trial only.
  // Collect leafs and bases and open structs on the pattern (this).
  private static final NonBlockingHashMapLong<TVar> TDUPS = new NonBlockingHashMapLong<>();
  public boolean trial_unify_ok(TVar that) {
    TDUPS.clear();
    return _trial_unify_ok(that);
  }
  boolean _trial_unify_ok(TVar that) {
    if( this==that ) return true; // hard-yes
    assert !unified() && !that.unified();
    long duid = dbl_uid(that._uid);
    if( TDUPS.putIfAbsent(duid,this)!=null )
      return true;              // Visit only once, and assume will resolve
    if( this instanceof TVLeaf leaf ) return true; // Leaves do not fail now, but might fail later
    if( that instanceof TVLeaf leaf ) return true; // Leaves do not fail now, but might fail later
    // Different classes always fail
    if( getClass() != that.getClass() ) return false;
    // Subclasses check sub-parts
    return _trial_unify_ok_impl(that);
  }

  // Subclasses specify on sub-parts
  boolean _trial_unify_ok_impl( TVar that ) { throw XEC.TODO(); }

  // -----------------
  // Glorious Printing

  // Look for dups, in a tree or even a forest (which Syntax.p() does).  Does
  // not rollup edges, so that debug printing does not have any side effects.
  public VBitSet get_dups(boolean debug) { return _get_dups(new VBitSet(),new VBitSet(),debug); }
  public VBitSet _get_dups(VBitSet visit, VBitSet dups, boolean debug) {
    if( visit.tset(_uid) )
      { dups.set(debug_find()._uid); return dups; }
    // Dup count unified and not the args
    return _uf==null
      ? _get_dups_impl(visit,dups,debug) // Subclass specific dup counting
      : _uf._get_dups (visit,dups,debug);
  }
  public VBitSet _get_dups_impl(VBitSet visit, VBitSet dups, boolean debug) {
    if( _args != null )
      for( TVar tv3 : _args )  // Edge lookup does NOT 'find()'
        if( tv3!=null )
          tv3._get_dups(visit,dups,debug);
    return dups;
  }

  public final String p() { VCNT=0; VNAMES.clear(); return str(new SB(), null, null, false ).toString(); }
  private static int VCNT;
  private static final NonBlockingHashMapLong<String> VNAMES = new NonBlockingHashMapLong<>();

  @Override public final String toString() { return str(new SB(), null, null, true ).toString(); }

  public final SB str(SB sb, VBitSet visit, VBitSet dups, boolean debug) {
    if( visit==null ) {
      assert dups==null;
      _get_dups(visit=new VBitSet(),dups=new VBitSet(),debug);
      visit.clear();
    }
    return _str(sb,visit,dups,debug);
  }

  // Fancy print for Debuggers - includes explicit U-F re-direction.
  // Does NOT roll-up U-F, has no side-effects.
  SB _str(SB sb, VBitSet visit, VBitSet dups, boolean debug) {
    boolean dup = dups.get(_uid);
    if( !debug && unified() ) return find()._str(sb,visit,dups,debug);
    if( unified() || this instanceof TVLeaf ) {
      vname(sb,debug,true);
      return unified() ? _uf._str(sb.p(">>"), visit, dups, debug) : sb;
    }
    // Dup printing for all
    if( dup && debug ) {
      vname(sb,debug,false);            // Leading V123
      if( visit.tset(_uid) ) return sb; // V123 and nothing else
      sb.p(':');                        // V123:followed_by_type_descr
    }
    return _str_impl(sb,visit,dups,debug);
  }

  // Generic structural TVar
  SB _str_impl(SB sb, VBitSet visit, VBitSet dups, boolean debug) {
    sb.p(getClass().getSimpleName()).p("( ");
    if( _args!=null )
      for( TVar tv3 : _args )
        tv3._str(sb,visit,dups,debug).p(" ");
    return sb.unchar().p(")");
  }

  // Pick a nice tvar name.  Generally: "A" or "B" or "V123" for leafs,
  // "X123" for unified but not collapsed tvars.
  private void vname( SB sb, boolean debug, boolean uni_or_leaf) {
    final boolean vuid = debug && uni_or_leaf;
    sb.p(VNAMES.computeIfAbsent((long) _uid,
                                (k -> (vuid ? ((unified() ? "X" : "V") + k) : ((++VCNT) - 1 + 'A' < 'V' ? ("" + (char) ('A' + VCNT - 1)) : ("Z" + VCNT))))));
  }

  // Debugging tool
  TVar f(int uid) { return _find(uid,new VBitSet()); }
  private TVar _find(int uid, VBitSet visit) {
    if( visit.tset(_uid) ) return null;
    if( _uid==uid ) return this;
    if( _uf!=null ) return _uf._find(uid,visit);
    if( _args==null ) return null;
    for( TVar arg : _args )
      if( arg!=null && (arg=arg._find(uid,visit)) != null )
        return arg;
    return null;
  }

  // Shallow clone of fields & args.
  public TVar copy() {
    try {
      TVar tv3 = (TVar)clone();
      tv3._uid = CNT++;
      tv3._args = _args==null ? null : _args.clone();
      return tv3;
    } catch(CloneNotSupportedException cnse) {throw XEC.TODO();}
  }

  public static void reset_to_init0() {
    CNT=0;
    //TVField.reset_to_init0();
    //TVStruct.reset_to_init0();
    //TVExpanding.reset_to_init0();
  }
}
