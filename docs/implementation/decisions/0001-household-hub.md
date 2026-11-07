# 0001: How a phone serves as the household hub

**Status:** decided for the build; the battery numbers still have to be measured on real phones (see the last section).
**Date:** 7 November 2026
**Milestone:** [M11.0](../M11-household-wifi.md)

## Question

Can one phone in the home keep the household's shared data and serve the other phones over Wi-Fi, without a paid server, reliably and without draining its battery?

## What Android allows (Android 14–17)

- A long-running listener needs a **foreground service** with a visible notification. Since Android 14 every foreground service declares a type.
- `dataSync` is limited to **6 hours per 24 hours** on Android 15 and later, so it can't keep a hub running.
- `connectedDevice` is for "interactions with external devices that need a Bluetooth, NFC, IR, USB or network connection". It has no daily limit. It needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` plus one of the listed permissions; `CHANGE_WIFI_MULTICAST_STATE` (a normal permission, granted at install) fits a hub that announces itself on the local network.
- A foreground service of this type may be started again after a restart (`BOOT_COMPLETED`); Android 15 only blocks `dataSync`, camera, media and phone-call types there.
- Discovery uses `NsdManager` (mDNS); it needs no extra permission and no multicast lock of our own.
- Android 17 asks for **local network access** (`ACCESS_LOCAL_NETWORK`) at runtime for apps that target API 37. Calore targets 36, so this is needed only when the target is raised; the request then goes before "Create household" and "Join".
- With the screen off, the CPU may sleep; an incoming connection usually wakes it (the Wi-Fi chip wakes the phone for packets addressed to it), but some phones are stricter. Holding a wake lock all day would cost too much battery.

## Decision

1. **Hub mode** is a foreground service of type `connectedDevice` that keeps a `ServerSocket` open and the NSD service registered. It shows the required notification (members, last sync).
2. **No permanent wake lock.** While the hub phone is **charging**, the service holds a partial wake lock, so the hub is fully reliable overnight on the charger. On battery it relies on incoming connections waking the phone.
3. **Fallback that always works:** every phone also syncs when Calore is opened on the home Wi-Fi, five seconds after a local change, and when the hub app is opened. Members retry with backoff when the hub doesn't answer. Nothing is lost while the hub sleeps: changes wait on each phone (M11.10 shows "3 changes waiting for home Wi-Fi").
4. Hub mode starts again after a restart only if it was on.

## Still to measure (owner, with real phones)

The build machine has no phones. Before calling M11 done on real devices, measure and fill in:

| Phone and Android version | Hub on battery, 24 h, screen off: battery used | Connections accepted with the screen off (of 20 tries) | Discovery works on the home router |
|---|---|---|---|
| (Android 15) | | | |
| (Android 16) | | | |
| (Android 17) | | | |

How: switch hub mode on, unplug, leave the phone for 24 hours; every hour, open Calore on a second phone on the same Wi-Fi and pull to sync. Note the battery use in Settings → Battery. If the hub uses more than about 3 % a day on battery, or misses many connections, switch to the fallback alone (sync only when the hub's app is open or the hub is charging) by making hub mode "only while charging".
