package org.xvm.cc_explore;

import org.xvm.cc_explore.tvar.TVar;
import org.xvm.cc_explore.tvar.TVStruct;
  
/**
   Union or Interface composed interface Part
 */
public class RelPart extends ClassPart {
  final Part _p1, _p2;
  final Op _op;
  public RelPart( Part p1, Part p2, Op op ) {
    super(null,make_name(p1._name,p2._name),Part.Format.INTERFACE);
    _p1 = p1;
    _p2 = p2;
    _op = op;
  }
  private static String make_name( String n1, String n2 ) {
    if( n1.compareTo(n2) > 0 ) { String tmp = n1; n1 = n2; n2 = tmp; }
    return "["+n1+","+n2+"]";
  }

  @Override Part search(String s, Part p) {
    Part c1 = _p1.child(s);
    Part c2 = _p2.child(s);
    return _op.search(c1,c2);
  }

  @Override TVStruct _setype() {
    TVStruct t1 = (TVStruct)_p1.setype();
    TVStruct t2 = (TVStruct)_p2.setype();
    return _op.op(t1,t2);
  }

  
  public static enum Op {
    // For a Union type, the result is one of the Union members - and the
    // allowed set of fields we can use is limited to what is common across
    // all members - i.e., the intersection.
    Union {
      @Override TVStruct op(TVStruct t1, TVStruct t2) {
        // Make a fresh copy of fields found in both.
        TVStruct t3 = (TVStruct)t2.fresh();
        boolean old = t1._open;
        t1._open = t3._open = false; // Both are closed; result has common fields
        t1.fresh_unify(t3,null);
        t1._open = old;
        t3._open = true;        // Still an interface
        return (TVStruct)t3.find();
      }
      @Override Part search(Part c1, Part c2) {
        return c1==c2 ? c1 : null; // Must be in both
      }
    },
    // For an Intersection type, the result is all the Intersection members,
    // all at the same time.  This pretty much is limited to interfaces, or
    // trivial parent/child classes.  The allowed set of fields we can use is
    // everything in both.
    Intersect {
      @Override TVStruct op(TVStruct t1, TVStruct t2) {
        // Make a fresh copy of fields found in both.
        assert t1 != t2;
        TVStruct t3 = (TVStruct)t2.fresh();
        boolean old = t1._open;
        t1._open = t3._open = true; // Both are open; result as all fields
        t1.fresh_unify(t3,null);
        t1._open = old;
        return (TVStruct)t3.find();
      }
      @Override Part search(Part c1, Part c2) {
        return c1==null ? c2 : c1; // Must be in either
      }
    },
    Difference {
      @Override TVStruct op(TVStruct t1, TVStruct t2) {
        // TODO: Not sure, just returning t1
        return t1;
      }
      @Override Part search(Part c1, Part c2) {
        return c1!=null && c2==null ? c1 : null; // Must be in first not 2nd
      }
    };
    abstract TVStruct op(TVStruct t1, TVStruct t2);
    abstract Part search(Part c1, Part c2);
  };
}
