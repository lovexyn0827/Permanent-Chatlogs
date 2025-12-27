package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import lovexyn0827.chatlog.gui.SessionListScreen;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.text.Text;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin extends Screen {
	protected GameMenuScreenMixin(Text title) {
		super(title);
	}

	@Inject(
			method = "initWidgets", 
			at = @At(
					value = "INVOKE", 
					target = "net/minecraft/client/gui/widget/GridWidget.refreshPositions()V"
			), 
			locals = LocalCapture.CAPTURE_FAILHARD
	)
	private void appendButtons(CallbackInfo ci, GridWidget gridWidget, GridWidget.Adder adder, Text text) {
		ButtonWidget chatlogBtn = ButtonWidget.builder(I18N.translateAsText("gui.chatlogs"), (btn) -> {
			this.client.setScreen(new SessionListScreen(client.currentScreen));
		}).width(204).build();
		adder.add(chatlogBtn, 2);
	}
}
