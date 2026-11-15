package de.nr137.syncedshulkerboxes.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import de.nr137.syncedshulkerboxes.Config;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

/**
 * The settings, opened from Mod Menu (Fabric) or the mod list (NeoForge): this client's own (ClientSettings) and, in
 * singleplayer, the world's (Config), which there run on this computer. On a multiplayer server its options belong to
 * the server: admins change them with /syncedshulkers config (the client can't know who may).
 */
public final class SettingsScreen extends Screen {
	private final @Nullable Screen parent;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Synced Shulker Boxes"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int y = height / 2 - 100;
		addTitle(title, center, y);
		y += 24;
		addTitle(Component.literal("This client"), center, y);
		y += 14;
		addRenderableWidget(CycleButton.onOffBuilder(ClientSettings.glint())
			.create(center - 155, y, 310, 20, Component.literal("Glint on synced boxes"), (button, value) -> ClientSettings.setGlint(value)));
		y += 24;
		addRenderableWidget(CycleButton.onOffBuilder(ClientSettings.openingParticles()).create(center - 155, y, 150, 20,
			Component.literal("Opening particles"), (button, value) -> ClientSettings.setOpeningParticles(value)));
		addRenderableWidget(CycleButton.onOffBuilder(ClientSettings.ambientParticles()).create(center + 5, y, 150, 20,
			Component.literal("Particles always"), (button, value) -> ClientSettings.setAmbientParticles(value)));
		y += 30;
		if (minecraft.hasSingleplayerServer()) {
			addTitle(Component.literal("This world (singleplayer)"), center, y);
			y += 14;
			Config config = SyncedShulkerBoxes.config();
			addRenderableWidget(new IntSlider(center - 155, y, 150, "Boxes per group", Config.MIN_GROUP_SIZE, Config.MAX_GROUP_SIZE,
				config.maxGroupSize(), value -> SyncedShulkerBoxes.updateConfig(c -> c.withMaxGroupSize(value))));
			addRenderableWidget(new IntSlider(center + 5, y, 150, "Levels per box", 0, Config.MAX_LINK_COST,
				config.linkCostLevels(), value -> SyncedShulkerBoxes.updateConfig(c -> c.withLinkCostLevels(value))));
			y += 24;
			addRenderableWidget(CycleButton.onOffBuilder(config.openingParticles()).create(center - 155, y, 150, 20,
				Component.literal("Send opening particles"),
				(button, value) -> SyncedShulkerBoxes.updateConfig(c -> c.withOpeningParticles(value))));
			addRenderableWidget(CycleButton.onOffBuilder(config.ambientParticles()).create(center + 5, y, 150, 20,
				Component.literal("Send particles always"),
				(button, value) -> SyncedShulkerBoxes.updateConfig(c -> c.withAmbientParticles(value))));
			y += 30;
		} else {
			addTitle(Component.literal("Server options: /syncedshulkers config (admins)"), center, y);
			y += 24;
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(center - 100, y, 200, 20).build());
	}

	private void addTitle(Component text, int center, int y) {
		addRenderableWidget(new StringWidget(center - font.width(text) / 2, y, font.width(text), 12, text, font));
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
