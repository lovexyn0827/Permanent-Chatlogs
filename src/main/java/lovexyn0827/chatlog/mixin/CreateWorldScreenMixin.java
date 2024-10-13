package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import lovexyn0827.chatlog.Session;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.world.level.LevelInfo;

@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
	@Shadow
	WorldCreator worldCreator;
	
	@Inject(method = "createLevelInfo", at = @At("HEAD"))
	private void onLevelCreation(boolean bl, CallbackInfoReturnable<LevelInfo> cir) {
		Session.current = new Session(this.worldCreator.getWorldName().trim(), false);
	}
}
