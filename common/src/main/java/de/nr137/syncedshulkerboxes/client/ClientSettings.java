package de.nr137.syncedshulkerboxes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

/**
 * This client's own settings, in config/synced_shulker_boxes-client.json: what this player sees of synced boxes (the
 * glint, the particles when one is opened, particles around them all the time). Nothing else changes, on any server.
 * Changed with /syncedshulkersclient or the settings screen (Mod Menu on Fabric, the mod list on NeoForge).
 */
public final class ClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean loaded;
	private static final Settings DEFAULTS = new Settings(true, true, false);
	private static Settings settings = DEFAULTS;

	private record Settings(boolean glint, boolean openingParticles, boolean ambientParticles) {
	}

	private ClientSettings() {
	}

	private static Path file() {
		return SyncedShulkerBoxes.platform().configDir().resolve(SyncedShulkerBoxes.MOD_ID + "-client.json");
	}

	private static Settings settings() {
		if (!loaded) {
			loaded = true;
			try {
				if (Files.isRegularFile(file())) {
					// Settings missing from the file keep their defaults.
					com.google.gson.JsonObject merged = GSON.toJsonTree(DEFAULTS).getAsJsonObject();
					com.google.gson.JsonParser.parseString(Files.readString(file())).getAsJsonObject().entrySet()
						.forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
					settings = GSON.fromJson(merged, Settings.class);
				}
			} catch (IOException | JsonParseException | IllegalStateException e) {
				SyncedShulkerBoxes.LOGGER.warn("Can't read {}, using the defaults: {}", file(), e.toString());
			}
		}
		return settings;
	}

	public static boolean glint() {
		return settings().glint();
	}

	public static boolean openingParticles() {
		return settings().openingParticles();
	}

	public static boolean ambientParticles() {
		return settings().ambientParticles();
	}

	public static void setGlint(boolean value) {
		Settings current = settings();
		save(new Settings(value, current.openingParticles(), current.ambientParticles()));
	}

	public static void setOpeningParticles(boolean value) {
		Settings current = settings();
		save(new Settings(current.glint(), value, current.ambientParticles()));
	}

	public static void setAmbientParticles(boolean value) {
		Settings current = settings();
		save(new Settings(current.glint(), current.openingParticles(), value));
	}

	private static void save(Settings changed) {
		settings = changed;
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(settings) + "\n");
		} catch (IOException e) {
			SyncedShulkerBoxes.LOGGER.warn("Can't save {}: {}", file(), e.toString());
		}
	}
}
