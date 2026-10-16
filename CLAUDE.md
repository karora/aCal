# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

aCal is an Android calendar application that syncs with CalDAV/CardDAV servers. Licensed under GNU GPL, it supports events, tasks, and notes with full iCalendar (RFC 5545) and WebDAV (RFC 4918) protocol implementation.

## Build Commands

Builds run in Docker (`acal-build:latest`, from the `Dockerfile`), never with a bare `./gradlew` on the host:

```bash
# Build debug APK
./build-in-container.sh assembleDebug        # or: make debug

# Build release APK (copied to apk/)
./build-in-container.sh assembleRelease      # or: make release

# Build release bundle for the Play Store
./build-in-container.sh bundleRelease        # or: make bundle

# Run the JVM unit tests
./build-in-container.sh testDebugUnitTest    # or: make test

# Run the instrumented tests on the in-container emulator (add --rerun to force a rerun)
./build-in-container.sh emulatorDebugAndroidTest   # or: make device-test

# Clean
./build-in-container.sh clean                # or: make clean
```

## Build Configuration

- **Gradle**: 9.8.1 wrapper, Android Gradle Plugin 9.4.1
- **Android SDK**: compileSdk 37, minSdk 24, targetSdk 36
- **Java**: source/target 17, built with JDK 25 in the container
- **AndroidX**: Enabled
- **Builds run in Docker**: see Build Commands; after changing the `Dockerfile`, rebuild the image with `docker build -t acal-build:latest .`. CI must use the same JDK as the `Dockerfile`
- **Apache HTTP Legacy**: Enabled via `useLibrary 'org.apache.http.legacy'`

Standard single-module Gradle layout (the project root is the module):
- Source: `src/main/java/`
- AIDL: `src/main/aidl/`
- Resources: `src/main/res/`
- Manifest: `src/main/AndroidManifest.xml`

## Architecture

### Entry Points
Three launcher activities share a common backend:
- `aCal.java` - Events calendar
- `aCalTasks.java` - Tasks
- `aCalNotes.java` - Notes/Journals

### Core Packages

**`service/`** - Background synchronization engine
- `aCalService` - Main Service coordinating sync
- `WorkerClass` - Job scheduler managing sync jobs
- `SyncCollectionContents` - Pull changes from server
- `SyncChangesToServer` - Push local changes
- `connector/` - HTTP communication layer

**`storage/`** - Where a collection's resources are kept, between the sync jobs and the database
- `StorageBackend`, `StorageBackends` - Interface, and the lookup of the backend for a server or collection (by `dav_server.backend_type`)
- `CalDavBackend` - Collections on a CalDAV server
- `LocalBackend`, `LocalCalendars` - Calendars kept only on the phone, under the "This phone" account
- `IcsFile` - Export of a calendar to an `.ics` file, and import from one

**`security/`** - Secrets kept on the device
- `DatabaseKeyManager` - The SQLCipher database key, wrapped by a key in the Android Keystore
- `CredentialManager` - Encryption of saved server passwords

**`davacal/`** - iCalendar/CalDAV protocol
- `VCalendar`, `VComponent` - iCalendar parsing and generation
- `VEvent`, `VTodo`, `VJournal` - Component types
- `AcalProperty` - Property handling with parameters

**`database/`** - SQLite persistence
- `AcalDBHelper` - Schema definition
- `DatabaseTableManager` - Query builder pattern
- `resourcesmanager/` - Event/task/note resource management
- `cachemanager/` - Performance caching layer
- `alarmmanager/` - Event notification scheduling

**`providers/`** - Content providers for data access
- `DavCollections`, `Servers`, `Timezones`, `PathSets`, `AlarmDataProvider`, `CacheDataProvider`

**`activity/`** - UI layer
- View activities: `MonthView`, `YearView`, `EventView` (the week view is `weekview/WeekViewActivity`)
- Edit activities: `EventEdit`, `TodoEdit`, `JournalEdit`
- Configuration: `serverconfig/ServerConfigList`, `CollectionConfigList`

**`xml/`** - WebDAV XML parsing (SAX/DOM via factory pattern)

### Key Files
- `Constants.java` - Debug flags, feature toggles, XML namespaces
- `AcalApplication.java` - Application singleton, preferences
- `ServiceManager.java` - Service lifecycle management

