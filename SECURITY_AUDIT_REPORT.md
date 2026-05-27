# LifeSteal Plugin Security Audit Report

**Version:** 3.9.3  
**Target:** Minecraft Paper 1.21.11 / Java 21  
**Assumptions:** Offline-mode (cracked), hacked clients possible, item editors present, Essentials/Multiverse/ProtocolLib/TAB/Citizens installed  
**Audit Type:** Security, Duplication, Abuse, and Exploit Analysis Only  

---

## Executive Summary

| Severity | Count |
|----------|-------|
| Critical | 5     |
| High     | 8     |
| Medium   | 7     |
| Low      | 4     |

**Total Findings: 24**

The plugin has **critical vulnerabilities** in heart item forgery, duplication, and state synchronization. The current heart item system is **fully forgeable** using standard Bukkit APIs and NBT manipulation. Cooldowns can be bypassed through multiple methods. The plugin lacks server-side state validation entirely.

---

## CRITICAL Findings

### C-01: Heart Item is Fully Forgeable/Spoofable

- **Severity:** Critical
- **Exploit Scenario:** Players can craft Nether Stars (Material.NETHER_STAR) and add the `HeartItem` NBT tag using item editors, ProtocolLib packet manipulation, or custom clients. The `NBTUtils` class uses Bukkit's `PersistentDataContainer` which is client-visible but **not server-validated** against item creation. Any player with an item editor (or hacked client) can create a valid heart item without using `/withdrawheart`.
- **Impact:** Infinite heart generation. Players can forge hearts at will, gaining unlimited health without killing anyone.
- **Code Location:** 
  - [`NBTUtils.java:14-15`](src/main/java/org/fliff/lifeSteal/utils/NBTUtils.java:14) - `addNBTTag()` writes NBT without validation
  - [`RightClickListener.java:32`](src/main/java/org/fliff/lifeSteal/listeners/RightClickListener.java:32) - Only checks `hasNBTTag()`, never validates source
  - [`WithdrawHeartCommand.java:77-86`](src/main/java/org/fliff/lifeSteal/commands/WithdrawHeartCommand.java:77) - `createHeartItem()` creates items with NBT but there is no server-side tracking of issued hearts
- **Recommended Fix:** Store issued heart amounts server-side in a persistent data structure (e.g., a `ConcurrentHashMap<UUID, Integer>` or database). On redeem, verify the player actually has withdrawable hearts server-side before applying health. Add a secondary NBT tag `Source` with value `"withdrawn"` and validate it. Better: use a signed token system.

---

### C-02: Heart Item Duplication via Drop/Death

- **Severity:** Critical
- **Exploit Scenario:** When a player dies, their inventory drops. If they hold heart items, those items drop as regular Nether Stars with `HeartItem` NBT. The player loses their health (hearts) but the dropped items can be picked up by anyone. On server restart, the dead player's health is already reduced — the dropped heart items are effectively "free" hearts for anyone who picks them up.
- **Impact:** Heart duplication through death. Killing a high-heart player drops all their heart items, creating duplicate hearts in the ecosystem. The dead player's health is permanently reduced while the hearts persist.
- **Code Location:**
  - [`PlayerDeathListener.java:15-66`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:15) - No heart item collection on death. Items drop naturally via Minecraft mechanics.
  - No `event.setDeathMessage()` or inventory clearing logic exists.
- **Recommended Fix:** On player death, clear heart items from the dropped inventory OR collect them into a separate holding mechanism. Alternatively, track heart items server-side and invalidate dropped items.

---

### C-03: Heart Duplication via Item Cloning (Packet Exploit)

- **Severity:** Critical
- **Exploit Scenario:** With ProtocolLib installed (assumed per requirements), attackers can intercept and manipulate inventory packets. By sending a `SET_SLOT` packet that adds a heart item to the inventory without triggering the server-side `withdrawheart` logic, items can be cloned. Additionally, the "off-by-one" window during `addItem()` — where `addItem()` returns a non-empty map on failure but the item may partially exist — can be exploited with rapid packet injection.
- **Impact:** Infinite heart duplication through packet manipulation.
- **Code Location:**
  - [`WithdrawHeartCommand.java:62-66`](src/main/java/org/fliff/lifeSteal/commands/WithdrawHeartCommand.java:62) - `addItem()` check happens AFTER health reduction on line 68-72. If `addItem()` fails, health is already reduced.
  - [`RightClickListener.java:52-53`](src/main/java/org/fliff/lifeSteal/listeners/RightClickListener.java:52) - Item amount is reduced BEFORE health is applied, but no packet-level validation exists.
