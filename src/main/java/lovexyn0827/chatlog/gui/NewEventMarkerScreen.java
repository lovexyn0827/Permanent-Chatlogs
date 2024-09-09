package lovexyn0827.chatlog.gui;

import com.google.common.collect.Lists;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

public class NewEventMarkerScreen extends Screen {
	private static final Text TITLE = I18N.translateAsText("gui.marker.title");
	private TextFieldWidget name;
	private CyclingButtonWidget<DyeColor> color;

	public NewEventMarkerScreen() {
		super(TITLE);
	}

	@SuppressWarnings("resource")
	@Override
	protected void init() {
		MinecraftClient mc = MinecraftClient.getInstance();
		int width = mc.getWindow().getScaledWidth();
		int height = mc.getWindow().getScaledHeight();
		this.addDrawable(new TextWidget((int) (width * 0.3), (int) (this.height * 0.25) - 25, 
				(int) (width * 0.4), 23, 
				TITLE, MinecraftClient.getInstance().textRenderer));
		this.name = new TextFieldWidget(mc.textRenderer, 
				(int) (width * 0.3), (int) (this.height * 0.25), 
				(int) (width * 0.4), 23, 
				I18N.translateAsText("gui.marker.name"));
		this.color = CyclingButtonWidget.<DyeColor>builder((c) -> {
					return Text.translatable(c.getName().toUpperCase()).withColor(c.getSignColor());
				})
				.values(Lists.newArrayList(DyeColor.values()))
				.initially(DyeColor.WHITE)
				.build((int) (width * 0.3), (int) (height * 0.25) + 27, 
						(int) (width * 0.4), 23, 
						I18N.translateAsText("gui.marker.color"));
		this.addDrawableChild(this.name);
		this.addDrawableChild(this.color);
		ButtonWidget saveBtn = ButtonWidget.builder(ScreenTexts.DONE, (b) -> {
			String name = this.name.getText();
			DyeColor color = this.color.getValue();
			Text title = Text.literal(name);
			Session.Event event = new Session.Event(title, System.currentTimeMillis(), color.getSignColor());
			if (Session.current != null) {
				Session.current.addEvent(event);
			}
			
			mc.inGameHud.setOverlayMessage(title, true);
			this.close();
		}).dimensions((int) (width * 0.3), (int) (this.height * 0.25) + 54, (int) (width * 0.4), 23).build();
		this.addDrawableChild(saveBtn);
	}
}
