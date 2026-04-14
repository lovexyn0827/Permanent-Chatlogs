package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.session.SessionRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.SaveLoader;
import net.minecraft.world.level.storage.LevelStorage;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(
			method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", 
			at = @At(value = "HEAD")
	)
	private void onDisconnected(CallbackInfo ci) {
		SessionRecorder.end(false);
	}
	
	@Inject(method = "stop", at = @At(value = "INVOKE", target = "java/lang/System.exit(I)V"))
	private void onStop(CallbackInfo ci) {
		SessionRecorder.end(true);
	}
	
	@Inject(
			method= "startIntegratedServer", 
			at = @At(
					value = "INVOKE", 
					target = "net/minecraft/client/MinecraftClient.disconnect()V", 
					shift = At.Shift.AFTER
			)
	)
	private void onStartSingleplayer(LevelStorage.Session session, ResourcePackManager dataPackManager, 
			SaveLoader saveLoader, boolean newWorld, CallbackInfo ci) {
		SessionRecorder.start(session.getDirectoryName(), false);
	}
}