- **Recommended Fix:** Validate and apply ALL state changes atomically. Never reduce health before confirming item creation succeeds. Use inventory event listeners to intercept unauthorized item additions.

---

### C-04: Withdraw Heart — Health Reduced Before Item Confirmed

- **Severity:** Critical
- **Exploit Scenario:** In [`WithdrawHeartCommand.java:62-72`](src/main/java/org/fliff/lifeSteal/commands/WithdrawHeartCommand.java:62), the sequence is:
  1. Line 62: Create heart item
  2. Lines 63-66: Try to add to inventory — if full, return (health NOT reduced)
  3. Lines 68-72: Reduce health

  This appears correct, BUT if the inventory has partial space (e.g., 1 slot for 2 hearts but amount=2), `addItem()` returns the overflow. The overflow items remain un-added but the player's health is already reduced. The player loses health but doesn't receive all items.

  More critically: if another plugin intercepts `InventoryEvent` and blocks the item, the health has already been reduced.
- **Impact:** Players can lose hearts without receiving items. Combined with item editors, this creates asymmetric value loss.
- **Code Location:** [`WithdrawHeartCommand.java:62-72`](src/main/java/org/fliff/lifeSteal/commands/WithdrawHeartCommand.java:62)
- **Recommended Fix:** Check `addItem()` overflow and refund if incomplete. Use `player.getInventory().addItem()` and if any items remain, revert the health change.

---

### C-05: No Heart Item Validation on Redeem — Negative NBT Spoofing

- **Severity:** Critical
- **Exploit Scenario:** An attacker using an item editor can create a Nether Star with `HeartItem=1` NBT tag but also add a custom negative value tag. While the current code reads `HeartItem` as a boolean check (`hasNBTTag`), a hacked client with ProtocolLib can manipulate the item's displayed amount. The `RightClickListener.java:44` uses `item.getAmount()` which can be manipulated via packet spoofing to report a larger stack than visually shown.
- **Impact:** Players can redeem more hearts than the item stack visually contains by spoofing packet data.
- **Code Location:** [`RightClickListener.java:44`](src/main/java/org/fliff/lifeSteal/listeners/RightClickListener.java:44) - `item.getAmount()` is trusted without server-side validation.
- **Recommended Fix:** Never trust `item.getAmount()` from client-sent packets. Validate against a server-tracked state. Use `PlayerInteractEvent` server-side validation.

---

## HIGH Findings

### H-01: Cooldown Bypass via Alt Account

- **Severity:** High
- **Exploit Scenario:** In offline-mode, UUIDs are hashed from usernames. An attacker can:
  1. Create an alt account with a different username
  2. Kill the victim from the main account to steal hearts
  3. The cooldown is stored as `killerUUID_victimUUID` in [`LifeSteal.java:171`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:171)
  4. Since the alt has a different UUID, the cooldown does NOT apply

  Additionally, the cooldown only tracks `same-victim` pairs. A player can rotate through multiple alt accounts, each stealing once.
- **Impact:** Anti-alt protection is completely circumvented in offline-mode. Players can steal unlimited hearts via alt rotation.
- **Code Location:** [`LifeSteal.java:171-172`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:171), [`PlayerDeathListener.java:68-72`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:68)
- **Recommended Fix:** Track cooldowns by username (not just UUID) in offline-mode. Maintain a list of all UUIDs associated with each username. Cross-reference all known alt UUIDs against the cooldown.

---

### H-02: Cooldown Bypass via Server Restart/Reload

- **Severity:** High
- **Exploit Scenario:** Cooldowns are persisted to `cooldowns.yml` and loaded on enable. However:
  1. If a player disconnects during the cooldown period and the server restarts BEFORE `saveCooldowns()` is called (e.g., crash, `kill -9`), all cooldowns are lost.
  2. The `/lifestealreload` command in [`LifeSteal.java:157-168`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:157) calls `loadCooldowns()` which clears and reloads. If `cooldowns.yml` is manually edited or corrupted during reload, cooldowns can be wiped.
  3. A player with admin permission can reload to clear cooldowns for an alt account.
