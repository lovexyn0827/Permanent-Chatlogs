package lovexyn0827.chatlog.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import lovexyn0827.chatlog.gui.NewEventMarkerScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
	@Shadow @Final MinecraftClient client;
	
	@Inject(method = "onKey", at = @At("RETURN"))
	private void handleKey(long window, int key, int scancode, int i, int j, CallbackInfo ci) {
		boolean isBeingPressed = i == GLFW.GLFW_PRESS;
		if(key == 'M' && Screen.hasControlDown() && isBeingPressed) {
			MinecraftClient.getInstance().setScreen(new NewEventMarkerScreen());
		}
	}
}
