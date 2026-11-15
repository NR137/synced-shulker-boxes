"""What the smoke test (smoke_test.py) does with this mod's features. Each mod fills this file in.

The smoke test calls these in order:
  configure(folder)                 before the modded server starts: write config files into folder/config
  with_mod(server, check)           the modded server runs, without players: leave in the world what the mod saves
  with_client(server, name, check)  a vanilla client is online as `name` (an operator): what players without the mod
                                    see and do
  on_vanilla(server, check)         the same world on the vanilla server, the mod removed: what must have survived,
                                    and what must be plain vanilla again
`server.command(line)` types a console command; `server.run(line, expect, what)` types one and checks that an output
line matches the regex `expect`. `check(ok, what)` records a check.

expected_problems and expected_vanilla_warnings filter out log lines this scenario causes on purpose.

Synced Shulker Boxes: a linked pair placed with the mod; on vanilla they are plain shulker boxes whose custom_data
vanilla keeps and ignores. The shared contents and the anchor's copy are covered by the game tests.
"""

import re
import time
from pathlib import Path

# Mods the servers run with besides ours (from Modrinth, newest for the version and loader): Lithium lets closed shulker
# boxes sleep and moves items in hoppers with code of its own, and many servers run it.
TEST_MODS = ['lithium']

# Two boxes of one group, placed above the flat world's grass (y = -61), in a chunk kept loaded with /forceload.
ANCHOR = '0 -60 0'
MEMBER = '2 -60 0'
HOPPER = '2 -59 0'
state = {}


def configure(folder: Path) -> None:
	pass


def with_mod(server, check) -> None:
	server.run('forceload add 0 0', r'[Mm]arked chunk|already marked', 'the test chunk stays loaded')
	server.run('syncedshulkers config maxGroupSize 6', r'maxGroupSize: 6', 'the config command changes an option')
	server.run('syncedshulkers config maxGroupSize 8', r'maxGroupSize: 8', 'and changes it back')
	# The always-on particles sent to everyone (off by default), so the vanilla client gets them too.
	server.run('syncedshulkers config ambientParticles true', r'ambientParticles: true', 'the server sends particles always')


def with_client(server, name: str, check) -> None:
	given = server.run(f'execute as {name} run syncedshulkers give 2', r'Gave 2 linked boxes of group \w{4} ([0-9a-f-]{36})',
		'the admin command gives a linked pair')
	if given:
		state['group'] = re.search(r'group \w{4} ([0-9a-f-]{36})', given).group(1)
	for position, slot in ((ANCHOR, 0), (MEMBER, 1)):
		server.run(f'setblock {position} minecraft:shulker_box', r'Changed the block', f'a box is placed at {position}')
		server.run(f'data modify block {position} components set from entity {name} Inventory[{slot}].components',
			r'Modified block data', f'the box at {position} becomes a linked member')
	time.sleep(1)
	if 'group' in state:
		server.run(f'syncedshulkers group {state["group"]}', r'2 boxes', 'the group has both boxes')
	server.run(f'data get block {MEMBER} components', r'synced_shulker_boxes', 'the placed member keeps its mark')
	# A player's own choice of particles, as a vanilla client would set it.
	server.run(f'execute as {name} run syncedshulkers particles always off', r'always off', 'a player turns the particles off for themselves')
	server.run(f'execute as {name} run syncedshulkers particles always on', r'always on', 'and on again')
	server.run(f'data get entity {name} Inventory[0].components', r'synced_shulker_boxes',
		'the linked boxes in the player\'s inventory have the mark')
	# A hopper on top of a member, with a diamond: it must not push it in (with Lithium's hopper code).
	server.run(f'setblock {HOPPER} minecraft:hopper[facing=down]', r'Changed the block', 'a hopper is placed on a member')
	server.run(f'item replace block {HOPPER} container.0 with minecraft:diamond', r'Replaced \d+ slot', 'the hopper gets a diamond')
	time.sleep(3)
	server.run(f'data get block {HOPPER} Items', r'minecraft:diamond', 'the hopper kept its diamond')


def on_vanilla(server, check) -> None:
	server.run(f'data get block {ANCHOR} id', r'shulker_box', 'the anchor is a plain shulker box on vanilla')
	server.run(f'data get block {MEMBER} components', r'synced_shulker_boxes',
		'vanilla keeps the mark in custom_data (ignored)')


def expected_problems(lines: list[str]) -> list[str]:
	return lines


def expected_vanilla_warnings(lines: list[str]) -> list[str]:
	return lines
