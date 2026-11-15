"""Singleplayer test: the mod on the integrated server of a real Minecraft client, on Fabric or NeoForge.

Server-side mods also run in singleplayer, where the client hosts the server itself. Starts the loader's dev client
(./gradlew :<loader>:runSmokeClient) on a virtual screen (Xvfb, software OpenGL), which opens a copy of the smoke
test's world directly (--quickPlaySingleplayer). Checks that the world opens with the mod running and the player in
it, then closes the client and checks its log for errors from the mod or its mixins.

Usage: python3 scripts/smoke_test/client_test.py fabric|neoforge
Needs: the smoke test for that loader first (its world), and xvfb-run (both in the dev image, docker/Dockerfile).
"""

import os
import re
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from smoke_test import CACHE, MC_VERSION, MOD_RUNNING, ROOT, check, failures, mod_problems  # noqa: E402

START_TIMEOUT_S = 900  # The first start downloads the client's assets.


def set_up(loader: str) -> Path:
	world = CACHE / f'{loader}-{MC_VERSION}' / 'world'
	if not (world / 'level.dat').is_file():
		sys.exit(f'{world} is missing: run the smoke test for {loader} first (./tasks.sh smoke {loader})')
	folder = ROOT / loader / 'build' / 'run' / 'smokeClient'
	for name in ('saves', 'config', 'logs', 'crash-reports', 'options.txt'):
		path = folder / name
		if path.is_dir():
			shutil.rmtree(path)
		elif path.exists():
			path.unlink()
	(folder / 'saves').mkdir(parents=True)
	shutil.copytree(world, folder / 'saves' / 'smoke', ignore=shutil.ignore_patterns('session.lock'))
	# No first-start screens, and no pause when the window has no focus (a paused game stops the integrated server).
	(folder / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:2\n')
	return folder


def processes_in(folder: Path) -> list[int]:
	"""The client is started by Gradle; it is found by its working folder, which is the run folder."""
	pids = []
	for entry in Path('/proc').iterdir():
		if entry.name.isdigit():
			try:
				if Path(os.readlink(entry / 'cwd')).resolve() == folder.resolve():
					pids.append(int(entry.name))
			except OSError:
				pass
	return pids


def main() -> None:
	if len(sys.argv) != 2 or sys.argv[1] not in ('fabric', 'neoforge'):
		sys.exit(__doc__)
	loader = sys.argv[1]
	folder = set_up(loader)
	log_path = folder / 'client-test.log'
	print(f'Starting the {loader} client in {folder}', flush=True)
	# OpenGL through EGL: on Xvfb, SDL (Minecraft's window library) finds no GLX visual it accepts.
	env = dict(os.environ, SDL_VIDEO_FORCE_EGL='1')
	with log_path.open('w', encoding='utf-8') as log:
		client = subprocess.Popen(
			['xvfb-run', '-a', '-s', '-screen 0 1280x720x24', './gradlew', f':{loader}:runSmokeClient', '--console=plain'],
			cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True,
		)
	started = time.time()
	joined = running = False
	try:
		while time.time() - started < START_TIMEOUT_S and client.poll() is None:
			text = log_path.read_text(encoding='utf-8', errors='replace')
			running = MOD_RUNNING.search(text) is not None
			joined = re.search(r'\S+ joined the game', text) is not None
			if running and joined:
				break
			# Without any graphics backend the client waits on an error screen instead of exiting.
			if 'Failed to create backend OpenGL' in text and 'Failed to create backend Vulkan' in text:
				print('        the client found no graphics backend, see ' + str(log_path))
				break
			time.sleep(2)
		check(running, f'the mod runs on the integrated server ({time.time() - started:.0f} s)')
		if check(joined, 'the player is in the world'):
			# A few seconds of play: ticks, chunk loading and saving with the mod.
			time.sleep(15)
			check(client.poll() is None, 'the client is still running')
	finally:
		for pid in processes_in(folder):
			try:
				os.kill(pid, signal.SIGTERM)
			except ProcessLookupError:
				pass
		try:
			client.wait(timeout=60)
		except subprocess.TimeoutExpired:
			os.killpg(client.pid, signal.SIGKILL)
			client.wait()

	print('Client log', flush=True)
	# Only log entries ("[time] [thread/LEVEL] ..."): stack traces of unrelated warnings (e.g. the narrator's missing
	# libflite) print the whole classpath, mixin library and mod folders included.
	log = [line for line in log_path.read_text(encoding='utf-8', errors='replace').splitlines() if line.startswith('[')]
	problems = mod_problems(log)
	for line in problems[:20]:
		print('        ' + line[:300])
	check(not problems, f'no errors or warnings from the mod or mixins ({len(problems)} found)')

	print()
	if failures:
		print(f'{len(failures)} check(s) failed on {loader}; full log: {log_path}')
		sys.exit(1)
	print(f'All singleplayer checks passed on {loader}.')


if __name__ == '__main__':
	main()
