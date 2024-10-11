package lovexyn0827.chatlog.gui;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.Session.Summary;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
				.dimensions(this.width / 2 - 128, this.height - 23, 124, 20)
				.build();
		ButtonWidget nextBtn = ButtonWidget.builder(I18N.translateAsText("gui.next"), 
						(btn) -> this.displayedSessions.turnPage(true))
				.dimensions(this.width / 2 + 4, this.height - 23, 124, 20)
				.build();
		ButtonWidget openBtn = ButtonWidget.builder(I18N.translateAsText("gui.open"), 
				(btn) -> {
					SessionList.SessionEntry entry = this.displayedSessions.getFocused();
					if (entry != null ) {
						entry.loadSession();
					}
				})
				.dimensions(this.width / 2 - 128, this.height - 46, 80, 20)
				.build();
		ButtonWidget exportBtn = ButtonWidget.builder(I18N.translateAsText("gui.export"), 
				(btn) -> {
					SessionList.SessionEntry entry = this.displayedSessions.getFocused();
					if (entry != null ) {
						this.client.setScreen(new ExportSessionScreen(entry.summary));
					}
				})
				.dimensions(this.width / 2 - 40, this.height - 46, 80, 20)
				.build();
		ButtonWidget deleteBtn = ButtonWidget.builder(I18N.translateAsText("gui.del"), 
				(btn) -> {
					SessionList.SessionEntry entry = this.displayedSessions.getFocused();
					if (entry != null ) {
						this.client.setScreen(new ConfirmScreen((confirmed) -> {
							if (confirmed) {
								// TODO Session.delete(entry.summary.id);
							}
							
							this.client.setScreen(this);
						}, I18N.translateAsText("gui.del.title"), I18N.translateAsText("gui.del.desc")));
					}
				})
				.dimensions(this.width / 2 + 48, this.height - 46, 80, 20)
				.build();
		ButtonWidget filterBtn = ButtonWidget.builder(I18N.translateAsText("gui.filter"), 
						(btn) -> this.client.setScreen(new FilterSessionScreen()))
				.dimensions(this.width / 2 - 128, 2, 80, 20)
				.build();
		ButtonWidget settingBtn = ButtonWidget.builder(I18N.translateAsText("gui.settings"), 
						(btn) -> this.client.setScreen(new SettingScreen()))
				.dimensions(this.width / 2 - 40, 2, 80, 20)
				.build();
		ButtonWidget exitBtn = ButtonWidget.builder(ScreenTexts.BACK, 
						(btn) -> this.client.setScreen(new TitleScreen()))
				.dimensions(this.width / 2 + 48, 2, 80, 20)
				.build();
		this.addDrawableChild(prevBtn);
		this.addDrawableChild(nextBtn);
		this.addDrawableChild(openBtn);
		this.addDrawableChild(exportBtn);
		this.addDrawableChild(deleteBtn);
		this.addDrawableChild(filterBtn);
		this.addDrawableChild(settingBtn);
		this.addDrawableChild(exitBtn);
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
				.sorted((s1, s2) -> (int) Math.signum((double) (s2.startTime - s1.startTime)))
				.collect(Collectors.toList());
		private List<Session.Summary> visibleSessions;
		private int currentPage = 0;
		
		public SessionList(MinecraftClient mc) {
			super(mc, SessionListScreen.this.width, SessionListScreen.this.height - 84, 30, 32);
			this.toPage(0);
		}
		
		private void toPage(int i) {
			this.currentPage = i;
			this.visibleSessions = this.sessionsInPage(i);
			this.clearEntries();
			this.visibleSessions.stream().map(SessionEntry::new).forEach(this::addEntry);
			this.setFocused(null);
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
				this.start = Text.literal(info.getFormattedStartTime())
						.formatted(Formatting.GRAY);
				long delta = (long) Math.floor((info.endTime - info.startTime) / 1000);
				this.sizeAndTimeLength = Text.literal(String.format(I18N.translate("gui.sizeandtime"), 
						(int) Math.floor(delta / 3600), (int) Math.floor((delta % 3600) / 60), delta % 60, info.size))
						.formatted(Formatting.GRAY);
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
				SessionList.this.setFocused(this);
				return true;
			}
			
			public boolean loadSession() {
				try {
					Session session = this.summary.load();
					if (session != null) {
						SessionListScreen.this.client.setScreen(new ChatLogScreen(this.summary, session));
					} else {
						SystemToast warning = new SystemToast(new SystemToast.Type(), 
								I18N.translateAsText("gui.sload.failure"), 
								I18N.translateAsText("gui.sload.failure.desc"));
						MinecraftClient.getInstance().getToastManager().add(warning);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				return true;
			}
		}
	}
}
