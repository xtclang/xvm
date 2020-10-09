/**
 * A cache of TimeZone information.
 */
static service TimeZoneCache
    {
    private Map<String, TimeZone> tzByName = new HashMap();

    TimeZone find(String desc)
        {
        if (desc == "")
            {
            return TimeZone.NoTZ;
            }

        if (desc == "Z" || desc == "UTC")
            {
            return TimeZone.UTC;
            }

        if (TimeZone zone := tzByName.get(desc))
            {
            return zone;
            }

        Char start = desc[0];
        if (start == '+' || start == '-')
            {
            TimeZone zone = new TimeZone(desc);
            tzByName.put(desc, zone);
            return zone;
            }

        // TODO build zone by name using tz rules db
        throw new IllegalArgument($"unknown TimeZone: {desc}");
        }
    }

