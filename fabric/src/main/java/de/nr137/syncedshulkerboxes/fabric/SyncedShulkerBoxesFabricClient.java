package de.nr137.syncedshulkerboxes.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import de.nr137.syncedshulkerboxes.client.ClientCommand;

/** Only on clients with the mod installed: the glint setting's client command (the glint itself is in mixins). */
public class SyncedShulkerBoxesFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
			dispatcher.register(ClientCommand.build((source, message) -> source.sendFeedback(message))));
	}
}