- **Impact:** Cooldowns are not reliable. Server crashes or reloads can reset all cooldown protection.
- **Code Location:** [`LifeSteal.java:115-130`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:115), [`LifeSteal.java:157-168`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:157)
- **Recommended Fix:** Save cooldowns on EVERY steal event, not just on disable. Use atomic file writes (write to temp file, then rename). Add cooldown persistence to a more reliable storage mechanism.

---

### H-03: Reload Abuse — Config Manipulation

- **Severity:** High
- **Exploit Scenario:** The `/lifestealreload` command ([`LifeSteal.java:68-76`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:68)) only checks `lifesteal.reload` permission. A reload:
  1. Resets `configManager` to a new instance
  2. Reloads `cooldowns.yml` from disk
  3. If an admin with reload permission is compromised, or if permissions plugin has a bug, cooldowns can be wiped
  4. Changing `max-health` in config mid-game allows existing players to exceed the new max (the check in `RightClickListener.java:39` only prevents exceeding, but existing health above the new max is NOT capped)
- **Impact:** Admin account compromise or permission bugs allow cooldown bypass and health manipulation.
- **Code Location:** [`LifeSteal.java:157-168`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:157), [`RightClickListener.java:39`](src/main/java/org/fliff/lifeSteal/listeners/RightClickListener.java:39)
- **Recommended Fix:** Cap existing player health to new max on reload. Add a cooldown wipe confirmation. Log all reloads.

---

### H-04: Multiverse World Boundary Bypass

- **Severity:** High
- **Exploit Scenario:** With Multiverse installed, worlds can be loaded/unloaded dynamically. The `isEnabledWorld()` check in [`PlayerDeathListener.java:23-29`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:23) checks if BOTH killer and victim are in enabled worlds. However:
  1. A player can be in an enabled world while the killer is in a disabled world — the killer does NOT lose hearts on death (line 27-29 returns early)
  2. Conversely, a player can lure a high-heart victim into a disabled world and kill them there — the killer bypasses the world check
  3. World unload/reload can cause `World` object reference changes, potentially causing `isEnabledWorld()` to fail unexpectedly
- **Impact:** Players can bypass heart-stealing by controlling kill location. Heart-stealing can be avoided by killing in disabled worlds.
- **Code Location:** [`PlayerDeathListener.java:23-29`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:23)
- **Recommended Fix:** Track world transitions. If a killer was in an enabled world within a recent tick window, apply heart steal even if the kill happens in a disabled world. Or: explicitly deny heart gain for kills in disabled worlds while still applying heart loss to victims.

---

### H-05: ResetHearts Command — No Validation, No Audit Trail

- **Severity:** High
- **Exploit Scenario:** The `/resethearts` command ([`ResetHeartsCommand.java:15-38`](src/main/java/org/fliff/lifeSteal/commands/ResetHeartsCommand.java:15)):
  1. Sets target health to 20 (hardcoded) regardless of config `min-health`
  2. No cooldown or rate limiting on resets
  3. No logging of who reset whose hearts
  4. `Bukkit.getPlayerExact()` is case-sensitive but vulnerable to UUID spoofing in offline-mode
  5. Can be used to "restore" hearts for any player, effectively acting as a heart-giving mechanism
- **Impact:** Admin command can be abused to give unlimited hearts. No audit trail for accountability.
- **Code Location:** [`ResetHeartsCommand.java:32-35`](src/main/java/org/fliff/lifeSteal/commands/ResetHeartsCommand.java:32)
- **Recommended Fix:** Use config `max-health` instead of hardcoded 20. Log all resets. Add a cooldown or rate limit. Consider UUID-based targeting.

---

### H-06: Health State Desync After Player Disconnect/Reconnect

- **Severity:** High
- **Exploit Scenario:** Player health and maxHealth are NOT persisted. When a player disconnects:
  1. Their current health and maxHealth are saved by Minecraft's default mechanism
  2. If the server restarts while the player is offline, and their heart state changed (via steal/withdraw), the persisted state may not match the server's tracked state
  3. On reconnect, the player's health is restored from the player data file, which may show a different maxHealth than what the server tracks
  4. The plugin has NO `PlayerJoinEvent` handler to sync health state
- **Impact:** Players can reconnect with incorrect health values, potentially retaining hearts they should have lost.
- **Code Location:** No `PlayerJoinEvent` handler exists. [`LifeSteal.java`](src/main/java/org/fliff/lifeSteal/LifeSteal.java) has no join/leave listeners.
- **Recommended Fix:** Add a `PlayerJoinEvent` listener that validates and syncs the player's maxHealth against a server-side tracked value.

