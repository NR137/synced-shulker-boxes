"""The official Minecraft client, without any mod, started like the launcher does but offline and on a virtual screen.

The smoke test uses it to join the modded server as a player without the mod (smoke_test.py, check_vanilla_client).
Downloads the client JAR, its libraries and the game's assets except sounds and music (not needed, and most of the
size) from Mojang into the given cache folder once. Linux only (xvfb-run, software OpenGL; both in the dev image).

Usage on its own: python3 scripts/smoke_test/vanilla_client.py <version> <host:port> [name]
"""

import hashlib
import json
import os
import platform
import re
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
RESOURCES = 'https://resources.download.minecraft.net'
USER_AGENT = 'mod smoke test'
# Asset folders the test doesn't need: sounds and music are most of the download.
SKIPPED_ASSETS = re.compile(r'^minecraft/(sounds|music|resourcepacks)/|\.ogg$')
# Client log lines that mean the connection or the server's data went wrong (with WARN or ERROR).
NETWORK_PROBLEM = re.compile(
	r'disconnect|lost connection|packet|protocol|decod|registry|registries|unknown (custom|payload)|'
	r'failed to (handle|read|parse|load)|tag|recipe|component|codec|datapack|data pack|invalid',
	re.IGNORECASE,
)
# Warnings an offline client on a virtual screen always logs, unrelated to the server.
OFFLINE_NOISE = re.compile(
	r'sound|audio|OpenAL|narrator|flite|realms|telemetry|profile key|player certificates|authentication|'
	r'session|user properties|social interactions|Unable to fetch|texture|font|shader|GLFW|clipboard|'
	r'Failed to fetch|Couldn\'t fetch|Missing sound',
	re.IGNORECASE,
)


def fetch(url: str) -> bytes:
	request = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
	for attempt in range(3):
		try:
			with urllib.request.urlopen(request, timeout=120) as response:
				return response.read()
		except OSError:
			if attempt == 2:
				raise
			time.sleep(2)
	raise AssertionError


def save(url: str, target: Path) -> Path:
	if target.is_file():
		return target
	target.parent.mkdir(parents=True, exist_ok=True)
	partial = target.with_name(target.name + '.part')
	partial.write_bytes(fetch(url))
	partial.replace(target)
	return target


def version_json(version: str) -> dict:
	manifest = json.loads(fetch(MANIFEST))
	entry = next((v for v in manifest['versions'] if v['id'] == version), None)
	if entry is None:
		sys.exit(f'Minecraft {version} is not in Mojang\'s version manifest')
	return json.loads(fetch(entry['url']))


def arch_matches(arch: str) -> bool:
	machine = platform.machine().lower()
	return {'x86': machine in ('i386', 'i686', 'x86'), 'arm64': machine in ('aarch64', 'arm64')}.get(arch, arch == machine)


def offline_uuid(name: str) -> str:
	"""The UUID the server gives an offline player (Java's UUID.nameUUIDFromBytes), without dashes."""
	digest = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode()).digest())
	digest[6] = digest[6] & 0x0f | 0x30
	digest[8] = digest[8] & 0x3f | 0x80
	return digest.hex()


def allowed(rules: list | None) -> bool:
	"""The launcher's rules for libraries and arguments, for Linux and without optional features."""
	if not rules:
		return True
	result = False
	for rule in rules:
		applies = True
		system = rule.get('os', {})
		if 'name' in system and system['name'] != 'linux':
			applies = False
		if 'arch' in system and not arch_matches(system['arch']):
			applies = False
		if rule.get('features'):
			applies = False
		if applies:
			result = rule['action'] == 'allow'
	return result


def wanted_native(name: str) -> bool:
	"""Native libraries come as one library per platform; keep only this machine's."""
	classifier = name.split(':')[3] if name.count(':') >= 3 else ''
	if not classifier.startswith('natives-'):
		return True
	if not classifier.startswith('natives-linux'):
		return False
	is_arm = platform.machine().lower() in ('aarch64', 'arm64')
	return ('arm64' in classifier or 'aarch' in classifier) == is_arm


def prepare(cache: Path, version: str) -> tuple[dict, list[Path], Path]:
	"""The version's metadata, the classpath and the natives folder; downloads what is missing."""
	info = version_json(version)
	libraries = cache / 'libraries'
	classpath = []
	jobs = []
	for library in info['libraries']:
		artifact = library.get('downloads', {}).get('artifact')
		if not artifact or not allowed(library.get('rules')) or not wanted_native(library['name']):
			continue
		target = libraries / artifact['path']
		classpath.append(target)
		jobs.append((artifact['url'], target))
	client = cache / 'versions' / version / f'{version}.jar'
	jobs.append((info['downloads']['client']['url'], client))
	classpath.append(client)

	index_info = info['assetIndex']
	index_path = save(index_info['url'], cache / 'assets' / 'indexes' / f'{index_info["id"]}.json')
	for name, entry in json.loads(index_path.read_text())['objects'].items():
		if SKIPPED_ASSETS.search(name):
			continue
		digest = entry['hash']
		jobs.append((f'{RESOURCES}/{digest[:2]}/{digest}', cache / 'assets' / 'objects' / digest[:2] / digest))

	missing = [(url, target) for url, target in jobs if not target.is_file()]
	if missing:
		print(f'Downloading {len(missing)} files of the vanilla client {version}', flush=True)
		with ThreadPoolExecutor(max_workers=16) as pool:
			list(pool.map(lambda job: save(*job), missing))

	natives = cache / 'natives' / version
	if not natives.is_dir():
		natives.mkdir(parents=True)
		for jar in classpath:
			if 'natives-linux' in jar.name:
				with zipfile.ZipFile(jar) as archive:
					for member in archive.namelist():
						if member.endswith('.so'):
							(natives / Path(member).name).write_bytes(archive.read(member))
	return info, classpath, natives


