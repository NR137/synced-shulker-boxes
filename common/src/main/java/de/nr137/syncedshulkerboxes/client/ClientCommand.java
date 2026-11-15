package de.nr137.syncedshulkerboxes.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;

/**
 * /syncedshulkersclient [glint|openingParticles|ambientParticles on|off]: this client's settings (ClientSettings). A
 * client command: it works on any server and needs no permission. Each loader registers it with its own source.
 */
public final class ClientCommand {
	private ClientCommand() {
	}

	public static <S> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> feedback) {
		return LiteralArgumentBuilder.<S>literal("syncedshulkersclient")
			.executes(context -> {
				feedback.accept(context.getSource(), state());
				return 1;
			})
			.then(toggle("glint", ClientSettings::setGlint, feedback))
			.then(toggle("openingParticles", ClientSettings::setOpeningParticles, feedback))
			.then(toggle("ambientParticles", ClientSettings::setAmbientParticles, feedback));
	}

	private static <S> LiteralArgumentBuilder<S> toggle(String name, java.util.function.Consumer<Boolean> set,
			BiConsumer<S, Component> feedback) {
		return LiteralArgumentBuilder.<S>literal(name)
			.then(LiteralArgumentBuilder.<S>literal("on").executes(context -> {
				set.accept(true);
				feedback.accept(context.getSource(), state());
				return 1;
			}))
			.then(LiteralArgumentBuilder.<S>literal("off").executes(context -> {
				set.accept(false);
				feedback.accept(context.getSource(), state());
				return 1;
			}));
	}

	private static Component state() {
		return Component.literal("Synced Shulker Boxes on this client: glint " + onOff(ClientSettings.glint())
			+ ", openingParticles " + onOff(ClientSettings.openingParticles())
			+ ", ambientParticles " + onOff(ClientSettings.ambientParticles()));
	}

	private static String onOff(boolean value) {
		return value ? "on" : "off";
	}
}