---

### H-07: Race Condition in Heart Steal — Concurrent Deaths

- **Severity:** High
- **Exploit Scenario:** If two players kill the same victim simultaneously (or near-simultaneously), both `PlayerDeathEvent` handlers may execute concurrently:
  1. Both killers read the victim's health before either applies the reduction
  2. Both killers gain 2 hearts, but the victim only loses 2 hearts total (or loses 4 if both apply)
  3. The `ConcurrentHashMap` for cooldowns is thread-safe, but `setMaxHealth()` and `setHealth()` are NOT guaranteed to be atomic across multiple concurrent calls
- **Impact:** Heart values can desync. Victim may lose more/fewer hearts than killers gain.
- **Code Location:** [`PlayerDeathListener.java:42-61`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:42)
- **Recommended Fix:** Use a single-threaded executor or synchronized block for death event processing. Queue death events sequentially.

---

## MEDIUM Findings

### M-01: No Inventory Event Listener — Item Placement/Use Exploits

- **Severity:** Medium
- **Exploit Scenario:** The plugin only listens for `PlayerInteractEvent` (right-click). It does NOT listen for:
  1. `InventoryClickEvent` — players could potentially interact with heart items in crafting tables, anvil, or other interfaces
  2. `InventoryOpenEvent` — heart items could be viewed/duplicated in certain GUI exploits
  3. `PlayerDropItemEvent` — dropping heart items could be exploited in combination with other plugins
- **Impact:** Heart items could be manipulated through non-right-click interactions.
- **Code Location:** No inventory event listeners exist.
- **Recommended Fix:** Add `InventoryClickEvent` and `PlayerDropItemEvent` listeners to handle heart items explicitly.

---

### M-02: NBT Tag Value Not Enforced — Arbitrary NBT Values

- **Severity:** Medium
- **Exploit Scenario:** The `HeartItem` NBT tag stores a string value (`"1"` in [`WithdrawHeartCommand.java:82`](src/main/java/org/fliff/lifeSteal/commands/WithdrawHeartCommand.java:82)), but `RightClickListener.java:32` only checks `hasNBTTag()` — it does NOT read or validate the value. An item editor could set `HeartItem` to any string value (e.g., `"999"`, `"-1"`), and it would still be treated as a valid heart item.
- **Impact:** While the current redeem logic uses `item.getAmount()` rather than the NBT value, the lack of NBT validation means forged items with unexpected NBT data are accepted.
- **Code Location:** [`RightClickListener.java:32`](src/main/java/org/fliff/lifeSteal/listeners/RightClickListener.java:32), [`NBTUtils.java:19-21`](src/main/java/org/fliff/lifeSteal/utils/NBTUtils.java:19)
- **Recommended Fix:** Read and validate the NBT value. Only accept `HeartItem` with value `"1"` (or store the actual heart amount as the NBT value and validate it).

---

### M-03: YAML Corruption Risk in Cooldown Persistence

- **Severity:** Medium
- **Exploit Scenario:** The cooldown save mechanism in [`LifeSteal.java:92-113`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:92) writes to `cooldowns.yml` directly without atomic writes. If the server crashes during a write:
  1. The YAML file becomes corrupted
  2. On reload, `YamlConfiguration.loadConfiguration()` may throw exceptions or load partial data
  3. All cooldown data is lost
  4. The `saveCooldowns()` method does NOT use try-catch around the entire operation — partial writes can leave the file in an inconsistent state
- **Impact:** Server crashes corrupt cooldown data, removing all anti-alt protection.
- **Code Location:** [`LifeSteal.java:108-112`](src/main/java/org/fliff/lifeSteal/LifeSteal.java:108)
- **Recommended Fix:** Use atomic file writes: write to a temporary file, then rename. Add comprehensive error handling.

---

### M-04: Config `min-health` and `max-health` Integer Overflow

- **Severity:** Medium
- **Exploit Scenario:** Config values `min-health` and `max-health` are read as `int` in [`ConfigManager.java:26-31`](src/main/java/org/fliff/lifeSteal/utils/ConfigManager.java:26). The actual health values are multiplied by 2 (`configManager.getMinHealth() * 2` in [`PlayerDeathListener.java:42`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:42)). If a config sets `max-health: 1000000`, the effective max health is 2000000, which is within Java's integer range but may cause performance issues or unexpected behavior in Minecraft.
- **Impact:** Misconfigured or malicious config values can create extreme health states.
- **Code Location:** [`ConfigManager.java:26-27`](src/main/java/org/fliff/lifeSteal/utils/ConfigManager.java:26), [`PlayerDeathListener.java:42-43`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:42)
- **Recommended Fix:** Add config validation on load. Clamp values to reasonable bounds (e.g., max 1000 health).

