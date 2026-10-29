# Embedded NPC libraries

The core embeds MythicNPCLib and the Core/Bukkit modules of MythicFramework from the source snapshots supplied on 2026-10-28 (both declare version 1.0.0). No separate Mythic plugin or Citizens installation is needed for the core's NPCs. ProtocolLib remains required.

MythicNPCLib lives in `core/src/main/java/cn/mythicland/npclib`. MythicFramework lives in `vendor/mythic-framework/{core,bukkit}` and builds as two local Gradle subprojects. Builds use these checked-in sources, without referencing a Downloads directory or requiring a sibling checkout. The independent Framework World and Database modules are outside this NPC integration.

The final JAR relocates MythicNPCLib to `net.mizukilab.pit.libs.npclib`, preserving existing Addon and RepairExTend method descriptors. It relocates MythicFramework to `net.mizukilab.pit.libs.mythicframework` and isolates its Gson, SnakeYAML, Reflections, Javassist and pinyin4j dependencies. Framework services are packaged but do not register commands, menus, database connections or background workers automatically. Bot movement/combat ticking starts only when an integration requests a robot.

## Local integration changes

* Retain the old `NPCLib.createNPC` entry points and `Skin.getValue/getSignature` alongside the new packet-NPC API.
* Compile for Java 17: replace Java 21 collection accessors, record deconstruction and clamp calls with equivalent implementations. Keep ordered point removal efficient with the already required fastutil linked map.
* Own and release every NPC, listener, scheduled visibility task, delayed tab-profile task and externally controlled robot during shutdown.
* Restrict visibility and packet interaction processing to each library's own NPCs. Marshal off-thread clicks to Bukkit, recheck the player/entity/visibility/range, coalesce pending packets, and debounce paired right-click packets.
* Refresh menu NPC text on one synchronous task. Skip unchanged hologram text, clear viewer caches on logout, handle changed line counts and null text, and destroy all client entities without modifying collections being iterated.
* Refresh visibility after teleport/respawn using a bounded next-tick task, rather than polling for an exact respawn location. Map reload destroys and rebuilds menu NPCs, including their skin, packets and holograms.
* Remove Citizens API imports and the soft dependency. NPC identity uses the new registry, custom entity registry or NPC/Bot metadata. Keep NPCView's death-packet cleanup optional without linking Citizens classes.

The supplied `NPCView-1.0-SNAPSHOT.jar` independently declares `depend: Citizens`. That older plugin cannot load without Citizens; removing the core's dependency does not migrate NPCs managed by other plugins or rewrite NPCView's own dependencies. Existing villager/silverfish core NPCs remain Bukkit entities, retaining their entity-interaction API.

## Validation

The regression suite covers multiple viewers, null and resized text, offline cache cleanup, hidden/out-of-range interactions, paired right-click packets, independent library instances, world/chunk/respawn visibility and existing extension NPC method overrides. Artifact inspection verifies embedded classes and absence of Citizens linkage. Framework path, scheduling, identity-cache and world/edit tests also run with `:core:build`.

Version 4.4.10 passed 445 core tests and 27 Framework tests. One legacy Addon registration test was skipped because the supplied Addon build no longer contains `CrazyDogStreakMode`; the separate saved-profile cleanup test still ran. Static linkage audits checked 372 core member references from Addon and 295 from RepairExTend without missing classes or members. The shipped JAR contains 98 NPC-library classes, no Citizens references or original unshaded Mythic classes, and no imported bytecode newer than Java 17. These checks use PandaSpigot 226 and the supplied extension binaries; live-server gameplay and performance have not been verified.

## Native Bot combat compatibility (4.4.11)

RepairExTend 5.3.0 embeds NPCLib in its own namespace. Its bots call an internal death callback and remove themselves without firing Bukkit's `PlayerDeathEvent`. The core recognizes both embedded copies by cached entity type and keeps separate temporary combat profiles for native bots, without registering database operators or loading LuckPerms users for their random UUIDs.

After damage, one callback per tick processes the affected bots and confirms their actual health before settling a death through the normal core kill flow. Melee and projectile attribution, combat timers, hit counts, streaks and kill rewards use the existing rules. A shared guard prevents an explicit extension death call or Bukkit death event from awarding the same bot twice. Cancelled hits, later healing and removal of a healthy bot do not create a kill; a bot delivering the final blow does not award an earlier human attacker. RepairExTend continues to own bot removal and respawning.

Version 4.4.11 passed 455 core tests and 27 Framework tests, with the same single legacy Addon skip. The new tests exercise real streak/reward settlement, attack statistics, arrows, independent bot profiles, duplicate deaths and shutdown cleanup. A real RepairExTend 5.3.0 classloader check and a 307-member static linkage audit passed alongside the Addon audit. Live-server verification remains necessary.

Keep original copyright headers in imported sources. This integration uses the provided source snapshots; it does not claim ownership of upstream work.
