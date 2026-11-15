"""Smoke test: the built mod JAR on a real Fabric or NeoForge server, then the same world on a vanilla server.

1. Sets up the official server for the versions in gradle.properties (with Fabric API on Fabric), puts the JAR from
   <loader>/build/libs/ into mods/ and starts it on a fresh flat world.
2. Runs this mod's scenario (scenario.py: with_mod), so the world has whatever the mod saves.
3. A vanilla client (vanilla_client.py, the official client without any mod) joins, stays a while and runs the
   scenario's with_client part: players without the mod must be able to play.
4. Stops the server and checks its log for errors and warnings from the mod or its mixins.
5. Opens a copy of that world on the official vanilla server (the mod removed) and runs the scenario's on_vanilla
   checks: the world must load, save and stop without errors or warnings, and keep what vanilla should keep.

Usage: python3 scripts/smoke_test/smoke_test.py fabric|neoforge [--no-client]
  --no-client  skips step 3 (for a machine without xvfb-run; CI always runs it)
Needs: ./gradlew build first (the JARs), Java as in .tool-versions as `java` (or JAVA_HOME), xvfb-run for the client
(all in the dev image, docker/Dockerfile). Servers, the client and downloads are kept in scripts/.cache/; worlds, config
and mods are written fresh on every run. Exits with 1 if a check fails.
"""

import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).parent))
import scenario  # noqa: E402
import vanilla_client  # noqa: E402

PROPERTIES = dict(
	line.split('=', 1)
	for line in (ROOT / 'gradle.properties').read_text().splitlines()
	if '=' in line and not line.lstrip().startswith('#')
)
MOD_ID = PROPERTIES['mod_id']
MOD_NAME = PROPERTIES['mod_name']
MOD_VERSION = PROPERTIES['mod_version']
MC_VERSION = PROPERTIES['minecraft_version']
ARCHIVES_NAME = PROPERTIES['archives_base_name']
FULL_VERSION = f'{MOD_VERSION}+{MC_VERSION}'
CACHE = ROOT / 'scripts' / '.cache' / 'smoke'
START_TIMEOUT_S = 900  # The first start downloads Minecraft and the loader's libraries.
# The line the mod logs once the server runs (SyncedShulkerBoxes.onServerStarted).
MOD_RUNNING = re.compile(rf'{re.escape(MOD_NAME)} \S+ is running')
CLIENT_PLAYER = 'VanillaTester'
# Every test server runs offline. The world remembers the data packs of the loader and the mod, which vanilla doesn't
# have and drops from the world's list: what removing the mod should do.
MISSING_PACK = re.compile(r'WARN\]: Missing data pack ')
EXPECTED_ON_VANILLA = re.compile(
	r'OFFLINE/INSECURE MODE|make no attempt to authenticate|hackers to connect with any username|"online-mode" to "true"|'
	+ MISSING_PACK.pattern)

failures: list[str] = []


def check(ok: bool, what: str) -> bool:
	print(('  ok    ' if ok else '  FAIL  ') + what, flush=True)
	if not ok:
		failures.append(what)
	return ok


def download(url: str, target: Path) -> Path:
	if target.is_file():
		return target
	target.parent.mkdir(parents=True, exist_ok=True)
	print('Downloading', url, flush=True)
	request = urllib.request.Request(url, headers={'User-Agent': f'{ARCHIVES_NAME} smoke test'})
	with urllib.request.urlopen(request, timeout=120) as response:
		data = response.read()
	partial = target.with_name(target.name + '.part')
	partial.write_bytes(data)
	partial.replace(target)
	return target


def free_port() -> int:
	with socket.socket() as probe:
		probe.bind(('127.0.0.1', 0))
		return probe.getsockname()[1]