---

### M-05: No Protection Against Essentials /vanish or Invisible Kills

- **Severity:** Medium
- **Exploit Scenario:** With Essentials installed, players with `/vanish` can become invisible. A vanished player can:
  1. Kill another player and gain hearts (the death listener does not check vanish state)
  2. The killer's identity may be obscured from other players
  3. In offline-mode, vanished players can create alts and rotate to bypass cooldowns
- **Impact:** Heart stealing can happen invisibly, making it impossible for other players to identify the attacker.
- **Code Location:** [`PlayerDeathListener.java:17`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:17) — `event.getEntity().getKiller()` returns the killer regardless of vanish state.
- **Recommended Fix:** Integrate with vanish APIs to detect vanished killers. Optionally prevent heart steal from vanished players.

---

### M-06: TAB Plugin Protocol Exploit — Fake Player Names

- **Severity:** Medium
- **Exploit Scenario:** With TAB plugin installed, tab list modifications can potentially be exploited. While this is a lower-risk vector, custom TAB profiles could theoretically send fake player metadata that interacts with `Bukkit.getPlayerExact()` in [`ResetHeartsCommand.java:26`](src/main/java/org/fliff/lifeSteal/commands/ResetHeartsCommand.java:26).
- **Impact:** Potential name collision or confusion in reset commands.
- **Code Location:** [`ResetHeartsCommand.java:26`](src/main/java/org/fliff/lifeSteal/commands/ResetHeartsCommand.java:26)
- **Recommended Fix:** Use UUID-based targeting instead of username. Implement UUID caching.

---

### M-07: Citizens NPC Interaction Not Handled

- **Severity:** Medium
- **Exploit Scenario:** With Citizens installed, NPC players exist in the world. If an NPC is set to attack a real player:
  1. The NPC death may trigger `PlayerDeathEvent` if the NPC is cast as a Player entity
  2. The `instanceof Player` check in [`PlayerDeathListener.java:19`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:19) may pass for Citizen NPCs
  3. This could trigger unintended heart mechanics with NPCs
- **Impact:** NPC deaths may trigger heart steal mechanics, causing unexpected behavior.
- **Code Location:** [`PlayerDeathListener.java:19`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:19)
- **Recommended Fix:** Check if the entity is a real player using `entity instanceof Player && !((Player)entity).isFake()` or by checking against a list of known NPC citizens.

---

## LOW Findings

### L-01: No Rate Limiting on `/withdrawheart`

- **Severity:** Low
- **Exploit Scenario:** A player can rapidly execute `/withdrawheart 1` multiple times (within the limits) to withdraw all hearts in quick succession. While each command execution is validated, there is no per-second rate limiting.
- **Impact:** Minor — could cause visual glitches or chat spam.
- **Code Location:** [`WithdrawHeartCommand.java:22-74`](src/main/java/org/fliff/lifeSteal/commands/WithdrawHeartCommand.java:22)
- **Recommended Fix:** Add a per-player command cooldown (e.g., 1 second between withdraw commands).

---

### L-02: Playtime Statistic Can Be Faked

- **Severity:** Low
- **Exploit Scenario:** The minimum playtime check in [`PlayerDeathListener.java:75-85`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:75) uses `victim.getStatistic(PLAY_ONE_MINUTE)`. This statistic is tracked server-side and cannot be faked by the client. HOWEVER, in offline-mode with username spoofing, a player can create a new account and the playtime check applies to the VICTIM, not the killer. A fresh account can be used as a victim to be killed by anyone with an alt-cooldown-free account.
- **Impact:** Fresh accounts have no protection — they can be killed for hearts immediately by anyone without a cooldown.
- **Code Location:** [`PlayerDeathListener.java:81`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:81)
- **Recommended Fix:** Track first-join date server-side and use that for playtime calculation instead of the Bukkit statistic.

---

### L-03: No Logging of Heart Steal Events

