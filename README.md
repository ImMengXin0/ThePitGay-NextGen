# ThePitGay-High

This branch targets **Paper/Purpur 1.21.11 and Java 25**, using **FancyNpcs 2.9.2** and **FancyHolograms 2.10.1**. Paper downloads the libraries declared in `plugin.yml` on first startup and reuses the server's `libraries/` cache. See the [High migration notes](docs/HIGH-MIGRATION.md) for validation results and remaining server checks.

## Project

ThePitGay-NextGen is a maintenance fork of [ThePitGay](https://github.com/Patcher0/ThePitGay), continuing the ThePitStudy / ThePitPremium codebase with core fixes, plugin compatibility improvements, and performance optimizations.

* Configurable gameplay and menus
* Compatibility bridges for migrated extensions; historical 1.8 binaries require review and recompilation
* Improved item handling, trading, and player data persistence
* Reduced repeated work in music playback, enchantment animations, and scoreboards

Install JDK 21 for Gradle/compilation and JDK 25 for tests. Build this branch with `bash gradlew build :core:paperLibrarySmoke` (Windows: `.\gradlew.bat build :core:paperLibrarySmoke`). The plugin is `core/build/libs/ThePitGay-High.jar`. Existing GitHub releases may belong to the 1.8 maintenance branch.

Prepare a separate 1.21.11 environment with copies of the maps, configuration and database backups before server validation. Modern item saves require restoring the original backups when rolling back to a 1.8 core.

## Features

* Unique equipment UUIDs and persistent custom item names
* Trade session validation and pending item returns
* Enchantment table viewing with membership-based privacy controls
* Command completion, menu, scoreboard, and TAB improvements
* A complete Angel armor set
* Ten built-in humanoid NPC skins featuring Honkai: Star Rail characters
* Embedded MythicNPCLib facade and MythicFramework Core/Bukkit, with FancyNpcs and FancyHolograms rendering

Old default NPC skins migrate automatically, while custom skin configurations are preserved. Villager and silverfish NPCs retain their original entity models.

The [older NPC migration notes](docs/MYTHIC-NPC-MIGRATION.md) describe the 1.8 branch. On High, install FancyNpcs, FancyHolograms, LuckPerms and the pinned ProtocolLib build documented in [BUILDING](docs/BUILDING.md). The optional legacy NPCView plugin requires its own migration.

### Validation

Automated checks use the real Paper 1.21.11 registries and item converters, with mocked transport, scheduling and database boundaries. The build also tests the embedded framework and platform module. External plugin binaries missing from the checkout are explicitly skipped. Actual server startup, native bot movement/combat, database integration and multiplayer load tests remain to be performed; see the [migration report](docs/HIGH-MIGRATION.md).

### Logging and Rollback

Use `/rollback` to inspect and restore player inventory backups. Trade fixes improve session validation and item returns, but do not provide cross-player database transactions or automatic recovery from a forced shutdown.

### Developer API

Use the latest core build as a dependency when developing integrations. Some facade signatures remain available, while changed Bukkit/NMS and chat types require extension recompilation. See the [build and test instructions](docs/BUILDING.md) for required dependencies and test fixtures.

## Contributing

Want to add features or fix bugs? Clone this repository, follow the [build instructions](docs/BUILDING.md), and run the relevant regression tests before submitting a pull request.

The High branch runs on Java 25 and targets Paper/Purpur 1.21.11. Full extension integration tests require migrated external plugin JARs as documented in the build instructions.

## Special thanks

Thanks to the upstream ThePitGay, ThePitStudy, and ThePitPremium contributors, and to the community creators of the Minecraft character skins bundled with this fork.

<a href="https://jb.gg/OpenSourceSupport"><img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg" width="150">
</a>

[JetBrains](https://jb.gg/OpenSourceSupport), creators of IntelliJ IDEA, supports open-source projects through its Open Source Licenses program.
