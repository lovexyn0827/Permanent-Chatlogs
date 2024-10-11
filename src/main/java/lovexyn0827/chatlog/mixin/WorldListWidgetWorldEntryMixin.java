package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.Session;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.world.level.storage.LevelSummary;

@Mixin(WorldListWidget.WorldEntry.class)
public abstract class WorldListWidgetWorldEntryMixin {
	@Shadow
	private @Final LevelSummary level;
	
	@Inject(method = "play", at = @At(value = "INVOKE", target = "net/minecraft/client/MinecraftClient"
			+ ".createIntegratedServerLoader()Lnet/minecraft/server/integrated/IntegratedServerLoader;"))
	private void onOpenWorld(CallbackInfo ci) {
		Session.current = new Session(this.level.getDisplayName(), false);
	}
}
