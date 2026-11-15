package de.nr137.syncedshulkerboxes.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

public class SyncedShulkerBoxesFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		SyncedShulkerBoxes.init(new FabricPlatform());
		ServerLifecycleEvents.SERVER_STARTING.register(SyncedShulkerBoxes::onServerStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(SyncedShulkerBoxes::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SyncedShulkerBoxes.onServerStopping());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SyncedShulkerBoxes.onServerStopped());
		ServerTickEvents.END_SERVER_TICK.register(SyncedShulkerBoxes::onServerTick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SyncedShulkerBoxes.registerCommands(dispatcher));
	}
}
