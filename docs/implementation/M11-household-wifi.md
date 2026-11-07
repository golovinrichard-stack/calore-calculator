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

*Status:* decided from Android's documented rules in [decisions/0001-household-hub.md](decisions/0001-household-hub.md): a `connectedDevice` foreground service, a wake lock only while charging, and syncing whenever the app is open as the fallback. The battery numbers still have to be measured on real phones (the record says how). Until then the household shows only in debug builds (`FeatureFlags.household`).

Before building, test on Android 15, 16 and 17 and write `docs/implementation/decisions/0001-household-hub.md`:

- [x] A foreground service that keeps a `ServerSocket` and an NSD registration while hub mode is on. Which service type fits? `dataSync` is limited to 6 hours per 24 hours on Android 15+; check `connectedDevice` and the alternatives, and their permission rules.
- [ ] Does it accept connections with the screen off and in Doze? What does it cost in battery over 24 hours? *(to measure on real phones; table in the decision record)*
- [ ] NSD discovery between two phones on a typical home router. *(to check on real phones)*
- [x] Android 17 blocks local network access by default for apps targeting it (API 37): the app must request `ACCESS_LOCAL_NETWORK` at runtime once `targetSdk` is raised to 37. Check the flow.
- [x] Fallback if a permanent hub is not practical: sync only when the hub app is open, plus a short sync window when the hub phone is charging on home Wi-Fi.

**Done when:** the decision record says which approach to use and why, with measured battery numbers.

### M11.1 Household and members (database version 12)

- [x] Tables `household`, `household_devices`, `requests`, `activity`, `sync_ops`, `sync_field_clock` and `sync_peers` (data-model M11). *As built:* database version 12; also `sync_changes` and `sync_state` (local change capture, M11.3). The same migration gives rows with a natural key the same id on every phone (`StableIds`): basic foods, barcode products, starter recipes with their ingredients and steps, seed shops and products, and week plans (one plan per week for the household); every reference follows. Foods sync too, because shared recipes, the pantry and the list refer to them.
- [x] Members of kind `HOUSEHOLD` represent the other people; their phones are rows in `household_devices`. *As built:* after each sync, every other phone's row becomes (or updates) a `HOUSEHOLD` member with the same id as that person's own profile, with their name and colour, and their targets only if they share them.
- [x] Roles: the **host** (creates the household, runs the hub, adds and removes members), **members**, and **managed profiles** (children without a phone, looked after by a member). *As built:* the host is the hub phone (handing over the hub hands over the host role too); managed profiles stay the member's "people I cook for" on that member's phone, and their boxes show for everyone with their owner's label.

### M11.2 Joining with a QR code

- [x] The host taps **Create household** and gets a QR code with `{v: 1, householdId, name, hubDeviceId, key}`, where `key` is 32 random bytes in Base64. *As built:* `HouseholdInvite` (made with ZXing on the phone); everything shared that was already on the phone goes to the household.
- [x] A member scans it (the M2 scanner in QR mode) and gets **Join "Novák family"?**. The host confirms the new phone. *As built:* until confirmed, the hub takes only the new phone's own row and sends back only phone rows; the host gets a notification. A phone's row can be changed only by that phone or the hub.
- [x] The household key is stored encrypted with a key from the Android Keystore; it is never in the database or in backups. *As built:* `HouseholdKeys`, in the app's no-backup folder together with the device id (so a restored phone gets a new id and scans the code again).
- [x] **Leave household** and **Remove member** (the host creates a new key and shows a new QR code to the remaining members). *As built:* the other phones see "The household code has changed" and scan the new code once; their data stays. The hub can leave only after handing the hub over.

### M11.3 The sync engine

- [x] Every write to a **shared** table goes through one `SyncWriter`, which writes the row and an op (A22) in the same transaction. Writes to private tables never create ops (test). *As built:* SQLite triggers on the shared tables (`data/sync/SyncTriggers`, made whenever the database opens) record each changed column in `sync_changes` in the same transaction as the write, so no screen or repository can forget it; `SyncStore.flush` turns them into ops with the next seq and clock right before a sync. Private tables have no triggers. The triggers record only while the phone is in a household, and not while incoming ops are applied. Phone-only columns (`foods.id`, `foods.lastUsedAt`, `household_devices.lastSeenAt`/`seenVector`) and basic foods are never recorded.
- [x] Applying incoming ops as in A22: hybrid logical clocks, newest change per column wins, the earlier "take" wins for boxes. *As built:* `domain/sync` (`Hlc`, `HlcClock`, `SyncEngine`). The box values follow "newest change per column" too, because a separate "earlier take wins" rule would let devices end up different depending on the order ops arrive; the earlier take wins in what users are told: the later taker gets a `TakeConflict` ("Anna had already taken box C3").
- [x] Compaction of old ops. *As built:* the rule (`SyncEngine.compactable`); `SyncStore.compact` deletes them. `SyncStore.apply` applies a batch in clock order; an op for a row that isn't here yet waits (`sync_ops.applied = 0`) and is tried again after each batch; a removed row stays removed.

**Tests:** all A22 test cases, including the randomised convergence test with 3 devices and 500 ops.
*As built:* `SyncEngineTest` (JVM): all A22 cases; the convergence test runs 5 rounds with ops replayed and shuffled. `SyncTriggersTest` (JVM) checks the triggers against the schema; `SyncStoreTest` (on a device) runs two databases: ops go across once, private data never becomes an op, waiting ops, removed rows, edits on both phones end the same.

### M11.4 Discovery and the connection

