package de.nr137.syncedshulkerboxes;

import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's permissions: synced_shulker_boxes.&lt;node&gt; for permission mods (LuckPerms and others, through
 * Fabric's or NeoForge's permission API). Without a permission mod, the config's "permissions" decide.
 */
public enum Permission {
	CRAFT("craft", true),
	LINK("link", true),
	OPEN("open", true),
	/** /syncedshulkers particles: what the server sends this player. */
	SETTINGS("settings", true),
	ADMIN("admin", false);

	private final String node;
	private final boolean defaultForEveryone;

	Permission(String node, boolean defaultForEveryone) {
		this.node = node;
		this.defaultForEveryone = defaultForEveryone;
	}

	public String node() {
		return node;
	}

	public boolean defaultForEveryone() {
		return defaultForEveryone;
	}

	public boolean check(ServerPlayer player) {
		return SyncedShulkerBoxes.platform().hasPermission(player, this, SyncedShulkerBoxes.config().forEveryone(this));
	}
}
