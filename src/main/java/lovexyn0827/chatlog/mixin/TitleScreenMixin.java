package lovexyn0827.chatlog.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.gui.SessionListScreen;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void onInit(CallbackInfo ci) {
		this.addButton(new ButtonWidget(this.width / 2 - 100, (this.height / 4 + 48) + 92 + 12, 98, 20, 
				I18N.translateAsText("gui.chatlogs"), (btn) -> this.client.openScreen(new SessionListScreen())));
		Session.tryRestoreUnsaved();
	}
}
