package de.nr137.syncedshulkerboxes.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;
import de.nr137.syncedshulkerboxes.client.ClientCommand;
import de.nr137.syncedshulkerboxes.client.SettingsScreen;

/** Only on clients with the mod installed: the settings screen in the mod list and the glint setting's client command. */
@Mod(value = SyncedShulkerBoxes.MOD_ID, dist = Dist.CLIENT)
public class SyncedShulkerBoxesNeoForgeClient {
	public SyncedShulkerBoxesNeoForgeClient(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event ->
			event.getDispatcher().register(ClientCommand.build((source, message) -> source.sendSuccess(() -> message, false))));
	}
}
