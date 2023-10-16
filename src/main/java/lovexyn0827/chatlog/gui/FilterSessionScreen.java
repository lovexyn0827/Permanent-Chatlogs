package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.function.Predicate;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.predicate.NumberRange.IntRange;

public final class FilterSessionScreen extends Screen {
	private Predicate<Session.Summary> filterer;
	private TextFieldWidget saveName;
	private TextFieldWidget date;
	private TextFieldWidget size;
	private TextFieldWidget seconds;
	
	protected FilterSessionScreen() {
		super(I18N.translateAsText("gui.filter.sessions"));
	}

	@Override
	public void close() {
		this.filterer = (s) -> {
			return s.saveName.contains(this.saveName.getText());
		};
		try {
			String dateStr = this.date.getText();
			if(dateStr.matches("^\\d+$")) {
				int y;
				int m;
				int d;
				int raw = Integer.parseInt(dateStr);
				switch(dateStr.length()) {
				case 4:
					y = raw;
					m = 0;
					d = 0;
					break;
				case 6:
					y = raw / 100;
					m = raw % 100;
					d = 0;
					break;
				default:
					y = raw / 10000;
					m = (raw / 100) % 100;
					d = raw % 100;
					break;
				}
				
				this.filterer = this.filterer.and((s) -> {
					ZonedDateTime start = ZonedDateTime.ofInstant(
							Instant.ofEpochMilli(s.startTime), s.timeZone.toZoneId());
					return start.getYear() == y
							&& (m == 0 || start.getMonthValue() == m)
							&& (d == 0 || start.getDayOfMonth() == d);
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			IntRange sizeRange = IntRange.parse(new StringReader(this.size.getText()));
			this.filterer = this.filterer.and((s) -> sizeRange.test(s.size));
		} catch (CommandSyntaxException e) {
		}
		
		try {
			IntRange sizeRange = IntRange.parse(new StringReader(this.seconds.getText()));
			this.filterer = this.filterer.and((s) -> sizeRange.test((int) ((s.endTime - s.startTime) / 1000)));
		} catch (CommandSyntaxException e) {
		}
		
		this.client.setScreen(new SessionListScreen(this.filterer));
	}
	
	@Override
	public void init() {
		int width = this.client.getWindow().getScaledWidth();
		int height = this.client.getWindow().getScaledHeight();
		this.saveName = new TextFieldWidget(this.textRenderer, 
				(int) (width * 0.35F), (int) (height * 0.25F), 
				(int) (width * 0.4F), 14, 
				I18N.translateAsText("gui.filter.savename"));
		this.date = new TextFieldWidget(this.textRenderer, 
				(int) (width * 0.35F), (int) (height * 0.25F) + 18, 
				(int) (width * 0.4F), 14, 
				I18N.translateAsText("gui.filter.date"));
		this.size = new TextFieldWidget(this.textRenderer, 
				(int) (width * 0.35F), (int) (height * 0.25F) + 36, 
				(int) (width * 0.4F), 14, 
				I18N.translateAsText("gui.filter.messages"));
		this.seconds = new TextFieldWidget(this.textRenderer, 
				(int) (width * 0.35F), (int) (height * 0.25F) + 54, 
				(int) (width * 0.4F), 14, 
				I18N.translateAsText("gui.filter.seconds"));
		this.addDrawableChild(this.saveName);
		this.addDrawableChild(this.date);
		this.addDrawableChild(this.size);
		this.addDrawableChild(this.seconds);
		this.addDrawableChild(new ButtonWidget(width / 2 - 40, (int) (height * 0.25F) + 72, 
				80, 20, ScreenTexts.DONE, (btn) -> this.close()));
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		int width = this.client.getWindow().getScaledWidth();
		int height = this.client.getWindow().getScaledHeight();
		this.renderBackground(matrices);
		drawCenteredText(matrices, this.textRenderer, I18N.translateAsText("gui.filter.savename"), 
				(int) (width * 0.25F), (int) (height * 0.25F), 0xFFFFFFFF);
		drawCenteredText(matrices, this.textRenderer, I18N.translateAsText("gui.filter.date"), 
				(int) (width * 0.25F), (int) (height * 0.25F) + 18, 0xFFFFFFFF);
		drawCenteredText(matrices, this.textRenderer, I18N.translateAsText("gui.filter.messages"), 
				(int) (width * 0.25F), (int) (height * 0.25F) + 36, 0xFFFFFFFF);
		drawCenteredText(matrices, this.textRenderer, I18N.translateAsText("gui.filter.seconds"), 
				(int) (width * 0.25F), (int) (height * 0.25F) + 54, 0xFFFFFFFF);
		this.saveName.render(matrices, mouseX, mouseY, height);
		this.date.render(matrices, mouseX, mouseY, height);
		this.size.render(matrices, mouseX, mouseY, height);
		this.seconds.render(matrices, mouseX, mouseY, height);
		super.render(matrices, mouseX, mouseY, delta);
	}
}
