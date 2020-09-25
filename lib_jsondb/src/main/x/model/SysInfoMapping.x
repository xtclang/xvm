import Catalog.Status;

import json.ElementInput;
import json.ElementOutput;
import json.FieldInput;
import json.FieldOutput;
import json.IllegalJSON;
import json.Mapping;

/**
 * JSON mapping for the database system status record.
 *
 * TODO get rid of this and use the reflection impl
 */
const SysInfoMapping
        implements Mapping<SysInfo>
    {
    @Override
    String typeName.get()
        {
        return "SysInfo";
        }

    @Override
    SysInfo read(ElementInput in)
        {
        try (val fields = in.openObject())
            {
            Status   status    = Status.byName[fields.readString("status")] ?: assert;
            DateTime timestamp = new DateTime(fields.readString("timestamp"));
            Version  version   = new Version(fields.readString("version"));
            return new SysInfo(status, timestamp, version);
            }
        catch (Exception e)
            {
            // TODO what should we do with the exception?
            @Inject Console console;
            console.println($"exception reading \"sys.json\" file: {e}");

            @Inject Clock clock;
            return new SysInfo(LockedOut, clock.now, v:1.0);
            }
        }

    @Override
    void write(ElementOutput out, SysInfo value)
        {
        using (val fields = out.openObject())
            {
            fields.add("status"   , value.status   .toString())
                  .add("timestamp", value.timestamp.toString())
                  .add("version"  , value.version  .toString());
            }
        }
    }
