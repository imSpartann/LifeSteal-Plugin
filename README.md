# LifeSteal Plugin

> **Forked and heavily extended from:** [TheSuperFluffyCat/LifeSteal-Plugin (version 2)](https://github.com/TheSuperFluffyCat/LifeSteal-Plugin)

A production-grade, highly configurable Minecraft plugin introducing a **LifeSteal mechanic** with enterprise-level security features. Perfect for PvP-focused servers, minigame networks, and competitive communities.

---

## Features

### Core LifeSteal Mechanics
- **Heart Stealing**: Players lose 1 heart (2 health) upon death, and the killer gains 1 heart (if below max health)
- **Heart Items**: Withdraw hearts into tradable items and redeem them later
- **Configurable Health**: Adjust max/min health values to fit your server economy
- **World Control**: Enable/disable LifeSteal mechanics per world

### Secure UUID Heart System
- Every heart item contains a **unique UUID** stored in NBT data
- Server-side tracking via `hearts.yml` prevents cloning and duplication
- Each heart UUID is invalidated upon redemption or withdrawal
- Heart items are **unstackable** (amount = 1) to prevent bulk exploits

### Anti-Duplication Protections
- Heart items are automatically **removed from death drops** to prevent duplication
- UUID validation ensures each heart can only be redeemed **once**
- Server-side heart tracking prevents offline cloning attacks
- Invalid heart items are detected and rejected at interaction time

### Audit Logging
- All heart economy actions are logged to `plugins/LifeSteal/logs/`
- Daily log files with format: `[HH:mm:ss] EVENT player=NAME world=WORLD uuid=UUID`
- Tracked events: `REDEEM`, `WITHDRAW`, `INVALIDATE`, `INVALID_REDEEM_ATTEMPT`, `MIN_HEALTH_ACTION`
- Configurable enable/disable via `audit-logging.enabled`

### Anti-Alt Protections
- **Same-victim cooldown**: Prevents repeated steals from the same player
- **Minimum playtime requirement**: New accounts must reach playtime threshold before stealing
- **Configurable thresholds**: Adjust cooldown duration and playtime requirements

### Withdraw Restrictions
- **Minimum playtime**: Players must have sufficient playtime before withdrawing hearts
- **Minimum max health**: Players must have accumulated enough hearts before withdrawal
- Prevents alt accounts from farming and transferring hearts to main accounts

### Minimum-Health Actions
- Configurable action when a player at minimum health dies again
- Supported actions: `BAN`, `SPECTATOR`, `RESET`, `NONE`
- Automatic audit logging of all minimum-health actions

### Unified `/lifesteal` Command Structure
- Single entry point with subcommands
- Permission-based access control
- Tab completion support

### Admin Tools
- Player inspection command with detailed statistics
- Heart reset functionality
- Configuration reload without server restart

### Debug Logging
- Toggleable debug output for troubleshooting
- Disabled by default in production
- Covers redeem, UUID validation, anti-alt, and reload operations

---

## Installation

1. Download the `.jar` file
2. Place it in your server's `/plugins` folder
3. Start your server to generate configuration files
4. Edit `plugins/LifeSteal/config.yml` to fit your server's needs
5. Restart your server (or use `/lifesteal reload`)

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/lifesteal check <player>` | `lifesteal.check` | Inspect a player's heart status |
| `/lifesteal reload` | `lifesteal.reload` | Reload plugin configuration |
| `/withdrawheart <amount>` | `lifesteal.withdraw` | Withdraw hearts as tradable items |
| `/resethearts <player>` | `lifesteal.reset` | Reset a player's health to default |
| `/giveheart <player> <amount>` | `lifesteal.giveheart` | Give secure heart items to a player |

### `/lifesteal check <player>` Output

```
=== LifeSteal Check: skylakes ===
Max Health: 24
Visible Hearts: 12
Current Health: 18
Playtime: 32h 14m
Withdraw Eligible: YES
Cooldown Entries: 2
```

---

## Configuration

### Full `config.yml` Example

```yaml
# LifeSteal Plugin Configuration

# Worlds where LifeSteal mechanics are enabled
# If empty or missing, LifeSteal works in all worlds
enabled-worlds:
  - resource_world
 # - new_world

# Heart item appearance configuration
heart-item:
  material: NETHER_STAR
  name: "&c&lHeart"
  lore:
    - "&7Server-issued heart"
    - "&7Right-click to redeem"
    - "&8Unstackable"

# Maximum and minimum health values (in whole hearts)
max-health: 60
min-health: 1

# Anti-alt protection settings
anti-alt:
  enabled: true
  same-victim-cooldown-minutes: 1
  minimum-playtime-minutes: 1440

# Withdraw heart restrictions (anti-alt protection)
# Player must meet these requirements before using /withdrawheart
withdraw:
  minimum-playtime-minutes: 1440
  minimum-max-health: 22

# Minimum health death action
# Triggered when a player at minimum allowed health dies again
# Available actions:
# BAN       - Permanently bans the player
# SPECTATOR - Sets the player to spectator mode
# RESET     - Resets player back to default health
# NONE      - Do nothing special
minimum-health-action:
  enabled: true
  action: BAN

# Prevent heart items from dropping on player death
# When enabled, heart items are removed from death drops to prevent duplication
prevent-heart-drops: true

# Debug logging control
# When enabled, detailed debug logs are written to console
debug:
  enabled: false

# Audit logging for heart economy actions
# Logs are stored in plugins/LifeSteal/logs/ directory
audit-logging:
  enabled: true

# Configurable messages (supports & color codes and MiniMessage)
messages:
  disabled-world: "&cLifeSteal is disabled in this world!"
  creative-disabled: "&cYou cannot use this command in creative mode!"
  no_hearts: "<red>You don't have enough hearts!"
  success_withdraw: "<green>Successfully withdrew a heart!"
  success_redeem: "<green>You redeemed %amount% hearts!"
  reset_success: "<green>%player%'s hearts have been reset!"
  cooldown-active: "&cYou already stole a heart from this player recently!"
  minimum-playtime: "&cThis player does not meet the minimum playtime requirement!"
  reload-success: "&aLifeSteal configuration reloaded!"
  no-permission: "&cYou do not have permission!"
  withdraw-playtime-required: "&cYou need more playtime before withdrawing hearts!"
  withdraw-min-health-required: "&cYou need at least {health} max health before withdrawing hearts!"
  min-health-action-ban: "&cYou ran out of hearts!"
  min-health-action-spectator: "&cYou ran out of hearts! You have been spectating."
  min-health-action-reset: "&cYou ran out of hearts! Your health has been reset."
```

### Configuration Reference

| Path | Type | Default | Description |
|------|------|---------|-------------|
| `enabled-worlds` | List | `[resource_world]` | Worlds where LifeSteal is active |
| `heart-item.material` | String | `NETHER_STAR` | Material for heart items |
| `heart-item.name` | String | `&c&lHeart` | Display name for heart items |
| `heart-item.lore` | List | [...] | Lore lines for heart items |
| `max-health` | Int | `60` | Maximum allowed health (in hearts) |
| `min-health` | Int | `1` | Minimum allowed health (in hearts) |
| `anti-alt.enabled` | Boolean | `true` | Enable anti-alt protections |
| `anti-alt.same-victim-cooldown-minutes` | Int | `1` | Cooldown between stealing same victim |
| `anti-alt.minimum-playtime-minutes` | Int | `1440` | Minimum playtime to steal |
| `withdraw.minimum-playtime-minutes` | Int | `1440` | Minimum playtime to withdraw |
| `withdraw.minimum-max-health` | Int | `22` | Minimum max health to withdraw |
| `minimum-health-action.enabled` | Boolean | `true` | Enable minimum-health death actions |
| `minimum-health-action.action` | String | `BAN` | Action when at min health and dying |
| `prevent-heart-drops` | Boolean | `true` | Remove heart items from death drops |
| `debug.enabled` | Boolean | `false` | Enable debug logging |
| `audit-logging.enabled` | Boolean | `true` | Enable audit logging |

---

## Compatibility

| Aspect | Detail |
|--------|--------|
| **Minecraft Version** | 1.21.11 (Paper API 1.21) |
| **Java Version** | 21 |
| **API Version** | 1.21 |
| **Dependencies** | None (standalone plugin) |
| **NBT Support** | Paper built-in NBT |

---

## Storage Structure

```
plugins/LifeSteal/
├── config.yml          # Main configuration
├── cooldowns.yml       # Persistent steal cooldowns
├── hearts.yml          # Server-side heart UUID tracking
└── logs/
    └── 2025-01-15.log  # Daily audit log
```

### File Descriptions

| File | Purpose |
|------|---------|
| `config.yml` | All plugin configuration settings |
| `cooldowns.yml` | Stores killer-victim cooldown pairs |
| `hearts.yml` | Tracks valid heart UUIDs for redemption |
| `logs/*.log` | Daily audit trail of all heart economy actions |

---

## Design Goals

1. **Security First**: UUID-based heart tracking prevents duplication and cloning attacks
2. **Performance Optimized**: Minimal overhead with efficient data structures
3. **Fully Configurable**: Every aspect adjustable via `config.yml`
4. **Audit Trail**: Complete logging for incident response and economy monitoring
5. **Anti-Exploit**: Multi-layered protection against alt accounts and heart farming
6. **Production Ready**: Clean architecture with proper error handling
7. **Minimal Dependencies**: No external libraries required

---

## Permissions

| Permission | Description |
|------------|-------------|
| `lifesteal.check` | Use `/lifesteal check` to inspect players |
| `lifesteal.reload` | Use `/lifesteal reload` to reload config |
| `lifesteal.withdraw` | Use `/withdrawheart` to withdraw hearts |
| `lifesteal.reset` | Use `/resethearts` to reset player health |
| `lifesteal.giveheart` | Use `/giveheart` to give heart items |

---

## License

This project is forked and heavily extended from [TheSuperFluffyCat/LifeSteal-Plugin](https://github.com/TheSuperFluffyCat/LifeSteal-Plugin).

---

*Perfect for PvP-focused gameplay or unique server concepts – scales seamlessly for small and large servers alike!*
