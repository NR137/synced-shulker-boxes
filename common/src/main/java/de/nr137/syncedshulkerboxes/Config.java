package de.nr137.syncedshulkerboxes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/synced_shulker_boxes.json, written with the defaults at the first start. Read at every server start; changed
 * with /syncedshulkers config or, in singleplayer, the settings screen (both save it right away).
 *
 * @param maxGroupSize   how many boxes one group may have
 * @param linkCostLevels the anvil's cost in experience levels for each box linked
 * @param openingParticles whether the server sends the burst of particles when a synced box is opened
 * @param ambientParticles whether the server sends particles around placed synced boxes all the time, to every
 *                         player (on by default, so players without the mod see that a box is synced); clients with
 *                         the mod hide them and decide with their own setting (off by default: they have the glint)
 * @param permissions    who has each permission without a permission mod: "everyone" or "operators"
 */
public record Config(int maxGroupSize, int linkCostLevels, boolean openingParticles, boolean ambientParticles,
		Map<String, String> permissions) {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Limits: a group needs two boxes; from 40 levels on, the anvil says "Too Expensive!" (a new group costs twice). */
	public static final int MIN_GROUP_SIZE = 2;
	public static final int MAX_GROUP_SIZE = 64;
	public static final int MAX_LINK_COST = 19;

	public static Config defaults() {
		Map<String, String> permissions = new LinkedHashMap<>();
		for (Permission permission : Permission.values()) {
			permissions.put(permission.node(), permission.defaultForEveryone() ? "everyone" : "operators");
		}
		return new Config(8, 5, true, true, permissions);
	}

	public static Config load(Path file) {
		Config defaults = defaults();
		if (!Files.isRegularFile(file)) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(defaults) + "\n");
			} catch (IOException e) {
				SyncedShulkerBoxes.LOGGER.warn("Can't write the default config to {}: {}", file, e.toString());
			}
			return defaults;
		}
		try {
			// Options missing from the file (e.g. added in a newer version) keep their defaults, instead of Gson's
			// false and 0; the file is written again with all of them.
			com.google.gson.JsonObject merged = GSON.toJsonTree(defaults).getAsJsonObject();
			com.google.gson.JsonObject read = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			for (var entry : read.entrySet()) {
				if (entry.getKey().equals("permissions") && entry.getValue().isJsonObject()) {
					entry.getValue().getAsJsonObject().entrySet()
						.forEach(node -> merged.getAsJsonObject("permissions").add(node.getKey(), node.getValue()));
				} else if (merged.has(entry.getKey())) {
					merged.add(entry.getKey(), entry.getValue());
				}
			}
			Config config = GSON.fromJson(merged, Config.class).clamped();
			if (!merged.equals(read)) {
				config.save(file);
			}
			return config;
		} catch (IOException | JsonParseException | IllegalStateException e) {
			SyncedShulkerBoxes.LOGGER.error("Can't read {}, using the defaults: {}", file, e.toString());
			return defaults;
		}
	}

	private Config clamped() {
		return new Config(Math.clamp(maxGroupSize, MIN_GROUP_SIZE, MAX_GROUP_SIZE), Math.clamp(linkCostLevels, 0, MAX_LINK_COST),
			openingParticles, ambientParticles, permissions);
	}

	public Config withMaxGroupSize(int value) {
		return new Config(value, linkCostLevels, openingParticles, ambientParticles, permissions).clamped();
	}

	public Config withLinkCostLevels(int value) {
		return new Config(maxGroupSize, value, openingParticles, ambientParticles, permissions).clamped();
	}

	public Config withOpeningParticles(boolean value) {
		return new Config(maxGroupSize, linkCostLevels, value, ambientParticles, permissions);
	}

	public Config withAmbientParticles(boolean value) {
		return new Config(maxGroupSize, linkCostLevels, openingParticles, value, permissions);
	}

	public Config withPermission(Permission permission, boolean forEveryone) {
		Map<String, String> changed = new LinkedHashMap<>(permissions);
		changed.put(permission.node(), forEveryone ? "everyone" : "operators");
		return new Config(maxGroupSize, linkCostLevels, openingParticles, ambientParticles, changed);
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this) + "\n");
		} catch (IOException e) {
			SyncedShulkerBoxes.LOGGER.warn("Can't save {}: {}", file, e.toString());
		}
	}

	/** Whether everyone has a permission when no permission mod decides (otherwise operators only). */
	public boolean forEveryone(Permission permission) {
		String value = permissions.getOrDefault(permission.node(), permission.defaultForEveryone() ? "everyone" : "operators");
		return !"operators".equalsIgnoreCase(value);
	}
}
