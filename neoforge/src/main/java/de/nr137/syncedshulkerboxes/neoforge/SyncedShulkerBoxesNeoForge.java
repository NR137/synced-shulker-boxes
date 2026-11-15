package de.nr137.syncedshulkerboxes.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

@Mod(SyncedShulkerBoxes.MOD_ID)
public class SyncedShulkerBoxesNeoForge {
	public SyncedShulkerBoxesNeoForge() {
		NeoForgePlatform platform = new NeoForgePlatform();
		SyncedShulkerBoxes.init(platform);
		NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> SyncedShulkerBoxes.onServerStarting(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> SyncedShulkerBoxes.onServerStarted(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> SyncedShulkerBoxes.onServerStopping());
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> SyncedShulkerBoxes.onServerStopped());
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> SyncedShulkerBoxes.onServerTick(event.getServer()));
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> SyncedShulkerBoxes.registerCommands(event.getDispatcher()));
		NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, event -> event.addNodes(platform.nodes()));
	}
}
