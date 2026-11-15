# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.9.0] - 2026-11-15

The first version of Synced Shulker Boxes, for Minecraft 26.3 on Fabric and NeoForge servers: link shulker boxes so
they share one inventory.

- **Syncable boxes** from the smithing table (echo shard, empty shulker box, eye of ender), **linked at the anvil**:
  two make a group, more join it, up to 8 (configurable).
- **One inventory** for all boxes of a group, from anywhere; the oldest box keeps a vanilla copy of the items, so the
  world stays vanilla-valid without the mod.
- **Clearly synced:** coloured name, group code and place in the tooltip and the screen's title, the shared contents
  in tooltips, particles when opened (and optionally all the time).
- **Safe:** no hoppers or other automation, no dupes through the anvil, creative mode or two players at once; tested
  with Lithium.
- **Server options and permissions** (LuckPerms through Fabric's and NeoForge's permission API), admin commands, a
  per-player choice of particles.
- **Optional on the client:** the enchantment glint on synced boxes and client-side particle settings, with a
  settings screen (Mod Menu, NeoForge's mod list).
