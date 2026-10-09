#!/usr/bin/env python3
"""
Work out which instances a recurring iCalendar fixture should have, using
python-dateutil rather than aCal, so that the two can be compared.

For each NAME.ics given that has a NAME.range beside it, print (or with
--write, save as NAME.instances) the start of every instance of the master
component that falls inside the range.

NAME.range holds one line: START END ZONE, where START and END are local
times in ZONE (an Olson name, "UTC" or "floating"), and END is exclusive.

Each line of NAME.instances is the start as it appears on the wall clock
in the event's own time zone, followed for events that have a time zone by
the same moment in UTC.

dateutil is a second opinion, not the truth: read what this writes before
relying on it.  Overridden instances (RECURRENCE-ID) are not applied, and a
file that has a hand-written "# " comment at the top is never overwritten.
"""
import sys
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from dateutil import rrule

UTC = timezone.utc


def content_lines(text):
    lines = []
    for raw in text.replace("\r\n", "\n").split("\n"):
        if raw[:1] in (" ", "\t") and lines:
            lines[-1] += raw[1:]
        elif raw:
            lines.append(raw)
    return lines


def split_content_line(line):
    """Splits NAME;PARAM=value;PARAM="quoted; value":VALUE into its three parts."""
    parts, start, quoted = [], 0, False
    for pos, char in enumerate(line):
        if char == '"':
            quoted = not quoted
        elif char in ";:" and not quoted:
            parts.append(line[start:pos])
            start = pos + 1
            if char == ":":
                break
    name, *params = parts
    return name, dict(p.split("=", 1) for p in params), line[start:]


def master_component(lines):
    """The properties of the first VEVENT/VTODO/VJOURNAL without a RECURRENCE-ID."""
    current, depth = None, 0
    for line in lines:
        if line.startswith("BEGIN:"):
            if current is None and line[6:] in ("VEVENT", "VTODO", "VJOURNAL"):
                current, depth = [], 0
            elif current is not None:
                depth += 1
        elif line.startswith("END:"):
            if current is not None and depth == 0:
                if not any(name == "RECURRENCE-ID" for name, _, _ in current):
                    return current
                current = None
            elif current is not None:
                depth -= 1
        elif current is not None and depth == 0:
            name, params, value = split_content_line(line)
            current.append((name.upper(), params, value))
    raise SystemExit("no master component found")


def parse_time(value, params):
    """Returns (datetime, kind) where kind is 'date', 'floating', 'utc' or 'zoned'."""
    if params.get("VALUE") == "DATE" or len(value) == 8:
        return datetime.strptime(value, "%Y%m%d"), "date"
    if value.endswith("Z"):
        return datetime.strptime(value, "%Y%m%dT%H%M%SZ").replace(tzinfo=UTC), "utc"
    when = datetime.strptime(value, "%Y%m%dT%H%M%S")
    if "TZID" in params:
        return when.replace(tzinfo=ZoneInfo(params["TZID"])), "zoned"
    return when, "floating"


def expected(ics_path, range_path):
    props = master_component(content_lines(ics_path.read_text(encoding="utf-8")))
    first = lambda *names: next(((p, v) for n, p, v in props if n in names), None)

    params, value = first("DTSTART") or first("DUE")
    start, kind = parse_time(value, params)

    def same_kind(when, when_kind):
        if kind in ("date", "floating"):
            return when.replace(tzinfo=None)
        return when if when.tzinfo else when.replace(tzinfo=start.tzinfo)

    instances = rrule.rruleset()
    rule = first("RRULE")
    if rule:
        instances.rrule(rrule.rrulestr(rule[1], dtstart=start))
    else:
        instances.rdate(start)
    for name, params, value in props:
        if name in ("RDATE", "EXDATE"):
            for item in value.split(","):
                when = same_kind(*parse_time(item, params))
                (instances.rdate if name == "RDATE" else instances.exdate)(when)

    range_start, range_end, zone = range_path.read_text().split()
    def bound(text):
        when = datetime.strptime(text.rstrip("Z"), "%Y%m%dT%H%M%S")
        if kind in ("date", "floating"):
            return when
        return when.replace(tzinfo=UTC if zone == "UTC" else ZoneInfo(zone))

    lines = []
    for when in instances.between(bound(range_start), bound(range_end), inc=True):
        if when == bound(range_end):
            continue
        if kind == "date":
            lines.append(when.strftime("%Y%m%d"))
        elif kind == "floating":
            lines.append(when.strftime("%Y%m%dT%H%M%S"))
        elif kind == "utc":
            lines.append(when.strftime("%Y%m%dT%H%M%SZ"))
        else:
            lines.append(when.strftime("%Y%m%dT%H%M%S") + " "
                         + when.astimezone(UTC).strftime("%Y%m%dT%H%M%SZ"))
    return lines


def main():
    args = sys.argv[1:]
    write = "--write" in args
    for name in (a for a in args if a != "--write"):
        ics = Path(name)
        range_file = ics.with_suffix(".range")
        if not range_file.exists():
            continue
        text = "".join(line + "\n" for line in expected(ics, range_file))
        target = ics.with_suffix(".instances")
        if not write:
            print("== " + ics.name)
            print(text, end="")
        elif target.exists() and target.read_text().startswith("# "):
            print("kept hand-written " + target.name)
        else:
            target.write_text(text)


if __name__ == "__main__":
    main()
