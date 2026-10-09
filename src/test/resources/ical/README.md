# iCalendar fixtures

`ICalendarFixturesTest` runs every `.ics` file in this directory through the
parser, the serialiser and the recurrence engine. Adding a case means adding
files here; no Java is needed.

## Files

| File | Purpose |
|---|---|
| `NAME.ics` | The calendar. Always checked: it must survive being read and written back, and no line written may be longer than 75 octets. |
| `NAME.range` | Optional. One line, `START END ZONE`: the period to list instances for. `START` and `END` are local times in `ZONE`, which is an Olson name, `UTC` or `floating`. `END` is exclusive. |
| `NAME.instances` | Optional, needs `NAME.range`. The start of each instance of the master component in that period, one per line. For an event with a time zone, the local time is followed by the same moment in UTC. Lines starting with `#` are comments. |
| `known-failures.txt` | Checks that are known to fail, with the issue for each. |

## Adding a fixture

1. Add `NAME.ics`, with CRLF line endings. Remove anything personal first.
2. For a repeating event, add `NAME.range`, then run

       src/test/tools/expected-instances.py --write src/test/resources/ical/NAME.ics

   which works the instances out with python-dateutil and writes
   `NAME.instances`. **Read the result.** It is a second opinion that does not
   come from aCal, which is the point of it, but it is not always right either.
   If you correct it by hand, start the file with a `# ` comment saying why,
   and the tool will not overwrite it again.
3. Run `make test`.

If the new fixture shows up a bug that is not going to be fixed straight away,
file an issue and add a line to `known-failures.txt`. The check is then
reported as skipped while it fails, and as a failure once it starts passing, so
the line has to be removed when the bug is fixed.

## What is not checked

- Instances that have been changed (a second component with a `RECURRENCE-ID`)
  are read and written back, but `.instances` lists only where the rule of the
  master component puts them.
- The `style-*` fixtures are written by hand to look like what Google
  Calendar, Apple Calendar and Thunderbird produce. They are not captured from
  those programs. Real files, with personal details removed, would be better.
