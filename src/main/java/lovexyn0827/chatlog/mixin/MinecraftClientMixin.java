package lovexyn0827.chatlog.mixin;

import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.Session;
import net.minecraft.client.MinecraftClient;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(
			method = "createWorld", 
			at = @At(value = "HEAD")
	)
	private void onCreateWorld(String worldName, LevelInfo levelInfo, DynamicRegistryManager.Impl registryTracker, 
			GeneratorOptions generatorOptions, CallbackInfo ci) {
		Session.current = new Session(worldName);
	}
}