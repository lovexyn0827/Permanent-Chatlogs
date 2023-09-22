package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.Session.Summary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class SessionListScreen extends Screen {
	private static final Text PREVIOUS = Text.literal("Previous");
	private static final Text NEXT = Text.literal("Next");
	private SessionList displayedSessions;
	private final Predicate<Session.Summary> filterer;
	
	public SessionListScreen() {
		super(Text.literal("Chat Logs"));
		this.filterer = (s) -> true;
	}
	
	public SessionListScreen(Predicate<Session.Summary> filterer) {
		super(Text.literal("Chat Logs"));
		this.filterer = filterer;
	}
	
	@Override
	protected void init() {
		this.displayedSessions = new SessionList(this.client);
		this.addDrawableChild(this.displayedSessions);
		
		ButtonWidget prevBtn = ButtonWidget.builder(PREVIOUS, (btn) -> this.displayedSessions.turnPage(false))
				.dimensions(this.width / 2 - 128, this.height - 23, 120, 20)
				.build();
		ButtonWidget nextBtn = ButtonWidget.builder(NEXT, (btn) -> this.displayedSessions.turnPage(true))
				.dimensions(this.width / 2 + 8, this.height - 23, 120, 20)
				.build();
		ButtonWidget filterBtn = ButtonWidget.builder(Text.literal("Filter"), 
						(btn) -> this.client.setScreen(new FilterSessionScreen()))
				.dimensions(2, 2, 60, 20)
				.build();
		ButtonWidget settingBtn = ButtonWidget.builder(Text.literal("Sessings"), 
						(btn) -> this.client.setScreen(new SettingScreen()))
				.dimensions(65, 2, 60, 20)
				.build();
		this.addDrawableChild(prevBtn);
		this.addDrawableChild(nextBtn);
		this.addDrawableChild(filterBtn);
		this.addDrawableChild(settingBtn);
	}
	
	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseY, mouseY, delta);
		this.displayedSessions.render(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
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
				this.sizeAndTimeLength = Text.literal(String.format("%d:%d:%d (%d Messages)", 
						(int) Math.floor(delta / 3600), (int) Math.floor((delta % 3600) / 60), delta % 60, info.size));
			}

			@Override
			public Text getNarration() {
				return this.saveName;
			}
			
			@Override
			public void render(DrawContext ctx, int i, int y, int x, 
					int width, int height, int var7, int var8, boolean var9, float var10) {
				TextRenderer tr = SessionListScreen.this.client.textRenderer;
				ctx.drawText(tr, this.saveName, x, y, 0xFFFFFFFF, false);
				ctx.drawText(tr, this.start, x, y + 10, 0xFFFFFFFF, false);
				ctx.drawText(tr, this.sizeAndTimeLength, x, y + 20, 0xFFFFFFFF, false);
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
