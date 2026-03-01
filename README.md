# ProjectKorraItems

A Spigot/Bukkit plugin (version 2.0, targeting Minecraft 1.18, API version 1.18) that lets server administrators define fully custom items through YAML configuration files. Items can boost or nerf [ProjectKorra](https://github.com/ProjectKorra/ProjectKorra) bending abilities, carry lore variables, have crafting recipes, appear in loot tables, and more.

---

## Table of Contents

1. [Building & Dependencies](#building--dependencies)
2. [High-Level Architecture](#high-level-architecture)
3. [Entry Point — `ProjectKorraItems`](#entry-point--projectkorraitems)
4. [Core Item Model](#core-item-model)
   - [`CustomItem`](#customitem-apicomprojectkorraitems-apicustomitemjava)
   - [`PKItem`](#pkitem-comprojectkorraitems-pkitemjava)
   - [`ItemProperties`](#itemproperties-interface)
   - [`ItemSet`](#itemset)
5. [Item Registry — `CustomItemRegistry`](#item-registry--customitemregistry)
6. [Configuration Loading — `ConfigManager`](#configuration-loading--configmanager)
   - [Item YAML Fields Reference](#item-yaml-fields-reference)
7. [Attribute System](#attribute-system)
   - [`AttributeBuilder` — Target & Type Discovery](#attributebuilder--target--type-discovery)
   - [`AttributeTarget` and its sub-classes](#attributetarget-and-its-sub-classes)
   - [`AttributeType` and `AttributeEvent`](#attributetype-and-attributeevent)
   - [`AttributeModification` — Parsing & Application](#attributemodification--parsing--application)
   - [`Requirements`](#requirements)
8. [Event & Inventory Cache System](#event--inventory-cache-system)
   - [`CustomEventCache`](#customeventcache-complex)
   - [`EventCache` (API wrapper)](#eventcache-api-wrapper)
   - [`ItemEvent` annotation & `ItemListener`](#itemevent-annotation--itemlistener)
   - [`ActiveConditions` & `Position`](#activeconditions--position)
   - [`EventContext`](#eventcontext)
9. [Main Event Listener — `PKIListener`](#main-event-listener--pkilistener)
10. [Loot System](#loot-system)
    - [`LootManager`](#lootmanager)
    - [`Pool` and `Item`](#pool-and-item)
    - [`CustomLootRegistry`](#customlootregistry)
11. [Recipe System](#recipe-system)
    - [`RecipeParser` and sub-classes](#recipeparser-and-sub-classes)
    - [Recipe YAML format](#recipe-yaml-format)
12. [Command System](#command-system)
    - [Available Commands](#available-commands)
13. [GUI — `GiveMenu`](#gui--givemenu)
14. [Properties & Persistent Data](#properties--persistent-data)
15. [Utility Classes](#utility-classes)
16. [Resource Files](#resource-files)
17. [Known Issues / TODO](#known-issues--todo)
18. [Permissions](#permissions)

---

## Building & Dependencies

**Build tool:** Maven  
**Java source/target:** Java 16  
**Final artifact name:** `ProjectKorraItems-2.0-PK1.12.0.jar`

The plugin uses the **maven-shade-plugin** to bundle dependencies into a single fat JAR.

### Dependencies

| Dependency | Version | Scope |
|---|---|---|
| `spigot-api` | 1.18-R0.1-SNAPSHOT | provided |
| `projectkorra` | 1.12.0 | provided |
| `commons-lang3` (Apache) | 3.17.0 | compile (shaded) |
| `annotations` (JetBrains) | 26.0.2 | compile |
| HoloItemsAPI | (local Maven repo) | compile (shaded) |

> **HoloItemsAPI** is a separate library by StrangeOne101. You must clone and `mvn install` it before building this plugin. It provides the inventory-tracking and NMS abstraction layer that this plugin relies on for `CustomItem`, `CustomItemRegistry`, `EventCache`, `HoloItemsAPI`, `Keys`, and NMS helpers.

---

## High-Level Architecture

```
ProjectKorraItems
├── core/
│   ├── ProjectKorraItems.java      ← plugin entry point (enable/disable/reload)
│   ├── PKItem.java                 ← main item class (extends CustomItem)
│   ├── ItemProperties.java         ← interface exposing PKI-specific attributes
│   ├── ItemSet.java                ← item set grouping (for set bonuses)
│   ├── CustomEventCache.java       ← per-player inventory position cache
│   └── PKIListener.java            ← Bukkit event listener (ability hooks)
│
├── configuration/
│   ├── ConfigManager.java          ← loads items & language from YAML files
│   └── Config.java                 ← thin YamlConfiguration wrapper
│
├── attribute/
│   ├── AttributeBuilder.java       ← discovers targets & types at startup
│   ├── AttributeTarget.java        ← what abilities an attribute affects
│   ├── AttributeType.java          ← what field of an ability is modified
│   ├── AttributeEvent.java         ← when the modification fires (enum)
│   ├── AttributeModification.java  ← a single parsed attribute rule
│   ├── AttributePriority.java      ← enum: LOWEST < LOW < NORMAL < HIGH
│   └── Requirements.java           ← permission/world/element restrictions
│
├── command/
│   ├── BaseCommand.java            ← registers "/bending items" root
│   ├── PKICommand.java             ← abstract sub-command base
│   ├── GiveCommand.java            ← /bending items give
│   ├── ListCommand.java            ← /bending items list
│   ├── RecipeCommand.java          ← /bending items recipe
│   ├── ReloadCommand.java          ← /bending items reload
│   └── TestCommand.java            ← /bending items test (debug)
│
├── loot/
│   ├── LootManager.java            ← reads loot/ YAML, registers table extensions
│   ├── Pool.java                   ← weighted random roll pool
│   ├── Item.java                   ← single loot entry with chance & amount
│   ├── Conditions.java             ← (stub) loot conditions
│   ├── LootProvider.java           ← interface: getItems(), getWeight()
│   └── NumberProvider.java         ← min/max integer range
│
├── menu/
│   ├── MenuBase.java               ← generic chest-GUI base
│   ├── MenuItem.java               ← a single clickable GUI slot
│   ├── MenuListener.java           ← Bukkit inventory-click handler for GUIs
│   └── GiveMenu.java               ← paginated give-item chest GUI
│
├── recipe/
│   ├── RecipeParser.java           ← abstract base + static registry
│   ├── Shaped.java                 ← shaped crafting table recipe
│   ├── Shapeless.java              ← shapeless crafting table recipe
│   ├── Furnace.java                ← furnace smelting recipe
│   ├── BlastFurnace.java           ← blast furnace recipe
│   ├── Smoker.java                 ← smoker recipe
│   ├── Campfire.java               ← campfire recipe
│   ├── Stonecutter.java            ← stonecutter recipe
│   ├── PKIIngredient.java          ← "PKI:ItemName" ingredient provider
│   └── IngredientProvider.java     ← interface for custom ingredient sources
│
├── event/
│   ├── PKIEvent.java               ← abstract base for PKI-specific Bukkit events
│   ├── PKItemEvent.java            ← event fired for item interactions
│   ├── PKItemDamageEvent.java      ← cancellable event when an item loses durability
│   └── PKItemLoadEvent.java        ← cancellable event fired as each item loads
│
├── items/
│   └── Consumable.java             ← (partial) consumable item helper
│
├── utils/
│   ├── ItemUtils.java              ← resolves active attributes for a player
│   ├── ElementUtils.java           ← element-related helpers
│   ├── GenericUtil.java            ← miscellaneous helpers
│   └── LootUtil.java               ← checks if a vanilla loot table is blank
│
└── api/  (HoloItemsAPI integration & extension surface)
    ├── CustomItem.java             ← base custom item class (from HoloItemsAPI)
    ├── CustomItemRegistry.java     ← registry: register/look up items
    ├── HoloItemsAPI.java           ← static accessor for the API instance
    ├── Config.java                 ← file-backed YamlConfiguration wrapper
    ├── Keys.java                   ← NamespacedKey constants (NBT keys)
    ├── Properties.java             ← typed Property<T> constants (owner, cooldown…)
    ├── Property.java               ← generic PersistentData property interface
    ├── abilities/FoodAbility.java  ← consumable-item ability helper
    ├── event/                      ← API-level damage events
    ├── interfaces/                 ← Interactable, Edible, Placeable, Swingable…
    ├── itemevent/                  ← @ItemEvent annotation, EventCache, EventContext
    ├── listener/
    │   ├── GenericListener.java    ← item-action generic listener
    │   └── ItemListener.java       ← main listener (inventory, crafting, anvil…)
    ├── loot/                       ← CustomLootRegistry, BlockLootTable, etc.
    ├── recipe/                     ← RecipeManager, RecipeBuilder, RecipeGroup…
    ├── tileentity/                 ← CustomTileEntity stub
    └── util/                       ← ItemUtils, ArmorStand3DItem, NMS helpers…
```

---

## Entry Point — `ProjectKorraItems`

`com.projectkorra.items.ProjectKorraItems` extends Bukkit's `JavaPlugin`.

### `onEnable()`

1. Stores the plugin instance and logger as static fields (`plugin`, `log`).
2. Schedules a **1-tick delayed task** via `BukkitRunnable` to call `reload()`. The delay ensures ProjectKorra has finished loading before items are processed.
3. Calls `copyAllResources()` — copies all default configuration files from the JAR on first run (reads `index.txt` for the list of files to copy).
4. Registers commands: `BaseCommand`, `GiveCommand`, `ListCommand`, `ReloadCommand`, `RecipeCommand`, `TestCommand`.
5. Initialises `HoloItemsAPI`.
6. Registers event listeners: `PKIListener`, `MenuListener`.
7. Starts `MetricsLite` for bStats analytics.

### `onDisable()`

Unregisters all recipes from both `RecipeManager` and `ConfigManager` (which tracks recipes not going through the HoloItems recipe manager).

### `reload()`

1. `AttributeBuilder.setupAttributes()` — discovers all bending ability attribute types and valid targets.
2. `new ConfigManager()` — loads/reloads all item YAML files and the language file.
3. `LootManager.loadAllLoot()` — scans the `loot/` folder and registers loot table extensions.
4. Broadcasts any accumulated errors to online admins.

### `createError(String)`

Records an error in the static `errors` list (deduplicated) and throws a logged exception so the stack trace appears in console. On next admin login, the error is re-shown in chat.

---

## Core Item Model

### `CustomItem` (`api/com/projectkorra/items/api/CustomItem.java`)

The base class provided by HoloItemsAPI. All custom items extend this. Key concepts:

- **Internal name** — lowercase string identifier (e.g. `"meteoriteingot"`).
- **`internalIntID`** — an integer used for `CustomModelData` in resource packs. Auto-assigned starting at 2300 if not explicitly set.
- **`buildStack(Player)`** — creates a fresh `ItemStack` from scratch. Applies display name, lore, leather/potion colour, skull skin, enchantments, flags, attributes, owner/cooldown/durability NBT tags, and any custom NBT via `HoloItemsAPI.getNMS().writeNBT(...)`.
- **`updateStack(ItemStack, Player)`** — refreshes an *existing* stack (e.g. when picked up) without discarding its NBT. If the material has changed it rebuilds from scratch but preserves vanilla durability damage.
- **`damageItem(ItemStack, int, Player)`** — decrements custom durability stored in the item's `PersistentDataContainer`. Calls `setDurability` and `updateStack`. When durability hits zero, the item type is set to `AIR` (destroying it) and a break sound + particle effect is played.
- **`replaceVariables(String, PersistentDataContainer)`** — text placeholder engine. Replaces `{durability}`, `{maxdurability}`, `{durabilitypercentage}`, `{durabilitycolor}`, `{owner}`, and any developer-registered variable in `{braces}`.
- **`addLore(BaseComponent)`** — supports JSON lore (BungeeCord `BaseComponent` API), automatically converting existing plain-text lore to JSON representation. This enables clickable/hoverable lore.
- **Attribute helpers** — `setArmor`, `setDamage`, `setAttackSpeed`, `setSpeed`, etc., all delegate to `setAttribute(Attribute, double, boolean)` which writes `AttributeModifier` entries to the item.

### `PKItem` (`com/projectkorra/items/PKItem.java`)

Extends `CustomItem` and adds all ProjectKorra-specific behaviour:

- **`Usage` enum** — `CONSUMABLE | WEARABLE | HOLD | INVENTORY | NONE`. Controls when item attributes are considered active (see `toConditions()`).
- **`Requirements` field** — restricts who can use the item (element, permission, world).
- **`attributes` map** — `Map<AttributeEvent, Set<AttributeModification>>`. Stores PKI-specific bending ability modifications keyed by the event that triggers them.
- **`file` field** — path of the source YAML file, used in duplicate/error messages.
- **`buildStack` / `updateStack`** — override the parent to inject ownership lore ("`You own this item!`" / "`Can only be used by {owner}!`") and usage lore ("`Right click while holding to use!`" etc.) at the end of the lore list.
- **`canUse(Player, ItemStack)`** — checks `Requirements.meets(player)` **and** player-lock (owner UUID stored in persistent data).
- **`getDurabilityColor(int)`** — maps the durability percentage to a `ChatColor` gradient: dark green (≥90 %) → green (≥60 %) → yellow (≥40 %) → gold (>25 %) → red (>5 %) → dark red.
- **Static helpers** — `getPKItem(ItemStack)`, `getItemFromName(String)`, `isPKItem(ItemStack)`.

### `ItemProperties` Interface

A minimal interface with a single method: `Map<AttributeEvent, Set<AttributeModification>> getPKIAttributes()`. Implemented by `PKItem`. This is used by `ItemUtils.getActive()` to retrieve the item's attribute modifications.

### `ItemSet`

Groups `PKItem` instances into named sets (e.g. `"FireArmor"`). The static `SETS` map acts as the registry. Sets are registered lazily when `addItem()` is first called. Defined in YAML with `SetName:` and `SetBonus:` keys. `ConfigManager` reads and stores the `SetBonus` config section alongside each item, but **the runtime detection of a complete set and application of the bonus attributes is not yet implemented** (high-priority TODO).

---

## Item Registry — `CustomItemRegistry`

Maintains a `LinkedHashMap<String, CustomItem>` keyed by lowercase internal name.

- `register(CustomItem)` — adds the item to the map, auto-assigns a `CustomModelData` ID (starting at 2300, skipping 404 which is the "invalid item" sentinel), then calls `EventCache.registerEvents(item)` to wire up any `@ItemEvent`-annotated methods.
- `getCustomItem(ItemStack)` — reads the `CUSTOM_ITEM_ID` string from the stack's `PersistentDataContainer` and looks it up.
- `invalidateItemstack(ItemStack)` — replaces an item whose internal ID is no longer registered with a red "`Invalid Item`" display, showing the old ID and owner UUID in its lore and setting `CustomModelData` to 404 for resource-pack identification.

---

## Configuration Loading — `ConfigManager`

Loaded at startup and on `/bending items reload`. The constructor:

1. Loads `language.yml` and `config.yml` as `Config` objects (wrapping `YamlConfiguration`).
2. Calls `checkLanguageFile()` — uses `addDefault(...)` to populate any missing language keys (so admins can customise messages while new keys are added automatically on update).
3. Calls `loadItems()` — walks the `items/` folder recursively (up to 15 directories deep), reading every `.yml` file.

### Item YAML Fields Reference

| Key | Type | Description |
|---|---|---|
| `Name` | String | Display name (supports `&` colour codes) |
| `Material` | String | Bukkit `Material` name |
| `Lore` | String or List | Item lore (supports `&` colour codes) |
| `Durability` | int | Custom max uses before item breaks |
| `Usage` | String | `HOLD`, `WEAR`/`WEARABLE`, `CONSUMABLE`, `INVENTORY`/`IN_INVENTORY` |
| `Glow` | boolean | Adds an enchanted glow effect |
| `OwnerLocked` / `PlayerLocked` | boolean | Binds item to the first player who receives it |
| `Requirements.World` / `Worlds` | String / List | Restricts item use to specific world(s) |
| `Requirements.Permission` / `Permissions` | String / List | Restricts to players with these permissions |
| `Requirements.Element` / `Elements` | String / List | Requires specific bending element(s) |
| `Attributes.<Target>.<Type>` | String | Bending ability modification (see [Attribute System](#attribute-system)) |
| `Color` | String / int | Hex colour for leather armour or potions (`#RRGGBB`, `0xRRGGBB`, or decimal) |
| `Enchantments.<name>` | int | Vanilla enchantment by namespaced key or legacy name, at the given level |
| `HideEnchants` / `HideEnchantments` | — | Hides enchantment lore lines |
| `HideDamage` / `HideArmor` | — | Hides attribute (damage/armour) lore lines |
| `HideOther` / `HidePotions` | — | Hides potion effect lore lines |
| `HideDye` / `HideColor` | — | Hides dye/colour information |
| `Damage` | double | Sets `GENERIC_ATTACK_DAMAGE` attribute |
| `AttackSpeed` | double | Sets `GENERIC_ATTACK_SPEED` attribute |
| `Armor` | double | Sets `GENERIC_ARMOR` attribute |
| `ArmorToughness` | double | Sets `GENERIC_ARMOR_TOUGHNESS` attribute |
| `CustomModel` / `CustomModelID` | int | Custom model data for resource packs |
| `Unstackable` | boolean | Prevents stacking by embedding a random integer in NBT |
| `SkullOwner` / `Texture` | String | Player name or Base64 skin texture for `PLAYER_HEAD` |
| `NBT.<key>` | any | Raw NBT data written via NMS |
| `SetName` | String | Associates this item with an `ItemSet` |
| `SetBonus.Attributes.*` | — | Extra attributes applied when the full set is worn |
| `<Type>Recipe` | section | Recipe definition (see [Recipe System](#recipe-system)) |

Recipes are intentionally parsed **after** all items are registered, so `PKI:ItemName` ingredient references always resolve correctly.

A `PKItemLoadEvent` is fired before each item is registered. If cancelled, the item is skipped. Plugins can also override the item instance via `event.getItem()`.

---

## Attribute System

The attribute system is the heart of the plugin — it lets items modify ProjectKorra bending abilities.

### `AttributeBuilder` — Target & Type Discovery

Called once at startup (and on reload) via `setupAttributes()`. It builds two lookup maps:

- **`targets`** — maps a lowercase string key to an `AttributeTarget`. Built from:
  - Every `SubElement` (e.g. `"lightning"`, `"lightningability"`, `"lightningcombo"`) — priority HIGH.
  - Every `Element` (e.g. `"fire"`, `"fireability"`, `"firecombo"`) — priority NORMAL.
  - `"combo"` — any combo ability.
  - `"all"` — every non-passive ability.
  - Every loaded `CoreAbility` by name (e.g. `"firepunch"`) — priority LOW.
- **`types`** — maps a lowercase attribute name to an `AttributeType`. Initially populated from a fixed set of common attribute names (`Damage`, `Cooldown`, `Range`, `Speed`, `Duration`, `Radius`, `KnockBack`, etc.) plus `"resistance"` (a PKI-specific type for damage reduction). Then extended by scanning `@Attribute`-annotated fields on every loaded `CoreAbility` class via reflection.

### `AttributeTarget` and its sub-classes

`AttributeTarget` is an abstract class with a `String prefix`, `AttributePriority`, and abstract `boolean affects(CoreAbility)`:

| Sub-class | Scope |
|---|---|
| `ElementTarget` | Abilities whose element matches (handles SubElement → parent lookup) |
| `SubElementTarget` | Abilities whose element is the exact subelement |
| `AbilityTarget` | Exactly one specific ability class |
| `ComboTarget` | Any `ComboAbility` |
| `ElementComboTarget` | Any `ComboAbility` of a given element |
| `CoreTarget` (`"all"`) | All non-passive abilities (or passives that are both instantiable and progressable) |

### `AttributeType` and `AttributeEvent`

`AttributeType` holds the PK attribute field name (e.g. `"Damage"`) and the `AttributeEvent` that should trigger it. `AttributeEvent` is an enum:

- `ABILITY_START` — applied when an ability is started.
- `DAMAGE_RECEIVED` — applied when the bending ability deals damage (used for the `Resistance` type).

### `AttributeModification` — Parsing & Application

This class parses a modification string from YAML and applies it to a `CoreAbility`.

**Parsing logic** (constructor):
1. Strips spaces and letters from the raw string (e.g. `"+25%"` stays `"+25%"`).
2. If the value is `"true"` / `"false"`, stores a boolean.
3. Detects a prefix (`+` = ADDITION, `-` = SUBTRACTION, `x`/`*` = MULTIPLICATION, `/` = DIVISION) and a `%` suffix.
4. If `%` is present, converts to a multiplier: `+25%` → multiplier `1.25`; `-15%` → multiplier `0.85`; bare `25%` → multiplier `0.25`.
5. Stores the parsed `double value` and `AttributeModifier` enum.

**`performModification(CoreAbility)`**:
- Checks `target.affects(ability)`.
- If no modifier (bare number), calls `ability.setAttribute(...)` to set the field directly.
- Otherwise calls `ability.addAttributeModifier(...)` with the appropriate `AttributeModifier` (ADD, SUBTRACT, MULTIPLY, DIVIDE).

**`performDamageModification(CoreAbility, AbilityDamageEntityEvent)`**:
- Only applies the `"resistance"` type.
- Calculates a damage reduction percentage and multiplies the event's damage by `(1 - percentage)`.

### `Requirements`

Holds sets of required permissions, worlds, and elements. `meets(Player)` returns `false` if the player lacks any one of them. Uses `BendingPlayer.getBendingPlayer(player)` to check elements. Used both to gate item effects in `canUse()` and to deny attribute application in `ItemUtils.getActive()`.

---

## Event & Inventory Cache System

This is the most architecturally complex part of the plugin. It tracks which custom items are in each player's inventory (and *where* they are) so that `@ItemEvent`-annotated methods on custom items fire at the right time with the right context.

### `CustomEventCache` (Complex)

`CustomEventCache` maintains several interlocking static maps and is updated whenever a player's inventory changes.

**Core data structures:**

```
CACHED_POSITIONS_BY_SLOT : Map<Player, Map<Integer, MutableTriple<CustomItem, ItemStack, Position>>>
    └─ Per player, per inventory slot → (item type, stack instance, current position enum)

CACHED_POSITIONS_BY_EVENT : Map<Event class, Map<Player, Set<MutableTriple<...>>>>
    └─ Per event class, per player → set of (item, stack, position) triples needing that event

POSITIONS_BY_ITEM : Map<CustomItem, Map<Player, Map<Integer, Pair<ItemStack, Position>>>>
    └─ Per item type, per player, per slot → (stack, position)

ITEMEVENT_EXECUTORS : Map<CustomItem, Map<Event class, Set<BiConsumer<Event, EventContext>>>>
    └─ Per item type, per event class → set of registered handler lambdas

METHODS_BY_EVENT : Map<Event class, Map<BiConsumer, MutableTriple<Set<CustomItem>, Target, ActiveConditions>>>
    └─ Per event class, per handler → (items that use it, Target scope, ActiveConditions)
```

**Key operations:**

- **`fullCache(Player)`** — rebuilds the entire cache for a player. Called when a player logs in or after a bulk inventory operation. Iterates all 36 slots, identifies custom items, and maps them into `CACHED_POSITIONS_BY_SLOT`, `CACHED_POSITIONS_BY_EVENT`, and `POSITIONS_BY_ITEM`.

- **`cacheItem(Player, int, ItemStack)`** — adds a single slot to the cache. Called when an item is placed into inventory.

- **`uncacheSlot(Player, int)`** — removes a slot from the cache. Called when an item is removed.

- **`updateCacheSlot(Player, int oldSlot, int newSlot)`** — moves an entry from one slot to another (e.g., items shifted in inventory).

- **`updateHeldSlot(Player, int oldHeld, int newHeld)`** — when the player changes their hotbar selection, updates the `Position` of the previously-held and newly-held items from/to `HELD`.

- **`swapCacheSlots(Player, int, int)`** — swaps two slots' cache entries, used for drag-and-drop or hotkey swaps.

- **`getPosition(int slot, int heldItemSlot)`** — maps a Bukkit slot number to the `Position` enum:
  - Slots 0–8: `HELD` (if it's the held slot) or `HOTBAR`
  - Slots 36–39: `ARMOR`
  - Slot 40: `OFFHAND`
  - Otherwise: `INVENTORY`

- **`triggerItemEvents(Event)`** — called by a dynamically-registered Bukkit `EventExecutor` for each registered event class. It iterates `METHODS_BY_EVENT` and fires each registered handler with an `EventContext` only if:
  - `Target.SELF`: the player performing the action has the item active.
  - `Target.WORLD`: any player in the same world with the item active.
  - `Target.ALL`: every online player with the item active.
  - `ActiveConditions.NONE`: fires unconditionally (no position check).
  - If an item is destroyed inside an event handler (stack becomes AIR), the slot is automatically removed from the inventory and uncached.

- **`registerItem(...)`** — called from `CustomItemRegistry.register()` → `EventCache.registerEvents()`. Wires a lambda handler for a specific event class on a custom item. If this is the first handler for a given Bukkit event class, it dynamically registers a new Bukkit listener via `Bukkit.getPluginManager().registerEvent(...)` using a dummy `Listener` and `EventExecutor` that forwards to `triggerItemEvents`.

### `EventCache` (API wrapper)

A thin wrapper in the `api.itemevent` package that delegates to `CustomEventCache`. Used by `CustomItemRegistry` and `ItemUtils` to stay decoupled from the internal implementation class.

### `@ItemEvent` annotation & `ItemListener`

`@ItemEvent` is a method-level annotation (`@Retention(RUNTIME)`) with two parameters:
- `target()` — `Target.SELF` (default), `WORLD`, or `ALL`.
- `active()` — `ActiveConditions.INVENTORY` (default), `HELD`, `MAINHAND`, `OFFHAND`, `HOTBAR`, `EQUIPED`, or `NONE`.

Placed on a method in a `CustomItem` subclass that accepts `(BukkitEvent, EventContext)`, the method automatically fires whenever the specified Bukkit event occurs and the item meets the active condition.

`ItemListener` (in `api.listener`) is a large Bukkit `Listener` that handles all the inventory bookkeeping events (item pickup, slot changes, crafting, anvil, grindstone, trade, login, quit, etc.) and updates `CustomEventCache` accordingly. It also:
- Prevents custom items from being placed as blocks (`BlockPlaceEvent`).
- Prevents custom items from being put in an anvil (`PrepareAnvilEvent`).
- Prevents picking up custom items from triggering vanilla recipe unlock (`PlayerRecipeDiscoverEvent`).
- Handles `NonConsumableChoice` recipes where ingredients are returned after crafting.
- Manages item updates when items spawn in the world (`ItemSpawnEvent`).

### `ActiveConditions` & `Position`

`ActiveConditions` is an enum that defines when an item is "active":

| Value | When active |
|---|---|
| `MAINHAND` | Only when held in main hand |
| `OFFHAND` | Only when held in off hand |
| `HELD` | In main hand or off hand |
| `HOTBAR` | Anywhere in hotbar (not held) |
| `EQUIPED` | Worn as armour |
| `INVENTORY` | Anywhere in inventory |
| `NONE` | Always (no position requirement) |

`Position` is an enum representing the *cached* location of an item: `HELD`, `OFFHAND`, `HOTBAR`, `ARMOR`, `INVENTORY`, `OTHER`.

### `EventContext`

Passed to every `@ItemEvent` handler. Contains:
- `player` — the player who triggered the event.
- `item` — the `CustomItem` instance.
- `stack` — the actual `ItemStack` in the player's inventory.
- `position` — the `Position` of that stack.

Setting `context.getStack().setType(Material.AIR)` inside a handler causes `CustomEventCache.triggerItemEvents` to automatically clean up the slot.

---

## Main Event Listener — `PKIListener`

`PKIListener` bridges the ProjectKorra ability event system with the PKI attribute system.

### `onJoin(PlayerJoinEvent)`

If the joining player has `bendingitems.admin` permission, all queued startup errors are sent to them in chat.

### `onAbilityStart(AbilityStartEvent)` (Complex)

1. Calls `ItemUtils.getActive(player, AttributeEvent.ABILITY_START)` to get a sorted map of `AttributeModification → (PKItem, Position, ItemStack)` for all active items in the player's inventory.
2. Iterates the map; for each modification, calls `attrdata.performModification(ability)`.
3. Collects `ItemStack` instances whose items were modified (deduplicating) into `itemsToDamage`.
4. For each item to damage, fires a `PKItemDamageEvent` (cancellable). If not cancelled, calls `item.damageItem(stack, 1, player)`.

This means every ability activation that is affected by a custom item consumes one durability charge from that item.

### `onAbilityDamage(AbilityDamageEntityEvent)` (Complex)

1. Calls `ItemUtils.getActive(player, AttributeEvent.DAMAGE_RECEIVED)` for `Resistance`-type attributes.
2. For each modification, calls `attrdata.performDamageModification(ability, event)` which reduces the event's damage.
3. Tracks the damage reduction amount per stack and calls `item.damageItem(stack, diff, player)` where `diff` is the integer-rounded reduction (minimum 1 if zero-rounded).

---

## Loot System

Custom items can be injected into vanilla loot tables (chest loot) and dropped on entity death or block break.

### `LootManager`

Reads every `.yml` file in the `loot/` folder. For each file:
1. Derives the `NamespacedKey` as `minecraft:chests/<relative-path-without-extension>`.
2. Looks up the vanilla `LootTable` via `Bukkit.getLootTable(key)` and validates it is non-null and non-blank.
3. Parses the YAML into a `Pool` object.
4. Registers a `LootTableExtension` lambda with `CustomLootRegistry` that, when the table is generated (e.g. a chest is opened for the first time), appends items from the `Pool` to the loot list.

### `Pool` and `Item`

`Pool` implements `LootProvider` and represents a weighted random roll mechanism:

- **`rolls`** — how many times to draw from the pool.
- **`items`** — array of `LootProvider` (either `Item`s or nested `Pool`s).
- **`totalWeight`** — sum of all item weights.

The `getItems()` method performs a **weighted random selection** for each roll. It generates a random double in `[0, totalWeight)` and iterates items, subtracting each weight until it reaches zero.

`getItemStacks(Player)` further applies a per-item **chance** check (`random.nextFloat() <= item.getChance()`) after the weighted selection, allowing further filtering.

**YAML format for loot files:**

```yaml
Pool1:              # Must start with "Pool" or "Item"
  Rolls: 5          # Number of draws from this pool
  Item1:
    Name: MyItem    # Internal name of the PKItem
    Chance: 0.2     # 20% chance to actually drop after being selected
    Amount:
      Min: 1
      Max: 3
    Weight: 2       # Relative weight within the pool
  Pool2:            # Nested sub-pool
    Rolls: 2
    Item2: ...
```

### `CustomLootRegistry`

A Bukkit `Listener` (registered at startup by HoloItemsAPI) that handles:

- **`LootGenerateEvent`** — injects `LootTableExtension` items when a chest/loot source is generated.
- **`EntityDeathEvent`** — adds items from registered `EntityType` → `LootTable` mappings (respects `DO_MOB_LOOT` game rule).
- **`BlockBreakEvent`** — fires `BlockLootTable` entries for specific block materials (respects `DO_TILE_DROPS` and survival mode check).

---

## Recipe System

Seven recipe types are supported out of the box, each with its own `RecipeParser` sub-class:

| Parser class | Config key | Bukkit recipe type |
|---|---|---|
| `Shaped` | `ShapedRecipe` | `ShapedRecipe` |
| `Shapeless` | `ShapelessRecipe` | `ShapelessRecipe` |
| `Furnace` | `FurnaceRecipe` | `FurnaceRecipe` |
| `BlastFurnace` | `BlastFurnaceRecipe` | `BlastingRecipe` |
| `Smoker` | `SmokerRecipe` | `SmokingRecipe` |
| `Campfire` | `CampfireRecipe` | `CampfireRecipe` |
| `Stonecutter` | `StonecutterRecipe` | `StonecuttingRecipe` |

All parsers are registered in `RecipeParser`'s static initialiser alongside `PKIIngredient` as the `"PKI"` ingredient provider.

### `RecipeParser` and sub-classes

`RecipeParser<T>` is abstract with:
- `parseRecipe(ConfigurationSection, PKItem)` — parse YAML → Bukkit recipe.
- `registerRecipe(T)` — add the recipe to the server.
- `unregisterRecipe(T)` — called on plugin disable.

`getIngredient(String, PKItem)` is a static helper that resolves an ingredient string:
- `#tag` or `#namespace:tag` → a `RecipeChoice.MaterialChoice` from a Bukkit `Tag<Material>`.
- `PKI:ItemName` → looks up a registered `PKItem` and returns a `RecipeChoice.ExactChoice` matching that item's NBT.
- `AIR`/empty string → `null` (empty slot).
- Any other string → a vanilla `Material`.

### Recipe YAML format

**Shaped:**
```yaml
ShapedRecipe:
  Recipe:
    - "GOLD_INGOT, AIR, GOLD_INGOT"
    - "GOLD_INGOT, #DIRT, GOLD_INGOT"
  Amount: 1
```
Each row is a comma-separated list of ingredient strings. Rows with fewer than 3 entries are right-padded with `AIR`.

**Shapeless:**
```yaml
ShapelessRecipe:
  Ingredients:
    - BLAZE_ROD
    - BLAZE_POWDER
    - "PKI:FireSigil"
  Amount: 1
```

**Furnace / BlastFurnace / Smoker / Campfire:**
```yaml
FurnaceRecipe:
  Item: "PKI:Meteorite"
  Amount: 1
  Experience: 10
  CookTime: 200   # optional, ticks
```

**Stonecutter:**
```yaml
StonecutterRecipe:
  Item: STONE
  Amount: 2
```

---

## Command System

All commands are sub-commands of `/bending items` (the `BaseCommand` registers this root with ProjectKorra's `PKCommand`).

### Available Commands

| Sub-command | Usage | Permission | Description |
|---|---|---|---|
| `give` | `/bending items give [item] [amount] [player]` | `bendingitems.command.give` | Opens a GUI if no args, otherwise gives the item directly. Tab-completes item names and player names. |
| `list` | `/bending items list [page]` | `bendingitems.command.list` | Lists all registered item names with pagination. |
| `recipe` | `/bending items recipe [item]` | (player) | Shows the crafting recipe for an item. |
| `reload` | `/bending items reload` | `bendingitems.command.reload` | Re-runs `ProjectKorraItems.reload()`. |
| `test` | `/bending items test` | (admin) | Debug command; dumps `CustomEventCache` registry info to the sender. |

`PKICommand` extends HoloItemsAPI's `SubCommand` / ProjectKorra's `SubCommand` interface and provides helpers: `hasPermission`, `isPlayer`, `correctLength`.

---

## GUI — `GiveMenu`

`GiveMenu` extends `MenuBase` (a wrapper around a Bukkit `Inventory`) and renders a paginated chest GUI for giving items:

- Dynamically calculates the number of rows (2–6) based on item count.
- Fills the second-to-last row with item stacks built via `item.buildStack(openPlayer)`.
- Navigation row (last row): previous page arrow (slot 0), next page arrow (slot 8), close button (slot 4 — a Barrier block).
- Remaining slots in navigation row are filled with black stained glass panes.
- `MenuListener` intercepts `InventoryClickEvent` for open menus and calls `MenuItem.onClick(Player)`.

---

## Properties & Persistent Data

`Properties` is a class of static `Property<T>` constants that each wrap a specific `NamespacedKey` in the item's `PersistentDataContainer`:

| Constant | Type | Key purpose |
|---|---|---|
| `OWNER` | `UUID` | UUID of the player who owns the item |
| `OWNER_NAME` | `String` | Display name of the owner (for offline players) |
| `COOLDOWN` | `Long` | Timestamp for consumable cooldown |
| `UNSTACKABLE` | `Boolean` | Random int embedded to prevent stacking |
| `ITEM_ID` | `String` | Internal item name (used to look up `CustomItem`) |
| `RENAMABLE` | `Integer` | Counter for anvil renames (non-zero = player renamed) |

The `UUIDTagType` utility class implements `PersistentDataType<byte[], UUID>` to store/retrieve UUIDs as byte arrays in NBT.

---

## Utility Classes

### `ItemUtils` (`utils/`)

- **`getActive(Player, AttributeEvent)`** — the main function for resolving which items and modifications are currently applicable to a player. Uses `EventCache.POSITIONS_BY_ITEM` to find all cached positions, filters by `Usage.toConditions().matches(position)` and `item.canUse(player, stack)`, then sorts results by `Position` ordinal (so `HELD` modifications take priority over `INVENTORY`). Returns a `LinkedHashMap<AttributeModification, Triple<PKItem, Position, ItemStack>>` ordered from highest-priority position to lowest.

### `LootUtil`

- `isBlank(LootTable)` — generates loot from the table with a dummy context and returns `true` if the result is empty. Used to validate that a loot table exists and is functional.

### `ElementUtils`

Helper methods for working with bending elements (details not publicly exposed in the YAML config).

### API Utilities (`api/util/`)

- **`ItemUtils` (api)** — `setAttribute(...)`, `setSkin(SkullMeta, String)` for head skin Base64.
- **`ReflectionUtils`** — `setTrueLore(ItemStack, List<String>)` uses NMS reflection to write JSON lore directly, bypassing the display name formatting restrictions in some Minecraft versions.
- **`NMS_118_2`** / **`INMSHandler`** — NMS abstraction for writing raw NBT tags to items.
- **`ArmorStand3DItem`** — utility for displaying items as holographic 3D models using invisible armour stands with item display.
- **`MapTagType`** / **`UUIDTagType`** — custom `PersistentDataType` implementations.
- **`TranslationUtils`** — helpers for translating colour codes in chat components.

---

## Resource Files

All default resources are listed in `index.txt` and copied to the plugin data folder on first run by `copyAllResources()`.

### `config.yml`

```yaml
ChestLoot:
  "minecraft:chests/": loot
  "terralith:chests/": terralith_loot
```
Maps chest loot table namespaces to sub-folder names within the `loot/` directory.

### `language.yml`

Contains all player-facing messages. Key categories:
- `Load.*` — error messages during item/recipe loading.
- `Item.Use.*` — restriction messages (wrong element, world, permission).
- `Item.Give.*` — give command responses.
- `Item.Command.*` — general command responses.
- `Loot.*` — loot table error messages.
- `Lore.*` — dynamic lore templates (durability bar, ownership, usage).

### `items/` (default item definitions)

| File | Contents |
|---|---|
| `examples.yml` | `ExampleStick`, `ExampleWool`, `ExampleBoots` (shaped recipe), `ExampleRod` (shapeless recipe, requirements, durability), `ExampleStone` (inventory usage) |
| `fire_armor.yml` | Full `FireHelmet/Chestplate/Leggings/Boots` set using leather armour dyed `#630604`, crafted by combining iron armour with `FireSigil`; each piece gives `+5%` to all fire ability stats and a `5%` resistance bonus |
| `meteorite.yml` | `Meteorite` (shaped), `MeteoriteIngot` (smelted from `PKI:Meteorite`), `MeteoriteSword` (player-locked, custom damage/attack speed, 530 durability, multiple enchantments, shaped recipe using `PKI:MeteoriteIngot`) |
| `misc_water.yml` | `CompressedSnow` (shaped), `SnowFallBoots` (leather boots, waterbender-only, boosts `WaterSpout` and `IceBreath` duration, feather falling II) |
| `sigils.yml` | `FireSigil`, `WaterSigil`, `EarthSigil`, `AirSigil` (all `CREEPER_BANNER_PATTERN`, used as recipe ingredients for element-specific armour) |
| `heads.yml` | (not shown — contains custom player head items) |

### `loot/` (default loot table injections)

| File | Vanilla table | Contents |
|---|---|---|
| `simple_dungeon.yml` | `minecraft:chests/simple_dungeon` | 5-roll pool: one of four elemental Infinity Shards (20 % each, 1–3 items, weight 2) |
| `desert_pyramid.yml` | `minecraft:chests/desert_pyramid` | (similar elemental shard drops) |
| `village/village_armorer.yml` | `minecraft:chests/village/village_armorer` | Village armorer chest additions |

### `drops/zombie.yml`

Defines custom mob drops for zombies (implementation via `CustomLootRegistry.registerDeathTable`).

---

## Known Issues / TODO

From `TODO.txt`:

**Bug (currently console spam risk):**
- `NullPointerException` in `PKItem.updateStack` when `player` is `null` — occurs when an item entity spawns without an associated player (e.g., a thrown or despawning item). Stack trace originates at `ItemSpawnEvent` → `ItemListener.onItemSpawn`. This can cause repeated console errors during normal gameplay and should be fixed by adding a null guard before calling `player.getUniqueId()`.

**Low priority:**
- **Bug:** Picking up a custom item currently grants the player the vanilla crafting recipe for its material. Custom items should suppress `PlayerRecipeDiscoverEvent`.
- Implement per-item permissions granted while the item is active.
- Implement `/bending items dropitem <item> <xyz> [world]` command.

**Medium priority:**
- Mob drops via YAML: `drops/zombie.yml` exists as a template and `CustomLootRegistry.registerDeathTable(EntityType, LootTable)` is implemented, but `LootManager` does not yet parse the `drops/` folder or register death tables automatically. The parsing and registration wiring is still missing.

**High priority:**
- Fix vanilla `Attribute` modifications not applying correctly to items.
- Fully implement `ItemSet` set bonuses — the YAML fields (`SetName`, `SetBonus`) are parsed and stored by `ConfigManager`, but nothing reads `SetBonus` at runtime or applies the bonus attributes when a player has the full set equipped.

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `bendingitems.admin` | op | All admin commands + error notifications on join. Inherits `bendingitems.player`, `bendingitems.command.reload`, `bendingitems.command.give` |
| `bendingitems.player` | true | Standard player access. Inherits `list`, `stats`, `items`, `equip` sub-permissions |
| `bendingitems.command.reload` | op | Access to `/bending items reload` |
| `bendingitems.command.give` | op | Access to `/bending items give` |
| `bendingitems.command.list` | true | Access to `/bending items list` |
| `bendingitems.command.stats` | true | (Reserved) |
| `bendingitems.command.items` | true | (Reserved) |
| `bendingitems.command.equip` | true | (Reserved) |
