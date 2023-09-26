package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoField;
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
	public void onClose() {
		this.filterer = (s) -> {
			return s.saveName.contains(this.saveName.getText());
		};
		try {
			String dateStr = this.date.getText();
			if(dateStr.matches("[0-9]+\\-[0-9]+\\-[0-9]+")) {
				LocalDate date = LocalDate.parse(dateStr);
				this.filterer = this.filterer.and((s) -> date.equals(
						LocalDate.ofEpochDay(s.startTime / 86400000)));
			} else if (dateStr.matches("[0-9]+\\-[0-9]+")){
				YearMonth date = YearMonth.parse(dateStr);
				this.filterer = this.filterer.and((s) -> {
					Instant sT = Instant.ofEpochMilli(s.startTime);
					return date.equals(YearMonth.of(sT.get(ChronoField.YEAR), sT.get(ChronoField.MONTH_OF_YEAR)));
				});
			} else if(dateStr.matches("[0-9]+")) {
				int year = Integer.parseInt(dateStr);
				this.filterer = this.filterer.and((s) -> {
					return Instant.ofEpochMilli(s.startTime).get(ChronoField.YEAR) == year;
				});
			}
		} catch (Exception e) {
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
		
		this.client.openScreen(new SessionListScreen(this.filterer));
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
		this.addChild(this.saveName);
		this.addChild(this.date);
		this.addChild(this.size);
		this.addChild(this.seconds);
		this.addButton(new ButtonWidget(width / 2 - 40, (int) (height * 0.25F) + 72, 
				80, 20, ScreenTexts.DONE, (btn) -> this.onClose()));
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
