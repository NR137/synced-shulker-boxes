#!/usr/bin/env bash
# The project's build and test commands, in one place for you, compose.yaml, the CI workflows and the pre-commit hook.
# Usually run in the dev image (docker/Dockerfile): docker compose run --rm dev ./tasks.sh test. Inside the VS Code dev
# container, or with the tools of .tool-versions installed locally, run it directly: ./tasks.sh test
set -euo pipefail
cd "$(dirname "$0")"

minecraft_version() {
	sed -n 's/^minecraft_version=//p' gradle.properties
}

# What a change touches, by its files: "jar" when the mod JAR can change (a release), "tools" when only tests, scripts,
# the dev image or CI change (all tests, no release), "docs" when only texts and screenshots change (no tests needed).
# Prints the group of the biggest kind among the given files (one per line on stdin).
change_kind() {
	local kind=docs file
	while IFS= read -r file; do
		case "$file" in
			'') ;;
			*/src/test/* | */src/gametest/*) kind=tools ;;
			common/* | fabric/* | neoforge/* | build-logic/* | *.gradle | gradle.properties | gradle/* | gradlew* | \
				LICENSE | .tool-versions)
				echo jar
				return ;;
			*.md | screenshots/* | docs/*) ;;
			*) kind=tools ;;
		esac
	done
	echo "$kind"
}

# The files changed since the merge base with the given ref (the pull request's target branch).
changed_files() {
	git diff --name-only "$(git merge-base "$1" HEAD)" HEAD
}

# main only has released versions, so CHANGELOG.md's top section must be a release: mod_version with a date, and no
# Unreleased section. With a base ref (the pull request's target branch): a change that touches the mod JAR is a new
# release, so mod_version must differ from the base's.
check_changelog() {
	local version heading date previous base_version
	version=$(sed -n 's/^mod_version=//p' gradle.properties)
	if [ -n "${1:-}" ] && [ "$(changed_files "$1" | change_kind)" = jar ]; then
		base_version=$(git show "$(git merge-base "$1" HEAD):gradle.properties" | sed -n 's/^mod_version=//p')
		if [ "$version" = "$base_version" ]; then
			echo "This change touches the mod JAR, so it is a new release: raise mod_version (still $version) and add its CHANGELOG.md section" >&2
			return 1
		fi
	fi
	if grep -Eiq '^## \[unreleased\]' CHANGELOG.md; then
		echo "CHANGELOG.md has an Unreleased section; main only has released versions" >&2
		return 1
	fi
	heading=$(grep -m1 '^## \[' CHANGELOG.md || true)
	if [ "${heading%% - *}" != "## [$version]" ]; then
		echo "CHANGELOG.md's top section is '$heading', but mod_version in gradle.properties is $version" >&2
		return 1
	fi
	if ! [[ "$heading" =~ \ -\ ([0-9]{4}-[0-9]{2}-[0-9]{2})$ ]] || ! date -d "${BASH_REMATCH[1]}" > /dev/null 2>&1; then
		echo "Give the release a date in CHANGELOG.md: '$heading' should be '## [$version] - YYYY-MM-DD'" >&2
		return 1
	fi
	# Catches typos in the date (ISO dates compare as strings).
	date=${BASH_REMATCH[1]}
	if [[ "$date" > "$(date -u +%F)" ]]; then
		echo "CHANGELOG.md: $version's date $date is in the future (today is $(date -u +%F) UTC)" >&2
		return 1
	fi
	previous=$(grep '^## \[' CHANGELOG.md | sed -n '2s/.* - \([0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}\)$/\1/p')
	if [ -n "$previous" ] && [[ "$date" < "$previous" ]]; then
		echo "CHANGELOG.md: $version's date $date is before the previous release's ($previous)" >&2
		return 1
	fi
	echo "CHANGELOG.md: $heading"
}

# Minecraft's code (unobfuscated since 26.1) decompiled into scripts/.cache/minecraft-src/<version>/, a gitignored
# folder, with Vineflower from Maven Central. Only for reading while writing mixins; it is Mojang's code, so it must
# never go into git, the JAR or anything published. Takes a few minutes, once per Minecraft version.
VINEFLOWER_VERSION=1.12.0
decompile() {
	local version root target work jar
	version=$(minecraft_version)
	root=scripts/.cache/minecraft-src
	target="$root/$version"
	if [ -d "$target" ]; then
		echo "Already there: $target"
		return
	fi
	# A link to a shared folder outside the repo (one copy for several repos on one machine): the dev container only
	# sees the repo, so the link is broken in there.
	if [ -L "$root" ]; then
		echo "$root links to $(readlink "$root"), outside the repo, which is not there or not visible here (the dev" \
			"container only sees the repo). Run this outside the container, or remove the link to decompile into the repo." >&2
		return 1
	fi
	# Loom puts the game's merged client and server classes into the Gradle cache when it configures the build.
	./gradlew -q :fabric:help > /dev/null
	jar=$(find "$HOME/.gradle/caches/fabric-loom/$version" -name minecraft-merged.jar 2> /dev/null | head -1)
	if [ -z "$jar" ]; then
		echo "Found no minecraft-merged.jar for $version in the Gradle cache" >&2
		return 1
	fi
	work=scripts/.cache/decompile
	rm -rf "$work"
	mkdir -p "$work/classes"
	curl -fsSL -o "$work/vineflower.jar" \
		"https://repo1.maven.org/maven2/org/vineflower/vineflower/$VINEFLOWER_VERSION/vineflower-$VINEFLOWER_VERSION.jar"
	# Only the game's classes: the textures, sounds and data in the JAR are not needed, and with them Vineflower runs
	# out of memory without a word.
	(cd "$work/classes" && unzip -q "$jar" 'net/minecraft/*' 'com/mojang/*' && zip -qr ../classes.jar .)
	mkdir -p "$target.part"
	java -Xmx5G -jar "$work/vineflower.jar" --thread-count="$(nproc)" -log=WARN "$work/classes.jar" "$target.part/" \
		> "$work/vineflower.log" 2>&1
	mv "$target.part" "$target"
	rm -rf "$work"
	echo "Decompiled Minecraft $version into $target ($(find "$target" -name '*.java' | wc -l) files)"
}

task() {
	case "$1" in
		build) # Both JARs
			./gradlew build ;;
		test-java) # JUnit tests of common/
			./gradlew :common:test ;;
		changelog) # CHANGELOG.md's top section = mod_version, dated; with a base ref, a JAR change raises mod_version
			# Base ref: ./tasks.sh changelog origin/main
			check_changelog "${2:-}" ;;
		changes) # What the changes since a base ref touch: jar, tools or docs (./tasks.sh changes origin/main)
			changed_files "${2:?changes needs a base ref, e.g. origin/main}" | change_kind ;;
		versions) # Every version the project uses against the newest one (needs a network)
			python3 scripts/versions/check_versions.py ;;
		test) # The quick tests (for now only test-java)
			task test-java ;;
		gametest) # Mock player game tests on Fabric and NeoForge
			./gradlew :fabric:runGameTest :neoforge:runGameTestServer ;;
		smoke) # Built JARs on the official servers, a vanilla client joining, the world on vanilla; needs build first
			if [ -n "${2:-}" ]; then
				python3 scripts/smoke_test/smoke_test.py "$2"
			else
				python3 scripts/smoke_test/smoke_test.py fabric
				python3 scripts/smoke_test/smoke_test.py neoforge
			fi ;;
		client) # Singleplayer on each loader's dev client, on a virtual screen; needs smoke first (its world)
			if [ -n "${2:-}" ]; then
				python3 scripts/smoke_test/client_test.py "$2"
			else
				python3 scripts/smoke_test/client_test.py fabric
				python3 scripts/smoke_test/client_test.py neoforge
			fi ;;
		decompile) # Minecraft's code as Java sources, to read (never commit or publish it: it is Mojang's)
			decompile ;;
		all) # Everything CI runs
			task test
			task build
			task gametest
			task smoke
			task client ;;
		*)
			echo "Usage: ./tasks.sh <task> [loader or base ref]"
			grep -E '^\s+[a-z-]+\) #' "$0" | sed -E 's/^\s+([a-z-]+)\) # /  \1\t/'
			exit 1 ;;
	esac
}

task "${1:-help}" "${2:-}"
