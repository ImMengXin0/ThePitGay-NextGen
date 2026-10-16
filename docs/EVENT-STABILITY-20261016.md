# Activity stability audit — 2026-10-16

This audit covers the eight built-in minor activities and seven major activities. Only the core is modified. ThePitAddon, ThePitRepairExTend and NPCView sources/binaries were used for compatibility checks and were not edited.

## Scope and fixes

| Activity | Confirmed issues addressed |
| --- | --- |
| Auction | Offline refunds were skipped; completed rounds could settle again; stale sign input could bid in a later round. Added settlement/round checks, loaded-profile checks, offline mail delivery and exact integer price increments. |
| Cake | Region/world selection was fixed at construction and cleanup could target the wrong world. Select a valid configured region at activation, require the exact world and block bounds, serialize build/undo, and discard the undone session. |
| Care package | Old loot remained in the static pool and could exhaust available slots during generation. Clear each round, bind menu buttons to that round, preserve loot/eligibility when inventory is full, and restore the original block. |
| Double rewards | The implementation added twice the base reward to the base reward (three times total). It now adds the base once. |
| Dragon egg | The location check rejected the real egg; relocation arithmetic did not implement the advertised interval. Correct both, cap rewarded clicks, reset round state, and restore replaced blocks. |
| Everyone bounty | A one-shot activity tried to stop before the factory marked it active. The factory now establishes the lifecycle before activation; unloaded profiles are skipped. |
| Hunt | Old kill counts and long-lived effects survived rounds. Reset counts and use short effects that naturally expire; do not indiscriminately strip other potion effects. |
| Quick maths | Substring answers and asynchronous reward mutations allowed incorrect/duplicate winners. Match the complete trimmed answer and award on the server thread, once per player, to at most five winners. Reward is 100 coins as announced. |
| Block head | Environmental deaths, cumulative coordinate mutation, duplicate claims, cross-world keys, exhausted material pools, unrelated falling-block removal and incomplete restoration. Use independent coordinates, ownership checks, full block snapshots, container protection, a distinct solid palette and owned-entity cleanup. |
| Hamburger | Reused timer/state, incorrect villager identity, insufficient spawn points, missing participants and inconsistent 500/600-order goal. Reset each round, validate NPC identity, skip obstructed/exhausted locations, fix settlement and use 500 orders consistently. |
| Rage pit | Reused timer and scores, inconsistent health baseline, wrong wall world, untracked tasks and unsafe fallback to ordinary WorldEdit from an async worker. Reset state, use configured world/FAWE, retain extra-health bonuses and restore health on cleanup. |
| Red vs blue | Rejoining players could be assigned twice and previous scores survived rounds. Reset teams/scores, keep one team per player and own the repeating task. |
| Respawn family | Timer tasks could remain after an early stop. Cancel them explicitly and return immediately after expiry. |
| Spire | Death subtraction increased souls, rank offset mis-awarded prizes, stale callbacks altered departed players, delayed restoration could overwrite later state, and old edit sessions were reused. Correct accounting, check round/player identity, restore participant inventories synchronously and undo edits in reverse order. |
| Squads | BukkitRunnable instances and timers were reused, team membership/capture state leaked, distances could cross worlds and banner blocks were not restored. Create fresh tasks, reset state, preserve one team per player, compare worlds and restore banner snapshots. |

## Shared lifecycle and threading

- Bukkit player/inventory/entity logic and BossBar updates run on the main thread. FAWE work remains on one serial worker to preserve build → undo → next-build order.
- Activation is mutually exclusive. Preparations cannot be duplicated. Required major-event coordinates are checked before player/world mutations.
- Every activation has a generation. Delayed work from a previous generation does not execute in a later one. Repeating activity tasks are cancelled on exit.
- Startup failures and plugin shutdown perform cleanup with an abort flag; major completion rewards are suppressed, and auction bids are refunded.
- The built-in major-event deadline is five minutes. Minor expiry is checked independently of the cooldown for attempting a new activity.
- Shutdown also drains active FAWE queues belonging to this worker: FAWE's async flush waits for main-thread processing. This avoids simply waiting on a worker while holding the thread it needs. The worker wait is bounded; individual FAWE operations still depend on the installed FAWE/server implementation.

## Review of the first patch

A second pass found and corrected remaining rank offsets, reward-on-abort paths, stale Spire callbacks, reused rollback sessions, destructive NPC-spawn clearing, snapshot ownership and material-pool exhaustion. It also removed the async ordinary-WorldEdit fallback and checked the actual FAWE undo/flush bytecode before implementing shutdown draining. Schematic streams are now closed after use.

## Validation

- Full offline Gradle build and the complete regression suite: 430 tests, including 23 added lifecycle/activity tests; no failures, errors or skipped tests.
- New tests cover self-ending activities, activation failure, duplicate starts, forced minimum bypass, off-thread entry, preparation, stale callbacks, expiry, shutdown cancellation, empty queues, reward multipliers, maths winners/answer matching, egg targeting/restoration, stale/full-inventory care-package menus, offline auction cancellation, serialized world edits, container protection, missing maps and material-pool cycling.
- Existing tests exercise the real Addon, RepairExTend and NPCView binaries at mocked Bukkit boundaries. Separate constant-pool linkage scans checked 370 Addon and 295 RepairExTend core-member references: no missing core classes or member/linkage issues were found. Reflection and configuration-dependent behavior remain outside that scan.
- Tests simulate scheduler/player/database boundaries. No live multiplayer stress test, client hologram/particle inspection or real-server FAWE shutdown round trip was performed. This release does not provide crash/blackout recovery or database transactions spanning all auction recipients.

## Configuration and deployment

ThePitAddon currently defaults `clear-epic-events` to `true`, and explicitly replaces the core's major-event registry with an empty list. Set it to `false` in the Addon configuration and fully restart if automatic major activities are wanted. The core intentionally preserves that setting. Squads retains its existing effectively-disabled automatic player threshold; this patch does not enable it automatically.

Back up the world and player data as for a normal core update, stop the server, replace `ThePitGay-V.jar`, and restart. Do not hot-reload. On a staging server, exercise two consecutive rounds, early stop, reconnect, non-default-world maps and full-inventory loot, then check world restoration and reward totals. Existing missing/duplicated historical items are not reconstructed.
