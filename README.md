# RLCraftFurnaceFix

Minecraft Forge 1.12.2 mod that backports modern furnace XP preservation behavior to vanilla furnaces.

## What it changes

- Automation extraction (hopper/pipes using inventory extraction) no longer destroys smelting XP.
- XP is accumulated on each vanilla furnace tile entity.
- Stored XP is paid out when a player manually takes output from the furnace.
- Stored XP is also paid out when the furnace block is broken (server-side only, single payout).

## Build

This project uses ForgeGradle 2.3 with Java 8 compatibility targets.

Local build command (without gradle wrapper):

```bash
gradle clean build
```

GitHub Actions builds with a pinned Gradle version and uploads built jars as artifacts.
