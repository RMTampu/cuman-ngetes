# Space Lab

Experimental Android virtual-space/multi-instance project for Android 11 arm64-v8a.

## Current scope

- isolated virtual users and application data from the pinned virtual-space engine;
- the same app can be installed for multiple virtual users;
- Virtual Android ID is stable per virtual user + package;
- Virtual Android ID can be rotated manually without uninstalling the guest APK or clearing guest app data;
- guest process is stopped before identity rotation takes effect;
- Android backup is disabled.

Not included: app-data backup/snapshot/recovery, hardware-ID modification, Play Integrity bypass, server-binding bypass, anti-cheat bypass, or anti-abuse bypass.

The repository stays small by pinning the upstream engine in \`UPSTREAM.lock\` and applying the local overlay during GitHub Actions.