def java() -> str:
	home = os.environ.get('JAVA_HOME')
	if home:
		return str(Path(home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
	return 'java'


# --- Server setup ---


def fabric_server(folder: Path) -> list[str]:
	installers = json.loads(urllib.request.urlopen('https://meta.fabricmc.net/v2/versions/installer', timeout=60).read())
	installer = next(entry['version'] for entry in installers if entry['stable'])
	loader = PROPERTIES['fabric_loader_version']
	launcher = download(
		f'https://meta.fabricmc.net/v2/versions/loader/{MC_VERSION}/{loader}/{installer}/server/jar',
		CACHE / 'downloads' / f'fabric-server-{MC_VERSION}-{loader}-{installer}.jar',
	)
	shutil.copy(launcher, folder / 'fabric-server-launch.jar')
	api = PROPERTIES['fabric_api_version']
	api_jar = download(
		'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/'
		f'{urllib.parse.quote(api)}/fabric-api-{urllib.parse.quote(api)}.jar',
		CACHE / 'downloads' / f'fabric-api-{api}.jar',
	)
	shutil.copy(api_jar, folder / 'mods' / api_jar.name)
	return ['-jar', 'fabric-server-launch.jar', 'nogui']


def neoforge_server(folder: Path) -> list[str]:
	version = PROPERTIES['neoforge_version']
	args_file = folder / 'libraries' / 'net' / 'neoforged' / 'neoforge' / version / (
		'win_args.txt' if os.name == 'nt' else 'unix_args.txt'
	)
	if not args_file.is_file():
		installer = download(
			f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar',
			CACHE / 'downloads' / f'neoforge-{version}-installer.jar',
		)
		print('Installing the NeoForge server', flush=True)
		subprocess.run([java(), '-jar', str(installer), '--install-server', str(folder)], check=True, cwd=folder,
			stdout=subprocess.DEVNULL)
	return ['@' + str(args_file.relative_to(folder)), 'nogui']


def test_mod(project: str, loader: str, mods: Path) -> None:
	"""Another mod the scenario wants on the server (scenario.TEST_MODS, e.g. Lithium): its newest release from Modrinth
	for this Minecraft version and loader. Only for testing, never part of the mod."""
	query = urllib.parse.urlencode({'game_versions': json.dumps([MC_VERSION]), 'loaders': json.dumps([loader])})
	request = urllib.request.Request(f'https://api.modrinth.com/v2/project/{project}/version?{query}',
		headers={'User-Agent': f'{ARCHIVES_NAME} smoke test'})
	versions = json.loads(urllib.request.urlopen(request, timeout=60).read())
	if not versions:
		sys.exit(f'{project} has no release for Minecraft {MC_VERSION} on {loader}')
	file = next(entry for entry in versions[0]['files'] if entry['primary'] or len(versions[0]['files']) == 1)
	jar = download(file['url'], CACHE / 'downloads' / file['filename'])
	shutil.copy(jar, mods / jar.name)
	print(f'With {project} {versions[0]["version_number"]} (test only)', flush=True)


def vanilla_server(folder: Path) -> list[str]:
	"""The official server JAR; it unpacks its libraries into the folder at the first start."""
	server = vanilla_client.version_json(MC_VERSION)['downloads']['server']
	jar = download(server['url'], CACHE / 'downloads' / f'minecraft-server-{MC_VERSION}.jar')
	shutil.copy(jar, folder / 'server.jar')
	return ['-jar', 'server.jar', 'nogui']


def fresh_folder(name: str) -> Path:
	"""A server folder with fresh state; downloaded server files and libraries stay."""
	folder = CACHE / name
	folder.mkdir(parents=True, exist_ok=True)
	for entry in ('world', 'config', 'mods', 'logs', 'crash-reports', 'usercache.json', 'server.properties',
			'smoke-test.log'):
		path = folder / entry
		if path.is_dir():
			shutil.rmtree(path)
		elif path.exists():
			path.unlink()
	(folder / 'eula.txt').write_text('eula=true\n')
	return folder


def write_properties(folder: Path, game_port: int) -> None:
	(folder / 'server.properties').write_text('\n'.join([
		f'server-port={game_port}',
		'server-ip=127.0.0.1',
		'online-mode=false',
		'white-list=false',
		'enforce-secure-profile=false',
		'level-type=minecraft\\:flat',
		'generate-structures=false',
		'spawn-protection=0',
		'view-distance=4',
		'simulation-distance=4',
		'max-players=4',
		'motd=Smoke test',
	]) + '\n')


def set_up(loader: str, game_port: int) -> tuple[Path, list[str]]:
	folder = fresh_folder(f'{loader}-{MC_VERSION}')
	(folder / 'mods').mkdir()
	jar = ROOT / loader / 'build' / 'libs' / f'{ARCHIVES_NAME}-{loader}-{FULL_VERSION}.jar'
	if not jar.is_file():
		sys.exit(f'{jar} is missing, run ./gradlew build first')
	check_jar_contents(jar)
	shutil.copy(jar, folder / 'mods' / jar.name)
	command = fabric_server(folder) if loader == 'fabric' else neoforge_server(folder)
	for project in getattr(scenario, 'TEST_MODS', []):
		test_mod(project, loader, folder / 'mods')
	write_properties(folder, game_port)
	scenario.configure(folder)
	return folder, command


def check_jar_contents(jar: Path) -> None:
	"""The JAR holds only our own files: no class, texture, sound, model or text of the game."""
	with zipfile.ZipFile(jar) as archive:
		names = archive.namelist()
	mojang = [name for name in names if re.match(r'(net/minecraft|com/mojang|assets/minecraft|assets/realms)/', name)
		or name.endswith(('.ogg', '.fsh', '.vsh'))]
	for name in mojang[:20]:
		print('        ' + name)
	check(not mojang, f'the JAR holds no files of the game ({len(mojang)} found in {jar.name})')


# --- Server process ---


class Server:
	"""A server process; its output is kept for the checks and appended to smoke-test.log."""

	def __init__(self, folder: Path, command: list[str], name: str):
		self.name = name
		self.output: list[str] = []
		self.lock = threading.Lock()
		self.process = subprocess.Popen(
			[java(), '-Xmx2G', *command], cwd=folder, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
			stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace',
		)
		self.log_file = (folder / 'smoke-test.log').open('a', encoding='utf-8')
		self.reader = threading.Thread(target=self._pump, daemon=True)
		self.reader.start()

	def _pump(self):
		for line in self.process.stdout:
			with self.lock:
				self.output.append(line.rstrip('\n'))
			self.log_file.write(line)
			self.log_file.flush()

	def wait_for(self, pattern, seconds: float, start: int = 0) -> str | None:
		"""The first output line from index `start` on that matches, waiting up to `seconds`; None if none."""
		regex = re.compile(pattern) if isinstance(pattern, str) else pattern
		deadline = time.time() + seconds
		index = start
		while True:
			with self.lock:
				lines = self.output[index:]
			for line in lines:
				if regex.search(line):
					return line
			index += len(lines)
			if time.time() > deadline or self.process.poll() is not None:
				return None
			time.sleep(0.2)

	def wait_ready(self, also=None) -> bool:
		started = time.time()
		done = self.wait_for(r'Done \(', START_TIMEOUT_S) is not None
		ready = done and (also is None or self.wait_for(also, 30) is not None)
		check(ready, f'the {self.name} server starts ({time.time() - started:.0f} s)')
		return ready

	def command(self, line: str) -> None:
		"""A console command, as typed into the server's console."""
		self.process.stdin.write(line + '\n')
		self.process.stdin.flush()

	def run(self, line: str, expect: str, what: str, seconds: float = 30) -> str | None:
		"""Runs a console command and checks that an output line after it matches `expect`."""
		with self.lock:
			start = len(self.output)
		self.command(line)
		answer = self.wait_for(expect, seconds, start)
		check(answer is not None, f'{what} (/{line})')
		return answer

	def stop(self) -> None:
		if self.process.poll() is None:
			self.command('stop')
			try:
				self.process.wait(timeout=120)
			except subprocess.TimeoutExpired:
				self.process.kill()
				self.process.wait()
		self.reader.join(timeout=10)
		self.log_file.close()
		check(self.process.returncode == 0, f'the {self.name} server stops cleanly (exit code {self.process.returncode})')


# --- Checks ---


def mod_problems(log: list[str]) -> list[str]:
	"""Errors and warnings from the mod or its mixins."""
	return [
		line for line in log
		if re.search(r'mixin', line, re.I) and re.search(r'error|fail|exception', line, re.I)
		or re.search(rf'(ERROR|WARN).*({re.escape(MOD_ID)}|{re.escape(MOD_NAME)}|de\.nr137\.)', line)
		or re.search(r'de\.nr137\.\S+(Exception|Error)', line)
	]


def check_log(log: list[str]) -> None:
	print('Server log', flush=True)
	problems = scenario.expected_problems(mod_problems(log))
	for line in problems[:20]:
		print('        ' + line[:300])
	check(not problems, f'no errors or warnings from the mod or mixins ({len(problems)} found)')


def check_vanilla_client(server: Server, game_port: int) -> None:
	"""Players without the mod: the official client joins, plays a moment and leaves without errors."""
	print('Vanilla client', flush=True)
	with server.lock:
		start = len(server.output)
	client = vanilla_client.start(CACHE / f'vanilla-client-{MC_VERSION}', MC_VERSION, CLIENT_PLAYER,
		f'127.0.0.1:{game_port}')
	try:
		answer = server.wait_for(rf'{CLIENT_PLAYER}.* (joined the game|lost connection)', START_TIMEOUT_S, start)
		if not check(answer is not None and 'joined the game' in answer,
				f'the vanilla client joins ({answer or "no answer"}; client log: {client.log_path})'):
			return
		# Time for the client to load the world and render it; a broken packet usually shows up here.
		time.sleep(10)
		server.command(f'op {CLIENT_PLAYER}')
		scenario.with_client(server, CLIENT_PLAYER, check)
		time.sleep(5)
		server.run('list', rf'{CLIENT_PLAYER}', 'the vanilla client is still connected')
		left = server.wait_for(rf'{CLIENT_PLAYER} (lost connection|left the game)', 0, start)
		check(left is None, f'the vanilla client was not disconnected ({left})')
	finally:
		client.stop()
	problems = client.problems()
	for line in problems[:20]:
		print('        ' + line[:300])
	check(not problems, f'no network or registry errors in the vanilla client\'s log ({len(problems)} found)')


def check_on_vanilla(loader: str, world: Path) -> None:
	"""The mod removed: the world on the official vanilla server."""
	print(f'The {loader} world on a vanilla server', flush=True)
	folder = fresh_folder(f'vanilla-{MC_VERSION}')
	command = vanilla_server(folder)
	write_properties(folder, free_port())
	shutil.copytree(world, folder / 'world', ignore=shutil.ignore_patterns('session.lock'))
	server = Server(folder, command, 'vanilla')
	try:
		if server.wait_ready():
			scenario.on_vanilla(server, check)
			server.run('save-all flush', r'Saved the game', 'the vanilla server saves the world')
	finally:
		server.stop()
	problems = [line for line in server.output if re.search(r'/(WARN|ERROR)\]', line)]
	problems = scenario.expected_vanilla_warnings([line for line in problems if not EXPECTED_ON_VANILLA.search(line)])
	for line in server.output:
		if MISSING_PACK.search(line):
			print('  note  ' + line[line.index(']: ') + 3:] + ' (a pack of the loader or mod; vanilla drops it)')
	for line in problems[:20]:
		print('        ' + line[:300])
	check(not problems, f'no errors or warnings from the vanilla server ({len(problems)} found)')


# --- Main ---


def main() -> None:
	args = sys.argv[1:]
	with_client = '--no-client' not in args
	args = [arg for arg in args if arg != '--no-client']
	if len(args) != 1 or args[0] not in ('fabric', 'neoforge'):
		sys.exit(__doc__)
	loader = args[0]
	game_port = free_port()
	folder, command = set_up(loader, game_port)
	print(f'Starting the {loader} server in {folder} (port {game_port})', flush=True)

	server = Server(folder, command, loader)
	try:
		if server.wait_ready(MOD_RUNNING):
			print('With the mod', flush=True)
			scenario.with_mod(server, check)
			if with_client:
				check_vanilla_client(server, game_port)
			server.run('save-all flush', r'Saved the game', 'the server saves the world')
	finally:
		server.stop()
	check_log(server.output)
	if (folder / 'world' / 'level.dat').is_file():
		check_on_vanilla(loader, folder / 'world')

	print()
	if failures:
		print(f'{len(failures)} check(s) failed on {loader}; logs: {CACHE}/*/smoke-test.log')
		sys.exit(1)
	print(f'All checks passed on {loader}.')


if __name__ == '__main__':
	main()
