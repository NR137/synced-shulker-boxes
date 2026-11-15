package de.nr137.syncedshulkerboxes.mixin;

import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

/**
 * Our recipes never go into a player's recipe book: a vanilla server would find ids there it doesn't know after the
 * mod is removed.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@ModifyVariable(method = "awardRecipes", at = @At("HEAD"), argsOnly = true)
	private Collection<RecipeHolder<?>> syncedShulkerBoxes$notOurs(Collection<RecipeHolder<?>> recipes) {
		if (recipes.stream().noneMatch(recipe -> recipe.id().identifier().getNamespace().equals(SyncedShulkerBoxes.MOD_ID))) {
			return recipes;
		}
		return recipes.stream()
			.filter(recipe -> !recipe.id().identifier().getNamespace().equals(SyncedShulkerBoxes.MOD_ID))
			.toList();
	}
}
