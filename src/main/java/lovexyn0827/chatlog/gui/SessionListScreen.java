package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.Session.Summary;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class SessionListScreen extends Screen {
	private SessionList displayedSessions;
	private final Predicate<Session.Summary> filterer;
	
	public SessionListScreen() {
		super(I18N.translateAsText("gui.chatlogs"));
		this.filterer = (s) -> true;
	}
	
	public SessionListScreen(Predicate<Session.Summary> filterer) {
		super(I18N.translateAsText("gui.chatlogs"));
		this.filterer = filterer;
	}
	
	@Override
	protected void init() {
		this.displayedSessions = new SessionList(this.client);
		this.addDrawableChild(this.displayedSessions);
		ButtonWidget prevBtn = ButtonWidget.builder(I18N.translateAsText("gui.prev"), 
						(btn) -> this.displayedSessions.turnPage(false))
				.dimensions(this.width / 2 - 128, this.height - 23, 120, 20)
				.build();
		ButtonWidget nextBtn = ButtonWidget.builder(I18N.translateAsText("gui.next"), 
						(btn) -> this.displayedSessions.turnPage(true))
				.dimensions(this.width / 2 + 8, this.height - 23, 120, 20)
				.build();
		ButtonWidget filterBtn = ButtonWidget.builder(I18N.translateAsText("gui.filter"), 
						(btn) -> this.client.setScreen(new FilterSessionScreen()))
				.dimensions(2, 2, 60, 20)
				.build();
		ButtonWidget settingBtn = ButtonWidget.builder(I18N.translateAsText("gui.settings"), 
						(btn) -> this.client.setScreen(new SettingScreen()))
				.dimensions(65, 2, 60, 20)
				.build();
		this.addDrawableChild(prevBtn);
		this.addDrawableChild(nextBtn);
		this.addDrawableChild(filterBtn);
		this.addDrawableChild(settingBtn);
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		this.displayedSessions.render(matrices, mouseX, mouseY, delta);
		super.render(matrices, mouseX, mouseY, delta);
	}
	
	private final class SessionList extends AlwaysSelectedEntryListWidget<SessionList.SessionEntry> {
		private final List<Session.Summary> allSessions = Session.getSessionSummaries()
				.stream()
				.filter(SessionListScreen.this.filterer)
				.sorted((s1, s2) -> (int) (s2.startTime - s1.startTime))
				.collect(Collectors.toList());
		private List<Session.Summary> visibleSessions;
		private int currentPage = 0;
		
		public SessionList(MinecraftClient mc) {
			super(mc, SessionListScreen.this.width, SessionListScreen.this.height, 
					20, SessionListScreen.this.height - 34, 32);
			this.toPage(0);
		}
		
		private void toPage(int i) {
			this.currentPage = i;
			this.visibleSessions = this.sessionsInPage(i);
			this.clearEntries();
			this.visibleSessions.stream().map(SessionEntry::new).forEach(this::addEntry);
		}

		private List<Summary> sessionsInPage(int i) {
			return this.allSessions.subList(i * 50, 
					Math.max(Math.min(i * 50 + 49, this.allSessions.size()), i * 50));
		}

		public void turnPage(boolean next) {
			this.toPage(MathHelper.clamp(this.currentPage + (next ? 1 : -1), 0, this.allSessions.size() / 50));
		}
		
		private final class SessionEntry extends AlwaysSelectedEntryListWidget.Entry<SessionEntry> {
			private final Session.Summary summary;
			private final Text saveName;
			private final Text start;
			private final Text sizeAndTimeLength;
			
			public SessionEntry(Session.Summary info) {
				this.summary = info;
				this.saveName = Text.literal(info.saveName);
				this.start = Text.literal(Instant.ofEpochMilli(info.startTime)
						.atZone(this.summary.timeZone.toZoneId()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
				long delta = (long) Math.floor((info.endTime - info.startTime) / 1000);
				this.sizeAndTimeLength = Text.literal(String.format(I18N.translate("gui.sizeandtime"), 
						(int) Math.floor(delta / 3600), (int) Math.floor((delta % 3600) / 60), delta % 60, info.size));
			}

			@Override
			public Text getNarration() {
				return this.saveName;
			}
			
			@Override
			public void render(MatrixStack ms, int i, int y, int x, 
					int width, int height, int var7, int var8, boolean var9, float var10) {
				TextRenderer tr = SessionListScreen.this.client.textRenderer;
				tr.draw(ms, this.saveName, x, y, 0xFFFFFFFF);
				tr.draw(ms, this.start, x, y + 10, 0xFFFFFFFF);
				tr.draw(ms, this.sizeAndTimeLength, x, y + 20, 0xFFFFFFFF);
			}
			
			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				try {
					SessionListScreen.this.client.setScreen(new ChatLogScreen(this.summary));
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				return true;
			}
		}
	}
}
