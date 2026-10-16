# ThePitGay-NextGen

## What is ThePitGay-NextGen and why should I use it?

ThePitGay-NextGen is a maintenance fork of [ThePitGay](https://github.com/Patcher0/ThePitGay), continuing the ThePitStudy / ThePitPremium codebase with core fixes, plugin compatibility improvements, and performance optimizations.

* Configurable gameplay and menus
* Compatibility fixes for ThePitAddon, ThePitRepairExTend, and NPCView
* Improved item handling, trading, and player data persistence
* Reduced repeated work in music playback, enchantment animations, and scoreboards

Builds are available on [GitHub Releases](https://github.com/ImMengXin0/ThePitGay-NextGen/releases/latest). See the [changelog](CHANGELOG.md) for the accumulated fixes.

To update, stop the server, replace the existing core with `ThePitGay-V.jar`, and restart fully. Keep your existing configuration and player data, and load only one copy of the core.

## Features

* Unique equipment UUIDs and persistent custom item names
* Trade session validation and pending item returns
* Enchantment table viewing with membership-based privacy controls
* Command completion, menu, scoreboard, and TAB improvements
* A complete Angel armor set
* Ten built-in humanoid NPC skins featuring Honkai: Star Rail characters

Old default NPC skins migrate automatically, while custom skin configurations are preserved. Villager and silverfish NPCs retain their original entity models.

### Performance

This fork reduces repeated item parsing, shares common scoreboard calculations, avoids unnecessary enchantment menu refreshes, and releases inactive music and NPC tasks. The maintenance release passed 430 automated tests; live-server CPU, memory, and TPS improvements have not been benchmarked.

### Logging and Rollback

Use `/rollback` to inspect and restore player inventory backups. Trade fixes improve session validation and item returns, but do not provide cross-player database transactions or automatic recovery from a forced shutdown.

### Developer API

Use the latest core build as a dependency when developing integrations. Compatibility bridges preserve legacy interfaces used by supported extensions. See the [build and test instructions](docs/BUILDING.md) for required dependencies and test fixtures.

## Contributing

Want to add features or fix bugs? Clone this repository, follow the [build instructions](docs/BUILDING.md), and run the relevant regression tests before submitting a pull request.

The project uses a Java 17 compilation toolchain and targets Minecraft 1.8.8 R3 with PandaSpigot extensions. Full compatibility tests require the external plugin JARs documented in the build instructions.

## Special thanks

Thanks to the upstream ThePitGay, ThePitStudy, and ThePitPremium contributors, and to the community creators of the Minecraft character skins bundled with this fork.

<a href="https://jb.gg/OpenSourceSupport"><img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg" width="150">
</a>

[JetBrains](https://jb.gg/OpenSourceSupport), creators of IntelliJ IDEA, supports open-source projects through its Open Source Licenses program.
