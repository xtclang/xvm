package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xClock
        extends TypeCompositionTemplate
    {
    public static xClock INSTANCE;

    public xClock(TypeSet types)
        {
        super(types, "x:Clock", "x:Object", Shape.Interface);

        INSTANCE = this;
        }

    // subclassing
    protected xClock(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        //    @ro DateTime epoch;
        //    @ro TimeZone timezone;
        //    @ro Interval precision;
        //    @ro Boolean monotonic;
        //    @ro Boolean realtime;
        //
        //    @ro Time time;
        //
        //    Timer createTimer();
        //
        //    Cancellable scheduleAlarm(Alarm alarm, DateTime timeToWakeUp);

        // TODO: change when the DateTime is implemented
        ensureMethodTemplate("scheduleAlarm", new String[]{"x:Function", "x:Int64"}, new String[]{"x:Function"});
        }
    }
