package de.nr137.syncedshulkerboxes;

import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;

/** What only the loader knows; fabric/ and neoforge/ each implement it. */
public interface Platform {
	Path configDir();

	String modVersion();

	String minecraftVersion();

	/**
	 * Asks the loader's permission API; without a permission mod: everyone if {@code forEveryone}, otherwise
	 * operators (permission level 2, like most commands).
	 */
	boolean hasPermission(ServerPlayer player, Permission permission, boolean forEveryone);
}
