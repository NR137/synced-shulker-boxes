package de.nr137.syncedshulkerboxes.neoforge;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import de.nr137.syncedshulkerboxes.Permission;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

final class NeoForgePlatform implements de.nr137.syncedshulkerboxes.Platform {
	/** NeoForge's permission nodes, synced_shulker_boxes.&lt;node&gt;; without a permission mod the config decides. */
	private final Map<Permission, PermissionNode<Boolean>> nodes = new EnumMap<>(Permission.class);

	NeoForgePlatform() {
		for (Permission permission : Permission.values()) {
			nodes.put(permission, new PermissionNode<>(SyncedShulkerBoxes.MOD_ID, permission.node(), PermissionTypes.BOOLEAN,
				(player, uuid, context) -> player != null && (SyncedShulkerBoxes.config().forEveryone(permission)
					|| player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))));
		}
	}

	PermissionNode<?>[] nodes() {
		return nodes.values().toArray(PermissionNode<?>[]::new);
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public String modVersion() {
		return version(SyncedShulkerBoxes.MOD_ID);
	}

	@Override
	public String minecraftVersion() {
		return version("minecraft");
	}

	@Override
	public boolean hasPermission(ServerPlayer player, Permission permission, boolean forEveryone) {
		return PermissionAPI.getPermission(player, nodes.get(permission));
	}

	private static String version(String modId) {
		return ModList.get()
			.getModContainerById(modId)
			.map(container -> container.getModInfo().getVersion().toString())
			.orElse("unknown");
	}
}
