package lovexyn0827.chatlog.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.gui.NewEventMarkerScreen;
import lovexyn0827.chatlog.i18n.I18N;
import lovexyn0827.chatlog.session.Session;
import lovexyn0827.chatlog.session.SessionRecorder;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
	@Shadow @Final MinecraftClient client;
	
	@Inject(method = "onKey", at = @At("RETURN"))
	private void handleKey(long window, int action, KeyInput input, CallbackInfo ci) {
		boolean isBeingPressed = action == GLFW.GLFW_PRESS;
		boolean ctrlDown = input.hasCtrlOrCmd();
		boolean altDown = input.hasAlt();
		if (input.key() == 'M' && ctrlDown && isBeingPressed && SessionRecorder.current() != null) {
			if (altDown) {
				Text title = I18N.translateAsText("gui.marker.title");
				Session.Event event = new Session.Event(title, 
						System.currentTimeMillis(), DyeColor.RED.getSignColor());
				SessionRecorder.current().addEvent(event);
				this.client.inGameHud.setOverlayMessage(title, true);
				return;
			}
			
			MinecraftClient.getInstance().setScreen(new NewEventMarkerScreen());
		}
	}
}