## Debug Configuration

In `Constants.java`, everything else is ANDed with `DEBUG_MODE`, so a release build has it all off:
```java
public static boolean DEBUG_MODE = false;
public static boolean LOG_VERBOSE = false && DEBUG_MODE;
public static boolean LOG_DEBUG = false && DEBUG_MODE;
public static boolean DEBUG_SETTINGS = false && DEBUG_MODE;   // Shows Debug Settings in Settings
// Component-specific: debugCalendar, debugSyncChangesToServer, etc.
```

Feature flags:
```java
DISABLE_FEATURE_WEBDAV_SYNC = false && DEBUG_MODE;
DISABLE_FEATURE_CURRENT_PRINCIPAL = false && DEBUG_MODE;
DISABLE_FEATURE_TZSERVER_SUPPORT = true && DEBUG_MODE;
```

## Development Notes

- JVM unit tests (JUnit 4) live in `src/test/java`, mirroring the package of the class under test. Run them with `./build-in-container.sh testDebugUnitTest` (or `make test`); CI runs them on every pull request. Reports land in `build/reports/tests/testDebugUnitTest/`
- Plain JUnit tests cannot call the Android framework (it throws "not mocked"), except `Log`, which `src/test/java/android/util/Log.java` stands in for
- Tests that need a `Context`, `Parcel`, `ContentValues`, shared preferences or notifications use Robolectric: add `@RunWith(RobolectricTestRunner.class)`. Settings are in `src/test/resources/robolectric.properties` (Android 16, and a bare `Application` because `AcalApplication` opens the database)
- Nothing that opens the database can run on the JVM: SQLCipher's native library only exists for devices. Those tests are instrumented tests (#44)
- Instrumented tests live in `src/androidTest/java` and run on an emulator inside the build container: `./build-in-container.sh emulatorDebugAndroidTest` (or `make device-test`). Each test runs in its own process and starts with `TestDatabase.startWithNothing()`. They replace the app's database, so never run them on a device with real aCal data
- A schema change needs a new `DB_VERSION`, an upgrade step and a `src/androidTest/assets/schema/vN.sql` (see the README there)
- Every bug fix lands with a test that fails without the fix
- iCalendar fixtures live in `src/test/resources/ical/` (see the README there): add `NAME.ics` (+ `NAME.range` and `NAME.instances` for recurrence) and `ICalendarFixturesTest` picks it up. Bugs a fixture exposes go in `known-failures.txt` with an issue number
- Epic #25 tracks the remaining test tiers
- AIDL interfaces in `src/main/aidl/` define IPC contracts (`ServiceRequest.aidl`, etc.)
- Localized to 14 languages (resource directories like `src/main/res/values-de/`)

## Releasing

- Bump `versionCode` and `versionName` in `build.gradle` (e.g. 71 and "1.71")
- Add a block to `src/main/res/raw/upgrade_notes.txt`: a `vNN=x.yy` line (versionCode=versionName), then one line per user-visible change. It is shown to users after the upgrade

## Git Rules
- Never run `git push` - I will push manually

## Issue Tracking

This project uses **GitHub Issues** (via the `gh` CLI) for issue tracking.

**Quick reference:**
- `gh issue list` - List open issues
- `gh issue view <number>` - View issue details (including nested sub-issues)
- `gh issue create --title "..." --body "..." --label "..."` - Create an issue
- `gh issue close <number>` - Complete work

**Conventions:**
- Priority is tracked with `priority/P0`..`priority/P4` labels (P0 = critical, P4 = backlog).
- Type is tracked with `type/epic`, `type/task`, `type/bug`, `type/feature` labels.
- Epics group their children using GitHub **sub-issues** (not task-list checkboxes).
- Cross-issue dependencies are noted in the issue body/comments as `Depends on #N`.

## Session Completion

When ending a work session:

1. **File issues for remaining work** - `gh issue create` for anything needing follow-up.
2. **Run quality gates** (if code changed) - Tests, linters, builds.
3. **Update issue status** - `gh issue close` finished work; comment on in-progress items.
4. **Commit** - Stage and commit completed changes.
5. **Hand off** - Provide context for the next session.

Note: Do NOT run `git push` — the maintainer pushes manually (see Git Rules).
