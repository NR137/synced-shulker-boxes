package de.nr137.syncedshulkerboxes.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Boxes;
import de.nr137.syncedshulkerboxes.client.ClientSettings;

/**
 * Only on clients with the mod, which decide themselves what they see of synced boxes (ClientSettings):
 * <ul>
 * <li>with "Opening particles" off, the server's opening burst (trails starting in the middle of a synced box,
 * Boxes.showOpening) isn't shown;</li>
 * <li>the server's always-on particles (Boxes.ambient, if the server sends them) are never shown: this client draws
 * its own if "Particles always" is on (AmbientParticles).</li>
 * </ul>
 * Everything else stays as it is.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "handleParticleEvent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
		shift = At.Shift.AFTER), cancellable = true)
	private void syncedShulkerBoxes$hideOpening(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		boolean opening = packet.particle() instanceof TrailParticleOption && !ClientSettings.openingParticles();
		boolean ambient = packet.particle() instanceof DustColorTransitionOptions;
		if (!opening && !ambient) {
			return;
		}
		Vec3 at = new Vec3(packet.x(), packet.y(), packet.z());
		BlockPos pos = BlockPos.containing(Vec3.atCenterOf(BlockPos.containing(at)).equals(at) ? at : at.subtract(0.0, 0.2, 0.0));
		Vec3 expected = opening ? Vec3.atCenterOf(pos) : Boxes.ambientOrigin(pos);
		var level = Minecraft.getInstance().level;
		if (level != null && expected.distanceToSqr(at) < 1.0E-4
				&& level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box && Boxes.isSynced(box)) {
			ci.cancel();
		}
	}
}
