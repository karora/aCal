# Database schemas, one file to a version

`vN.sql` holds the statements aCal ran to make a new database at version N,
one statement to a line. The instrumented tests use them in two ways:

- `SchemaUpgradeTest` makes a database from each file older than the current
  version, puts a server, a calendar, an event and an unsent change in it, and
  opens it with the app. It has to come out with the schema of a new database
  and with that data still in it.
- `SchemaTest` checks that a new database has the schema in the file for the
  current `DB_VERSION`.

So a change to the schema fails a test until it has a new `DB_VERSION`, a file
here for it, and an upgrade step that brings every older file to the same
schema.

## When the schema changes

1. Leave the existing files alone: they are what is on people's phones.
2. Add one to `DB_VERSION` in `AcalDBHelper`, and add the upgrade step.
3. Copy the newest file to `vN.sql` for the new version and make the same
   change in it. Where the upgrade step and the statements for a new database
   differ in what they produce, `SchemaUpgradeTest` says how.
4. If the upgrade deliberately throws something away, say so in
   `SchemaUpgradeTest`, next to the other versions that do.

## Why the files start at version 22

The application id changed from `com.morphoss.acal` to `org.davical.acal` in
April 2026, when the database was at version 22. An install under the new id
starts with no data, so no older database can be upgraded by this app.
