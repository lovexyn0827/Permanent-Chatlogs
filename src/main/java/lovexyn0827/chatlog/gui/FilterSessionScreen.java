package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.function.Predicate;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.predicate.NumberRange.IntRange;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
		selectDate:
		try {
			String dateStr = this.date.getText();
			int[] dateComps = Arrays.stream(dateStr.split("[^0-9]"))
					.filter((s) -> s.matches("^\\d+$"))
					.mapToInt(Integer::parseInt)
					.toArray();
			if(dateComps.length > 0 && dateComps.length <= 3) {
				int y;
				int m;
				int d;
				switch(dateComps.length) {
				case 1:
					y = dateComps[0];
					m = 0;
					d = 0;
					break;
				case 2:
					y = dateComps[0];
					m = dateComps[1];
					d = 0;
					break;
				case 3:
					y = dateComps[0];
					m = dateComps[1];
					d = dateComps[2];
					break;
				default:
					break selectDate;
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
		this.date.setPlaceholder(Text.literal("YYYY-MM-DD").formatted(Formatting.GRAY));
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
		
		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, (btn) -> this.close())
				.dimensions(width / 2 - 40, (int) (height * 0.25F) + 72, 80, 20)
				.build());
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		int width = this.client.getWindow().getScaledWidth();
		int height = this.client.getWindow().getScaledHeight();
		this.renderBackground(matrices);
		drawCenteredTextWithShadow(matrices, this.textRenderer, I18N.translateAsText("gui.filter.savename"), 
				(int) (width * 0.25F), (int) (height * 0.25F), 0xFFFFFFFF);
		drawCenteredTextWithShadow(matrices, this.textRenderer, I18N.translateAsText("gui.filter.date"), 
				(int) (width * 0.25F), (int) (height * 0.25F) + 18, 0xFFFFFFFF);
		drawCenteredTextWithShadow(matrices, this.textRenderer, I18N.translateAsText("gui.filter.messages"), 
				(int) (width * 0.25F), (int) (height * 0.25F) + 36, 0xFFFFFFFF);
		drawCenteredTextWithShadow(matrices, this.textRenderer, I18N.translateAsText("gui.filter.seconds"), 
				(int) (width * 0.25F), (int) (height * 0.25F) + 54, 0xFFFFFFFF);
		this.saveName.render(matrices, mouseX, mouseY, height);
		this.date.render(matrices, mouseX, mouseY, height);
		this.size.render(matrices, mouseX, mouseY, height);
		this.seconds.render(matrices, mouseX, mouseY, height);
		super.render(matrices, mouseX, mouseY, delta);
	}
}
