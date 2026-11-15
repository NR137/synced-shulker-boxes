package de.nr137.syncedshulkerboxes.fabric;

import java.nio.file.Path;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import de.nr137.syncedshulkerboxes.Permission;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

final class FabricPlatform implements de.nr137.syncedshulkerboxes.Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public String modVersion() {
		return version(SyncedShulkerBoxes.MOD_ID);
	}

	@Override
	public String minecraftVersion() {
		return version("minecraft");
	}

	/** Fabric API's permission API: the node is synced_shulker_boxes:&lt;node&gt;; permission mods answer it. */
	@Override
	public boolean hasPermission(ServerPlayer player, Permission permission, boolean forEveryone) {
		PermissionContextOwner owner = (PermissionContextOwner) player;
		if (forEveryone) {
			return owner.checkPermission(SyncedShulkerBoxes.id(permission.node()), true);
		}
		return owner.checkPermission(SyncedShulkerBoxes.id(permission.node()), PermissionLevel.GAMEMASTERS);
	}

	private static String version(String modId) {
		return FabricLoader.getInstance()
			.getModContainer(modId)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}
}