def arguments(entries: list, values: dict) -> list[str]:
	result = []
	for entry in entries:
		if isinstance(entry, dict):
			if not allowed(entry.get('rules')):
				continue
			entry = entry['value']
		for part in entry if isinstance(entry, list) else [entry]:
			result.append(re.sub(r'\$\{(\w+)\}', lambda match: values.get(match.group(1), ''), part))
	return result


class Client:
	def __init__(self, process: subprocess.Popen, log_path: Path):
		self.process = process
		self.log_path = log_path

	def stop(self) -> None:
		if self.process.poll() is None:
			os.killpg(self.process.pid, signal.SIGTERM)
			try:
				self.process.wait(timeout=30)
			except subprocess.TimeoutExpired:
				os.killpg(self.process.pid, signal.SIGKILL)
				self.process.wait()

	def problems(self) -> list[str]:
		"""WARN and ERROR lines about the connection or the server's data."""
		log = self.log_path.read_text(encoding='utf-8', errors='replace').splitlines()
		return [
			line for line in log
			if re.search(r'/(WARN|ERROR)\]', line) and NETWORK_PROBLEM.search(line) and not OFFLINE_NOISE.search(line)
		]


def start(cache: Path, version: str, name: str, server: str, display: str | None = None) -> Client:
	"""
	Starts the client, which connects to `server` (host:port) right away as the offline player `name`. With `display`
	(e.g. ":99", an X server already running) it uses that screen instead of a virtual one of its own, so the screen
	can be captured.
	"""
	info, classpath, natives = prepare(cache, version)
	game_dir = cache / 'game'
	if game_dir.is_dir():
		for entry in ('logs', 'crash-reports', 'options.txt', 'servers.dat'):
			path = game_dir / entry
			if path.is_dir():
				shutil.rmtree(path)
			elif path.exists():
				path.unlink()
	game_dir.mkdir(parents=True, exist_ok=True)
	# No first-start screens, no sound, and no pause when the window has no focus.
	(game_dir / 'options.txt').write_text('\n'.join([
		'onboardAccessibility:false',
		'skipMultiplayerWarning:true',
		'joinedFirstServer:true',
		'tutorialStep:none',
		'pauseOnLostFocus:false',
		'renderDistance:2',
		'soundCategory_master:0.0',
		'narrator:0',
	]) + '\n')
	values = {
		'auth_player_name': name,
		'version_name': version,
		'game_directory': str(game_dir),
		'assets_root': str(cache / 'assets'),
		'assets_index_name': info['assetIndex']['id'],
		'auth_uuid': offline_uuid(name),
		'auth_access_token': '0',
		'clientid': '',
		'auth_xuid': '',
		'user_type': 'legacy',
		'version_type': 'release',
		'natives_directory': str(natives),
		'launcher_name': 'smoke-test',
		'launcher_version': '1',
		'classpath': os.pathsep.join(str(path) for path in classpath),
		'classpath_separator': os.pathsep,
		'library_directory': str(cache / 'libraries'),
	}
	jvm = arguments(info['arguments']['jvm'], values)
	game = arguments(info['arguments']['game'], values)
	java_home = os.environ.get('JAVA_HOME')
	java = str(Path(java_home) / 'bin' / 'java') if java_home else 'java'
	command = [java, '-Xmx2G', *jvm, info['mainClass'], *game, '--quickPlayMultiplayer', server]
	log_path = game_dir / 'client.log'
	# OpenGL through EGL: on Xvfb, SDL (Minecraft's window library) finds no GLX visual it accepts.
	env = dict(os.environ, SDL_VIDEO_FORCE_EGL='1')
	if display:
		env['DISPLAY'] = display
	else:
		command = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24', *command]
	with log_path.open('w', encoding='utf-8') as log:
		process = subprocess.Popen(command, cwd=game_dir, env=env, stdout=log, stderr=subprocess.STDOUT,
			start_new_session=True)
	print(f'Started the vanilla client {version} as {name} for {server}', flush=True)
	return Client(process, log_path)


if __name__ == '__main__':
	if len(sys.argv) not in (3, 4):
		sys.exit(__doc__)
	root = Path(__file__).resolve().parents[2]
	client = start(root / 'scripts' / '.cache' / 'smoke' / f'vanilla-client-{sys.argv[1]}', sys.argv[1],
		sys.argv[3] if len(sys.argv) == 4 else 'VanillaTester', sys.argv[2])
	try:
		client.process.wait()
	except KeyboardInterrupt:
		client.stop()
