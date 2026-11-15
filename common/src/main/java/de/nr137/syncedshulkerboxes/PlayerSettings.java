package de.nr137.syncedshulkerboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What each player wants the server to send them (/syncedshulkers particles), so players without the mod can turn
 * the particles off too: saved as {@code <world>/data/synced_shulker_boxes/players.dat}, which vanilla ignores. Only
 * players who changed something are in it.
 */
public final class PlayerSettings extends SavedData {
	/** One player's choices; both on unless changed. */
	public record Choices(boolean openingParticles, boolean ambientParticles) {
		static final Choices DEFAULT = new Choices(true, true);
		static final Codec<Choices> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("opening_particles", true).forGetter(Choices::openingParticles),
			Codec.BOOL.optionalFieldOf("ambient_particles", true).forGetter(Choices::ambientParticles)
		).apply(instance, Choices::new));
	}

	private static final Codec<PlayerSettings> CODEC = Codec.unboundedMap(Codec.STRING, Choices.CODEC)
		.xmap(PlayerSettings::new, settings -> settings.choices);

	public static final SavedDataType<PlayerSettings> TYPE = new SavedDataType<>(SyncedShulkerBoxes.id("players"),
		PlayerSettings::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, Choices> choices;

	public PlayerSettings() {
		this(Map.of());
	}

	private PlayerSettings(Map<String, Choices> choices) {
		this.choices = new HashMap<>(choices);
	}

	public static PlayerSettings get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Choices of(UUID player) {
		return choices.getOrDefault(player.toString(), Choices.DEFAULT);
	}

	public void set(UUID player, Choices changed) {
		if (changed.equals(Choices.DEFAULT)) {
			choices.remove(player.toString());
		} else {
			choices.put(player.toString(), changed);
		}
		setDirty();
	}
}