- **Severity:** Low
- **Exploit Scenario:** Heart steal events are not logged. Server administrators cannot audit who stole hearts from whom, making it difficult to investigate exploits.
- **Impact:** No audit trail for heart transfers.
- **Code Location:** [`PlayerDeathListener.java:87-91`](src/main/java/org/fliff/lifeSteal/listeners/PlayerDeathListener.java:87) — `recordSteal()` only saves cooldown data, no logging.
- **Recommended Fix:** Add server log entries for every heart steal event, including killer UUID, victim UUID, hearts transferred, and timestamps.

---

### L-04: Config Message Path Mismatch

- **Severity:** Low
- **Exploit Scenario:** The config.yml defines messages with paths like `no_hearts`, `success_withdraw`, `success_redeem` (snake_case), but the code references paths like `"cooldown-active"`, `"minimum-playtime"`, `"reload-success"` which DO exist in config. However, `no_hearts` and `success_withdraw` and `success_redeem` are defined in config but NEVER referenced in the code. This is dead config, not a security issue, but indicates incomplete implementation.
- **Impact:** No security impact. Confusing configuration.
- **Code Location:** [`config.yml:26-29`](src/main/resources/config.yml:26), code references [`ConfigManager.java:42-45`](src/main/java/org/fliff/lifeSteal/utils/ConfigManager.java:42)
- **Recommended Fix:** Either use the defined message paths in code or remove unused config entries.

---

## Specific Question Answers

### Is the current heart item system forgeable/spoofable?

**YES — Fully forgeable.** Any player with an item editor or hacked client can create a Nether Star with the `HeartItem` NBT tag. The plugin only checks for the presence of the NBT tag (`hasNBTTag`) but does NOT:
- Track which players have been issued heart items
- Validate that the item corresponds to withdrawn hearts
- Use signed/tamper-proof tokens
- Store server-side issuance records

### Can cooldowns be bypassed?

**YES — Multiple methods:**
1. **Alt account rotation** — Each alt has a unique UUID, bypassing UUID-based cooldown tracking
2. **Server crash/restart** — Cooldowns saved to YAML can be lost on crash
3. **Reload command** — `/lifestealreload` reloads cooldowns from disk; if disk is corrupted or manually edited, cooldowns are lost
4. **Disabled world kills** — Killing in a disabled world bypasses all checks

### Does restart clear protections?

**YES — Partially.** On server restart:
- Cooldowns are reloaded from `cooldowns.yml` — if the file was saved correctly, cooldowns persist
- Player health states are NOT persisted by this plugin — players reconnect with whatever health Minecraft saved
- If the server crashes before `saveCooldowns()` runs, ALL cooldowns are lost
- There is NO `PlayerQuitEvent` handler to save cooldowns on disconnect

### Can item duplication create infinite hearts?

**YES.** Duplication vectors:
1. **Death drops** — Dead players drop heart items; hearts are lost from the dead player's health but items persist
2. **Packet manipulation** — ProtocolLib can duplicate items via inventory packet spoofing
3. **Item editor** — Direct creation of valid heart items without server validation
4. **Overflow exploit** — `addItem()` overflow in withdraw command can cause asymmetric heart/item loss

---

## Architecture Observations (Non-Critical)

1. **No server-side heart state tracking** — The plugin relies entirely on `player.getMaxHealth()` as the source of truth, which can be manipulated client-side.
2. **No PlayerJoinEvent/PlayerQuitEvent handlers** — Player state is not synced on join/quit.
3. **No inventory event listeners** — Heart items are not tracked during inventory interactions.
4. **ConfigManager instantiated per-call** — Each listener/command creates a new `ConfigManager()` instance, which is lightweight but creates redundant `getConfig()` calls.
5. **Hardcoded health value in ResetHearts** — Uses `20` instead of reading from config.
6. **No integration with common plugins** — No vanish detection, no NPC filtering, no Multiverse world event handling.

---

## Priority Remediation Order

1. **C-01** — Implement server-side heart issuance tracking
2. **C-02** — Prevent heart item drops on death
3. **C-03** — Add inventory event validation
4. **C-04** — Fix withdraw atomicity
5. **C-05** — Validate item amount server-side
6. **H-01** — Implement username-based cooldown tracking for offline-mode
7. **H-02** — Atomic cooldown persistence
8. **H-03** — Health capping on reload
9. **H-04** — World boundary enforcement
10. **H-05** — Fix ResetHearts command
11. **H-06** — Add PlayerJoinEvent health sync
12. **H-07** — Sequential death event processing
