# M11: Household over home Wi-Fi

**Goal:** people in one home share the shopping list, pantry, boxes, recipes and plans, see who owns what, and send requests to each other. There is **no server**: phones sync over the home Wi-Fi, and one phone, the family "boss's", is the **hub**.
**Users get:** a shared fridge and shopping list, "Mine, don't touch" / "Shared" / "Up for grabs" labels, requests (cook for me, buy for me, can I have this?), and a feed of who bought, cooked or took what.
**Depends on:** M5, M6.
**Read first:** [algorithms.md](algorithms.md) A22; [data-model.md](data-model.md) (M11, and which tables are shared or private).
**New dependencies:** none. Discovery uses Android's `NsdManager`, the connection uses `java.net` sockets, encryption uses `javax.crypto` (AES-GCM), and QR codes use ZXing (from M2).

## Why a hub phone and Wi-Fi only

The owner is a student and can't pay for servers for every user. So the household's shared data lives on the phones, and one phone acts as the hub. This must stay **light**: the hub only stores and passes on small change records; it never runs planning for others. Users are told why, in the app (M11.5).

**What this means:** syncing happens when phones are on the same home Wi-Fi. Changes made away from home (for example ticking items in the shop) are saved on the phone and sync when it gets home. For urgent things there is a fallback: share the request or the list as a message (M11.10).

## Tasks

### M11.0 Spike: can a phone be a reliable hub? (decision record)

Before building, test on Android 15, 16 and 17 and write `docs/implementation/decisions/0001-household-hub.md`:

- [ ] A foreground service that keeps a `ServerSocket` and an NSD registration while hub mode is on. Which service type fits? `dataSync` is limited to 6 hours per 24 hours on Android 15+; check `connectedDevice` and the alternatives, and their permission rules.
- [ ] Does it accept connections with the screen off and in Doze? What does it cost in battery over 24 hours?
- [ ] NSD discovery between two phones on a typical home router.
- [ ] Android 17 blocks local network access by default for apps targeting it (API 37): the app must request `ACCESS_LOCAL_NETWORK` at runtime once `targetSdk` is raised to 37. Check the flow.
- [ ] Fallback if a permanent hub is not practical: sync only when the hub app is open, plus a short sync window when the hub phone is charging on home Wi-Fi.

**Done when:** the decision record says which approach to use and why, with measured battery numbers.

### M11.1 Household and members (database version 12)

- [ ] Tables `household`, `household_devices`, `requests`, `activity`, `sync_ops`, `sync_field_clock` and `sync_peers` (data-model M11).
- [ ] Members of kind `HOUSEHOLD` represent the other people; their phones are rows in `household_devices`.
- [ ] Roles: the **host** (creates the household, runs the hub, adds and removes members), **members**, and **managed profiles** (children without a phone, looked after by a member).

### M11.2 Joining with a QR code

- [ ] The host taps **Create household** and gets a QR code with `{v: 1, householdId, name, hubDeviceId, key}`, where `key` is 32 random bytes in Base64.
- [ ] A member scans it (the M2 scanner in QR mode) and gets **Join "Novák family"?**. The host confirms the new phone.
- [ ] The household key is stored encrypted with a key from the Android Keystore; it is never in the database or in backups.
- [ ] **Leave household** and **Remove member** (the host creates a new key and shows a new QR code to the remaining members).

### M11.3 The sync engine

- [ ] Every write to a **shared** table goes through one `SyncWriter`, which writes the row and an op (A22) in the same transaction. Writes to private tables never create ops (test).
- [ ] Applying incoming ops as in A22: hybrid logical clocks, newest change per column wins, the earlier "take" wins for boxes.
- [ ] Compaction of old ops.

**Tests:** all A22 test cases, including the randomised convergence test with 3 devices and 500 ops.

### M11.4 Discovery and the connection

- [ ] NSD service type `_calore._tcp`, with a hash of the household id in the TXT record (never the name or the key).
- [ ] TCP with frames: 4-byte length, 12-byte nonce, then AES-256-GCM ciphertext of a JSON message, with the household id as associated data. A frame that fails to decrypt closes the connection.
- [ ] Messages: `HELLO {deviceId, versionVector}`, `OPS {ops}`, `ACK`. At most 1 MB per frame and 500 ops per batch; 10-second timeouts.
- [ ] When to sync: when the app opens on Wi-Fi, 5 seconds after a local change (combined), and while hub mode is on.

### M11.5 Hub mode, and telling users why

- [ ] Show this to the host before hub mode starts (English and Czech):

  > **Your phone will be the household hub.**
  > Calore has no paid servers, which keeps it free. So your household's shared list, fridge and boxes are kept on this phone and shared over your home Wi-Fi.
  > - It uses a little battery while hub mode is on.
  > - Others sync when they are on the same Wi-Fi as this phone.
  > - Your own diary, weight and cycle data are never shared.

- [ ] While hub mode is on, there is a notification (required for the foreground service) with the number of members and the last sync.
- [ ] A status screen: members, their last sync, the number of changes waiting.
- [ ] **Hand over the hub** to another member's phone.

### M11.6 Sharing and ownership

- [ ] Boxes and pantry items show a label: **Mine, don't touch** (lock, name and colour), **Shared**, or **Up for grabs**. The owner can change it.
- [ ] Food reserved for a planned meal shows "Reserved for Anna's curry on Wednesday".
- [ ] Taking an item that isn't yours starts a **Can I have it?** request instead.
- [ ] **Up for grabs** boxes also appear on everyone's Today screen.

### M11.7 Requests

- [ ] **Cook for me:** day, meal, optional recipe and portions → accept / decline / suggest another time. Accepting adds it to the cook's plan.
- [ ] **Buy for me:** an item → the shared shopping list, with the requester's name.
- [ ] **Can I have it?:** a specific item → yes / no.
- [ ] An inbox, statuses, and notifications when requests and answers arrive (channel *Household*).

### M11.8 Activity feed

- [ ] Built from incoming ops: "Anna bought eggs and milk", "Tom cooked chili (6 boxes)", "Tom took 2 eggs", requests and answers. The last 30 days.

### M11.9 Cook once for everyone

- [ ] "By person" box filling (M4.5) and the planner (M6) include household members who allow sharing their meal targets (`members.sharesTargets`); asked once, when they join.

### M11.10 Away from home

- [ ] A small indicator: "3 changes waiting for home Wi-Fi".
- [ ] **Send as a message:** any request or the shopping list can be shared as text through the share sheet (WhatsApp and so on), for things that can't wait.

### M11.11 Security and privacy tests

- [ ] Private tables never appear in ops.
- [ ] A wrong key or a changed frame is rejected.
- [ ] A replayed op changes nothing.
- [ ] A removed member's phone can't sync after the key changes.

## Out of scope

Sync over the internet or through any server, sharing with people outside the home, iPhone.