- [x] NSD service type `_calore._tcp`, with a hash of the household id in the TXT record (never the name or the key). *As built:* `HouseholdNetwork`; the service name also starts with the first 8 characters of that hash, so phones resolve only their own hub.
- [x] TCP with frames: 4-byte length, 12-byte nonce, then AES-256-GCM ciphertext of a JSON message, with the household id as associated data. A frame that fails to decrypt closes the connection. *As built:* `data/sync/SyncFrames` (the frames; the socket comes with the hub service).
- [x] Messages: `HELLO {deviceId, versionVector}`, `OPS {ops}`, `ACK`. At most 1 MB per frame and 500 ops per batch; 10-second timeouts. *As built:* `SyncMessage`; the timeouts come with the socket.
- [x] When to sync: when the app opens on Wi-Fi, 5 seconds after a local change (combined), and while hub mode is on. *As built:* `HouseholdSync`; also once a minute while the app is open, so phones see each other's changes without restarting. The hub listens while hub mode is on or while Calore is open on it (the fallback from the decision record). `SyncSession`: HELLO, then the phone's ops, then the hub's, 500 per batch with ACK, 10-second timeouts.

### M11.5 Hub mode, and telling users why

- [x] Show this to the host before hub mode starts (English and Czech):

  > **Your phone will be the household hub.**
  > Calore has no paid servers, which keeps it free. So your household's shared list, fridge and boxes are kept on this phone and shared over your home Wi-Fi.
  > - It uses a little battery while hub mode is on.
  > - Others sync when they are on the same Wi-Fi as this phone.
  > - Your own diary, weight and cycle data are never shared.

- [x] While hub mode is on, there is a notification (required for the foreground service) with the number of members and the last sync. *As built:* `HubService` (`connectedDevice`), a quiet channel; a wake lock only while charging; starts again after a restart if it was on.
- [x] A status screen: members, their last sync, the number of changes waiting. *As built:* Me → Household.
- [x] **Hand over the hub** to another member's phone. *As built:* the old hub keeps serving until the new hub has synced once more; the new hub's user is asked to turn hub mode on.

### M11.6 Sharing and ownership

- [x] Boxes and pantry items show a label: **Mine, don't touch** (lock, name and colour), **Shared**, or **Up for grabs**. The owner can change it. *As built:* `Ownership` (domain); labels show only while the phone is in a household. An item without an owner belongs to the household; marking it "Mine" makes the person who marks it its owner. The owner's "people I cook for" count as theirs.
- [x] Food reserved for a planned meal shows "Reserved for Anna's curry on Wednesday". *As built:* pantry food is reserved by hand (Pantry → the item → Reserve for a planned meal) for any planned cook not cooked yet; the name is the item's owner when it has one.
- [x] Taking an item that isn't yours starts a **Can I have it?** request instead. *As built:* for someone else's "Mine, don't touch" box or pantry item; "Shared" and "Up for grabs" can be taken. A box from another phone that I take goes into my own diary.
- [x] **Up for grabs** boxes also appear on everyone's Today screen. *As built:* with "Take it", which logs it in my diary.

### M11.7 Requests

- [x] **Cook for me:** day, meal, optional recipe and portions → accept / decline / suggest another time. Accepting adds it to the cook's plan. *As built:* `RequestRepository`; accepting adds a "cook fresh" meal for the person who asked to the shared week plan.
- [x] **Buy for me:** an item → the shared shopping list, with the requester's name.
- [x] **Can I have it?:** a specific item → yes / no. *As built:* "yes" makes the person who asked the item's owner.
- [x] An inbox, statuses, and notifications when requests and answers arrive (channel *Household*). *As built:* Me → Household → Requests and activity.

### M11.8 Activity feed

- [x] Built from incoming ops: "Anna bought eggs and milk", "Tom cooked chili (6 boxes)", "Tom took 2 eggs", requests and answers. The last 30 days. *As built:* the phone that does something writes a row of the shared `activity` table (item names and a count, never a sentence), which reaches the others as ops; each phone makes the sentence in its language (`ActivityFeed`), and purchases by one person within an hour become one line. Nothing is written outside a household.

### M11.9 Cook once for everyone

- [x] "By person" box filling (M4.5) and the planner (M6) include household members who allow sharing their meal targets (`members.sharesTargets`); asked once, when they join. *As built:* `MemberRepository.cookFor()`; the targets travel in the phone's `household_devices` row only while sharing is on, and can be switched on or off later on the Household screen.

### M11.10 Away from home

- [x] A small indicator: "3 changes waiting for home Wi-Fi". *As built:* on the Household screen and the Today card.
- [x] **Send as a message:** any request or the shopping list can be shared as text through the share sheet (WhatsApp and so on), for things that can't wait. *As built:* the shopping list had it since M5; each request has "Send as a message".

### M11.11 Security and privacy tests

- [x] Private tables never appear in ops. *As built:* `WireOp` refuses any table not in `SyncScope.SHARED`, both ways (`SyncFramesTest`). The same check goes into `SyncWriter` when the tables come.
- [x] A wrong key or a changed frame is rejected.
- [x] A replayed op changes nothing.
- [x] A removed member's phone can't sync after the key changes. *As built:* frames sealed with the old key fail to open with the new one.

*As built, tests:* `SyncSessionTest` (JVM: through the hub, batches of 500, a wrong key, a waiting phone, a removed phone), `HouseholdInviteTest`, `OwnershipTest`, `ActivityFeedTest`, `SyncTriggersTest`; on a device: `SyncStoreTest`, `HouseholdRepositoryTest`, `RequestRepositoryTest` (compiled; they run on a phone or emulator).

## Out of scope

Sync over the internet or through any server, sharing with people outside the home, iPhone.
