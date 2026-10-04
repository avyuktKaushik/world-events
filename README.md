# WorldEvents — Arc 2

A Paper **1.21.11** plugin with 15 SMP world events, a spinning **WORLD EVENT** announcement,
a chat auction with a sacrifice economy, a generated escape maze and anti-abuse everywhere.

No dependencies — just drop the jar in `plugins/`.

## Building

**On GitHub (easiest):** push this repo to GitHub. The included Actions workflow builds the jar on every push —
open the **Actions** tab → latest run → download the `WorldEvents` artifact.

**Locally:** needs Java 21.

```bash
./gradlew build          # jar ends up in build/libs/WorldEvents-1.0.0.jar
./gradlew runServer      # optional: starts a local Paper 1.21.11 test server with the plugin
```

## The announcement

Every event start shows a big **WORLD EVENT** title while the event names flash rapidly in the subtitle,
each with a click sound, slowing down until it lands on the chosen event (sound + chat box + boss bar with a timer).

## Events

| Event | What it does | Anti-abuse |
|---|---|---|
| **Vulnerable** | Max health 7 hearts | Attribute-based, re-applied every second / on join / on respawn |
| **Rags to Riches** | Hero of the Village 255 | Milk & totems can't remove it |
| **Glow and Behold** | Everyone glows | Uses the entity glow flag — milk can't touch it |
| **Locator Bar** | Turns the `locator_bar` gamerule on, restores the old value after | Restored even after a crash |
| **Loot Rain** | Coords announced + countdown, then loot falls in waves | Chunks kept loaded; loot is filtered (no netherite / prot IV) |
| **THE HUNTED** | Random player: red glow, 16 hearts, buffs, coords leaked (boss bar + chat) | Logout = forfeit after grace period; no reward for non-player / same-IP / too-early kills |
| **Looting 3000** | Ore + mob drops ×4 | **Only naturally generated ore** — every ore a player places (ever) or pushes with a piston is remembered in chunk data and never multiplies. No silk-touch multiply. Mob drops only for real player kills; no spawner/egg/bred/split/golem-farm mobs; items a mob was holding are never multiplied |
| **Short Reach** | Entity reach 1.5, block reach 3 | Vanilla attributes (server side) + server distance check on hits |
| **Boss Event** | Announces random coords X 1–6000 / Z 1–6000 | Admins get a clickable `[Teleport]` to place the boss |
| **Money Grows on Trees** | Natural leaves broken by players: 45% golden apple. Logs nothing | Placed leaves never drop; trees grown during the event never drop; per-player cap |
| **Auction** | Sacrifice window, then lots bid on in chat | See below |
| **Smartie Pants** | Math questions in chat, first correct answer wins 20 gapples | Answers hidden from chat, limited guesses + cooldown per question |
| **Twists and Turns** | Generated maze, Blindness + Adventure, loot chests in dead ends, first to the emerald exit wins | Bedrock walls with a barrier ceiling flush on top (can't jump out), pearls/chorus/teleport commands blocked, elytra blocked, leaving the box teleports you back, no mob spawns, location + gamemode saved to disk and restored |
| **Tanky** | 20 hearts | Health clamped back down afterwards |
| **Unfortunate** | 30% chance gapples, potions (drink + splash), shields and attacks fail — with a sound | Failed items are still consumed |

**All effects, health and glow survive relogging, milk, totems and death.** Events don't apply effects directly —
they declare what each player should have, and the plugin reconciles every player every second, on join and on respawn.
Leftovers are cleaned up automatically if an event ended while someone was offline.

## Rewards

* Every event ends with a **participation roll** for every player who was *active* (not AFK) for at least 50% of it:
  **20% jackpot** (shulker box full of Prot III / Unbreaking III / Mending diamond armor) or a consolation item.
* Winners get extra: Hunted killer → 2 enchanted gapples, 2 stacks of gapples, Prot III diamond set.
  Smartie Pants → 20 gapples. Maze winner → gapples + armor set. Each also rolls the jackpot.
* **Every reward is filtered: netherite is removed and Protection is capped at III**, no matter what's in the config.
* Rewards for offline players are saved and given when they join.

## Auction

1. The auction event opens a **sacrifice window** (its duration). Players use **`/sacrifice`** — put items in, see the value, confirm.
   Golden apples ≈ $20/stack, diamond armor pieces $1–8 depending on enchants (scaled by durability).
2. Lots are announced in a big chat box + title. **Type an amount in chat to bid.** You can never bid more than you have.
   Bids in the last 10 s extend the timer. **No buyouts.** The winner is announced in chat.
3. Lot items are only **announced** — admins get a message and the win is logged to `plugins/WorldEvents/auction-winners.log`.
   The **OP Villager** is given by the plugin (a spawn item: master librarian selling Prot III, Mending, Unbreaking III, Sharpness V for 1 emerald each, unlimited uses).
4. Starting prices rise automatically when the richest online players have a lot of money.
5. 1–2 auctions per day run automatically inside the configured time windows (`auction.auto`).

Money-printing protection: only non-farmable items have a sacrifice value, enchanted books are worth $0 by default,
and anything bought from a villager or wandering trader is tagged and worth $0.

## Commands

| Command | Description |
|---|---|
| `/worldevent random [noroulette]` | Start a random event |
| `/worldevent start <event> [minutes] [noroulette]` | Start a specific event |
| `/worldevent stop <event\|all>` | Stop events |
| `/worldevent list` | All events + status (click to start) |
| `/worldevent gui` | Duration GUI (left/right ±1 min, shift ±5, Q toggles random pool, F starts) |
| `/worldevent reload` | Reload config |
| `/worldevent hunted <player> [minutes]` | Hunted with a chosen target |
| `/worldevent lootrain [here] [minutes]` | Loot rain at random coords or where you stand |
| `/worldevent boss [x z]` · `/worldevent bosstp` | Boss event (random or fixed coords) · teleport there |
| `/worldevent maze setorigin` | Set the maze corner to your position |
| `/worldevent auction start` | Auction event (sacrifice window first) |
| `/worldevent auction now [lots]` | Auction immediately |
| `/worldevent auction item <price>` | Auction the item in your hand (given to the winner, returned if unsold) |
| `/worldevent auction text <price> <description>` | Auction something you'll hand out manually |
| `/worldevent auction skip \| cancel` | Close current lot / cancel |
| `/worldevent balance <player> [set\|add\|take <amount>]` | Manage auction money |
| `/worldevent givejackpot <player>` | Give the jackpot shulker |
| `/sacrifice` | Sacrifice items for auction money |
| `/abal` | Your auction balance |
| `/activeevents` | Active events |

Event ids: `vulnerable, rags_to_riches, glow_and_behold, locator_bar, loot_rain, the_hunted, looting_3000, short_reach,
boss, money_grows_on_trees, auction, smartie_pants, twists_and_turns, tanky, unfortunate`.

## Permissions

* `worldevents.admin` (op) — all admin commands
* `worldevents.sacrifice` (everyone) — `/sacrifice`
* `worldevents.bypass` (nobody) — not affected by events

## Notes

* **Pick an empty area for the maze** (`/worldevent maze setorigin`) — it is built there and fully cleared afterwards.
  Default is X/Z 10000 at Y 290.
* The red Hunted glow uses a scoreboard team on the main scoreboard. Tab/nametag plugins that force their own teams may override the colour.
* Stop running events before removing the plugin, so health/reach modifiers are cleaned up.
* Golden apples are valued at $20/stack as requested — if your server has a gold farm, consider lowering that value in `config.yml`.
