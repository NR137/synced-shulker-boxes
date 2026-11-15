package de.nr137.syncedshulkerboxes.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import de.nr137.syncedshulkerboxes.Boxes;

/**
 * Only on clients with the mod, with "Particles always" on: specks in the box's colour, fading to white, float slowly
 * around every synced box in view, a few every fifth of a second. Drawn by this client alone, which can set their speed
 * (the server's, for vanilla clients, move at the game's fixed dust speed).
 */
public final class AmbientParticles {
	private static final long EVERY_NANOS = 200_000_000L;
	private static final Map<ShulkerBoxBlockEntity, Long> LAST = new WeakHashMap<>();

	private AmbientParticles() {
	}

	/** Called while the box is drawn (so only for boxes in view). */
	public static void maybeSpawn(ShulkerBoxBlockEntity box) {
		Level level = box.getLevel();
		if (level == null || !ClientSettings.ambientParticles()) {
			return;
		}
		long now = System.nanoTime();
		Long last = LAST.get(box);
		if (last != null && now - last < EVERY_NANOS) {
			return;
		}
		LAST.put(box, now);
		RandomSource random = level.getRandom();
		Vec3 center = Vec3.atCenterOf(box.getBlockPos());
		DustColorTransitionOptions speck = new DustColorTransitionOptions(Boxes.color(box), 0xFFFFFF, 1.4F);
		for (int i = 0; i < 3; i++) {
			Particle particle = Minecraft.getInstance().particleEngine.createParticle(speck, center.x + random.nextGaussian() * 0.4,
				center.y + 0.2 + random.nextGaussian() * 0.45, center.z + random.nextGaussian() * 0.4, 0.0, 0.0, 0.0);
			if (particle != null) {
				// Dust drifts up on its own at a fixed speed; these float slowly instead, and stay a little longer.
				particle.setParticleSpeed(random.nextGaussian() * 0.0015, 0.0025 + random.nextDouble() * 0.002,
					random.nextGaussian() * 0.0015);
				particle.setLifetime(40 + random.nextInt(30));
			}
		}
	}
}
