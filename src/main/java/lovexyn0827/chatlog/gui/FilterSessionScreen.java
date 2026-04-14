package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lovexyn0827.chatlog.i18n.I18N;
import lovexyn0827.chatlog.mixin.TextFieldWidgetAccessor;
import lovexyn0827.chatlog.session.Session;
import lovexyn0827.chatlog.session.Session.Summary;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.predicate.NumberRange.IntRange;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class FilterSessionScreen extends Screen {
    private final Screen parent;
	private final List<Session.Summary> cachedSessions = Session.getSessionSummaries();
	private Predicate<Session.Summary> filterer;
	private TextFieldWidget saveName;
	private TextFieldWidget date;
	private TextFieldWidget size;
	private TextFieldWidget seconds;
	private TextFieldWidget contents;
	private CheckboxWidget caseSenstive;
	private CyclingButtonWidget<Scope> scopeBtn;
	
	protected FilterSessionScreen(Screen parent) {
		super(I18N.translateAsText("gui.filter.sessions"));
        this.parent = parent;
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
			// FIXME: Handle overflowed values gracefully. 
			this.filterer = this.filterer.and((s) -> sizeRange.test((int) s.size));
		} catch (CommandSyntaxException e) {
		}
		
		try {
			IntRange sizeRange = IntRange.parse(new StringReader(this.seconds.getText()));
			this.filterer = this.filterer.and((s) -> sizeRange.test((int) ((s.endTime - s.startTime) / 1000)));
		} catch (CommandSyntaxException e) {
		}
		
		if (!this.contents.getText().isEmpty()) {
			this.client.setScreen(new FullTextSearchProgressScreen(
					this.parent, this.filterer, this.contents.getText(), this.caseSenstive.isChecked()));
		} else {
			this.filterer = this.filterer.and((s) -> this.scopeBtn.getValue().test(s));
			this.client.setScreen(new SessionListScreen(this.parent,this.filterer));
		}
	}
	
	@Override
	public void init() {
		int width = this.client.getWindow().getScaledWidth();
		int height = this.client.getWindow().getScaledHeight();
		this.saveName = new TextFieldWithAutoCompletionWidget(this.textRenderer, 
				(int) (width * 0.35F), (int) (height * 0.25F), 
				(int) (width * 0.4F), 14, 
				I18N.translateAsText("gui.filter.savename"));
		this.saveName.setChangedListener((in) -> {
			this.saveName.setSuggestion(this.cachedSessions.stream()
					.map((s) -> s.saveName)
					.filter((n) -> n.startsWith(this.saveName.getText()))
					.collect(Object2IntOpenHashMap<String>::new, (m, s) -> m.put(s, 0), (m1, m2) -> m1.putAll(m2))
					.object2IntEntrySet()
					.stream()
					.max(Comparator.comparing(Object2IntMap.Entry::getIntValue))
					.map(Map.Entry::getKey)
					.filter((n) -> n.length() > this.saveName.getText().length())
					.map((n) -> n.substring(this.saveName.getText().length()))
					.orElse(null));
		});
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
		this.contents = new TextFieldWidget(this.textRenderer, 
				(int) (width * 0.35F), (int) (height * 0.25F) + 72, 
				(int) (width * 0.4F), 14, 
				I18N.translateAsText("gui.filter.fulltext"));
		this.caseSenstive = CheckboxWidget.builder(Text.empty(), this.textRenderer)
				.checked(false)
				.pos((int) (width * 0.75F) - 18, (int) (height * 0.25F) + 90)
				.build();
		this.scopeBtn = CyclingButtonWidget.builder(Scope::getText)
				.values(Scope.values())
				.initially(Scope.ALL)
				.build((int) (width * 0.35F), (int) (height * 0.25F) + 108, (int) (width * 0.4F), 20, 
						Text.translatable("advMode.type"));
		this.addDrawableChild(this.saveName);
		this.addDrawableChild(this.date);
		this.addDrawableChild(this.size);
		this.addDrawableChild(this.seconds);
		this.addDrawableChild(this.contents);
		this.addDrawableChild(this.caseSenstive);
		this.addDrawableChild(this.scopeBtn);
		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, (btn) -> this.close())
				.dimensions(width / 2 - 40, (int) (height * 0.25F) + 132, 80, 20)
				.build());

	}
	
	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int width = this.client.getWindow().getScaledWidth();
		int height = this.client.getWindow().getScaledHeight();
		ctx.drawCenteredTextWithShadow(this.textRenderer, I18N.translateAsText("gui.filter.savename"), 
				(int) (width * 0.27F), (int) (height * 0.25F) + 2, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, I18N.translateAsText("gui.filter.date"), 
				(int) (width * 0.27F), (int) (height * 0.25F) + 20, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, I18N.translateAsText("gui.filter.messages"), 
				(int) (width * 0.27F), (int) (height * 0.25F) + 38, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, I18N.translateAsText("gui.filter.seconds"), 
				(int) (width * 0.27F), (int) (height * 0.25F) + 56, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, I18N.translateAsText("gui.filter.fulltext"), 
				(int) (width * 0.27F), (int) (height * 0.25F) + 74, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, I18N.translateAsText("gui.filter.case"), 
				(int) (width * 0.27F), (int) (height * 0.25F) + 92, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("advMode.type"), 
				(int) (width * 0.27F), (int) (height * 0.25F) + 113, 0xFFFFFFFF);
		this.saveName.render(ctx, mouseX, mouseY, height);
		this.date.render(ctx, mouseX, mouseY, height);
		this.size.render(ctx, mouseX, mouseY, height);
		this.seconds.render(ctx, mouseX, mouseY, height);
		this.contents.render(ctx, mouseY, width, height);
		this.caseSenstive.render(ctx, mouseX, mouseY, delta);
		this.scopeBtn.render(ctx, mouseX, mouseY, delta);
	}
	
	private static enum Scope implements Predicate<Session.Summary> {
		SINGLE_PLAYER(Text.translatable("menu.singleplayer")) {
			@Override
			public boolean test(Summary s) {
				return !s.multiplayer;
			}
		}, 
		MULTI_PLAYER(Text.translatable("menu.multiplayer")) {
			@Override
			public boolean test(Summary s) {
				return s.multiplayer;
			}
		}, 
		ALL(Text.translatable("gui.all")) {
			@Override
			public boolean test(Summary s) {
				return true;
			}
		};
		
		private final Text text;

		private Scope(Text text) {
			this.text = text;
		}
		
		private final Text getText() {
			return this.text;
		}

		@Override
		public abstract boolean test(Summary t);
	}
	
	private static class TextFieldWithAutoCompletionWidget extends TextFieldWidget {
		public TextFieldWithAutoCompletionWidget(TextRenderer textRenderer, int x, int y, int width, 
				int height, Text text) {
			super(textRenderer, x, y, width, height, text);
		}
		
		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_TAB && ((TextFieldWidgetAccessor) this).getSuggestion() != null) {
				this.setText(this.getText().concat(((TextFieldWidgetAccessor) this).getSuggestion()));
				return true;
			} else {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}
}
