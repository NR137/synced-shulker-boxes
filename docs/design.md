# Synced Shulker Boxes: design

How the mod works inside, as built for 0.9.0. Server-side only, the world stays vanilla-valid (see `../CLAUDE.md`).
What players see is in the README.

## Items and the mark

- A synced box is a vanilla shulker box with a mark in its `custom_data`: `{synced_shulker_boxes: {}}` from the
  smithing table ("Syncable"), `{synced_shulker_boxes: {group: "<uuid>", member: "<uuid>"}}` once linked. Nothing
  else is saved on the item: the glint, the coloured name ("Syncable/Synced Red Shulker Box") and the tooltip lines
  are added only to what is sent to clients (`Boxes.forClient`, through a hook on the item stack network codec).
- A placed box keeps the mark in its block entity's components. Breaking it in survival goes through the vanilla loot
  table, which copies only name, contents and lock: the mark is put back onto the drop (`Boxes.keepMark`).
- Recipes: 17 `smithing_transform` data pack recipes (one per colour), result = the base with the mark. Base must be
  empty and unmarked, the player needs `craft` (`SmithingMenuMixin`). Never added to recipe books
  (`ServerPlayerMixin`), so a vanilla server finds no unknown recipe ids.

## Groups

- Saved in `<world>/dimensions/minecraft/overworld/data/synced_shulker_boxes/groups.dat` (vanilla ignores it): id,
  name, members in the order they joined, the 27 items (with their data version, fixed by the game's data fixer when a
  newer Minecraft loads them), whether the anchor holds the current copy.
- The group's code ("K7QF") is the first 20 bits of its id in Crockford base 32, unique among a world's groups
  (`Groups.newGroupId`). A box's place ("2/3") is its index among the current members, so it moves up when an older
  box is destroyed.
- **The anchor** is the oldest member. It also holds the items in vanilla's own box contents, so they survive removing
  the mod; the others are empty in vanilla terms. When it changes or the items change, the copy is written as soon as
  the anchor is loaded: placed (every server tick) or as an item in an online player's inventory, ender chest or open
  screen.
- Placed boxes are handled from the server tick over a list of loaded shulker boxes (`BlockEntityMixin`,
  `Boxes.tickLoaded`), never from the box's own tick: Lithium lets closed shulker boxes sleep.

## Linking (anvil)

- First slot + second slot, both empty and unmarked-linked: a new group, the first box is the anchor; a typed name
  goes onto both and becomes the group's name. A linked box (first) + an unlinked synced box (second): the second
  joins, with a typed name, the first box's name or the group's name. Cost: `linkCostLevels` per box joining.
- The result is the second box; the first goes back into its slot (`AnvilMenuMixin`). Vanilla state that would let the
  result be taken twice is reset (`onlyRenaming`, the remaining name). The client computes its own anvil result, so
  the server sends the anvil's full state after each click or rename.

## Using it

- Opening any member opens the group's shared container (`BoxView`): lid, sound and reach from the box opened, items
  from the group, so several players at several boxes share one inventory. Opening plays the lodestone compass "lock"
  sound on top of the shulker sound and sends trails in the box's colour to all sides.
- No automation: `getSlotsForFace` is empty for linked boxes (hoppers, hopper minecarts, droppers, crafters).
  Comparators read the shared contents.
- Destroyed items (`BlockItem.onDestroyed`: lava, fire, cactus, explosions) leave the group without dropping
  anything; the last one drops the group's items. Lost items (despawn, void, `/kill`, `Boxes.onLost`) leave the group;
  the last one takes the items with it, as in vanilla.
- Creative clients send items back with what they were shown: the server drops the client-only parts and keeps its
  own contents (`ServerGamePacketListenerImplMixin`). After a click, clients report hashes of what they see, which the
  server compares with `forClient` of its own items (`RemoteSlotMixin`), or every click would resend the slots.

## Clients with the mod

- The glint: vanilla doesn't draw it on block entity items (MC-69683). The box is drawn with its vanilla model in one
  pass with a render type of its own: the armor's cutout glint pipeline with the item glint texture and movement
  (`GlintRenderType`). Solid glint types fill the texture's holes; a separate glint layer flickers where lid and base
  overlap. Placed boxes learn their mark from the block entity's update tag, which the server sends for synced boxes.
- Particles: the opening trails come from the server and can be hidden; the always-on particles are drawn by the
  client itself, and the server's are always hidden there (`ClientPacketListenerMixin`, `AmbientParticles`).
- The anvil's name field: "Synced Shulker Box" for two unlinked boxes, and scrolled to the start on every new text.

## Settings

Server (`Config`, `/syncedshulkers config`): `maxGroupSize`, `linkCostLevels`, `openingParticles`, `ambientParticles`,
permission defaults; options missing from the file keep their defaults. Per player (`PlayerSettings`,
`/syncedshulkers particles`): which particles the server sends them. Client (`ClientSettings`): glint, opening
particles, particles always.

## Not yet

Server-side translations (all texts are English), where each member was last seen (admin info). Both wait for the
shared library.
