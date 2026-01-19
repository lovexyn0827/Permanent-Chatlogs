package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import lovexyn0827.chatlog.i18n.I18N;
import lovexyn0827.chatlog.session.Session;
import lovexyn0827.chatlog.session.Session.Line;
import lovexyn0827.chatlog.session.Session.Summary;
import lovexyn0827.chatlog.util.TextEventContentExtractor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.Util;

public class FullTextSearchResultScreen extends Screen {
	private final ConcurrentHashMap<Summary, List<Pair<Integer, Line>>> results;
	private SessionList sessions;
	private MessageList messages;

	protected FullTextSearchResultScreen(ConcurrentHashMap<Summary, List<Pair<Integer, Line>>> results) {
		super(I18N.translateAsText("gui.filter.result"));
		this.results = results;
	}
	
	@Override
	public void init() {
		this.sessions = new SessionList(this.client, this.results.keySet());
		this.addDrawableChild(sessions);
		this.messages = new MessageList(this.client);
		this.addDrawableChild(messages);
	}
	
	@Override
	public void close() {
		this.client.setScreen(new SessionListScreen());
	}
	
	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, I18N.translateAsText("gui.filter.result"), 
				this.width / 2, 5, 0xFFFFFFFF);
	}
	
	private class SessionList extends AlwaysSelectedEntryListWidget<SessionList.Entry> {
		public SessionList(MinecraftClient mc, Set<Session.Summary> sessions) {
			super(mc, (int) (FullTextSearchResultScreen.this.width * 0.38), 
					FullTextSearchResultScreen.this.height - 30, 
					20, 32);
			sessions.stream()
					.sorted((s1, s2) -> (int) (s2.startTime - s1.startTime))
					.map(Entry::new)
					.forEach(this::addEntry);
			this.setX(this.getRowLeft());
		}
		
		@Override
		public int getRowLeft() {
			return (int) (FullTextSearchResultScreen.this.width * 0.12 - 10);
		}
		
		@Override
		public int getRowWidth() {
			return this.width;
		}
		
		@Override
		public int getScrollbarX() {
			return FullTextSearchResultScreen.this.width / 2 - 10;
		}

		private class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
			protected final Session.Summary summary;
			private final Text saveName;
			private final Text start;
			private final Text sizeAndTimeLength;
			private long lastClick = 0;
			
			public Entry(Session.Summary info) {
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
			public boolean mouseClicked(Click click, boolean doubled) {
				FullTextSearchResultScreen.this.messages.setSession(
						this.summary, 
						FullTextSearchResultScreen.this.results.get(this.summary));
				if (this.isFocused() && Util.getMeasuringTimeMs() - this.lastClick < 1000) {
					GuiUtils.loadSession(FullTextSearchResultScreen.this.client, 
							this.summary, FullTextSearchResultScreen.this);
					return true;
				}

				SessionList.this.setFocused(this);
				this.lastClick = Util.getMeasuringTimeMs();
				return true;
			}

			@Override
			public Text getNarration() {
				return this.saveName;
			}

			@Override
			public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
				int x = this.getX();
				int y = this.getY();
				TextRenderer tr = FullTextSearchResultScreen.this.textRenderer;
				ctx.drawText(tr, this.saveName, x, y, 0xFFFFFFFF, false);
				ctx.drawText(tr, this.start, x, y + 10, 0xFFFFFFFF, false);
				ctx.drawText(tr, this.sizeAndTimeLength, x, y + 20, 0xFFFFFFFF, false);
			}
			
		}
	}
	
	private class MessageList extends ElementListWidget<MessageList.Entry> {
		private Session.Summary currentSessionSummary;
		
		public MessageList(MinecraftClient mc) {
			super(mc, (int) (FullTextSearchResultScreen.this.width * 0.38), 
					FullTextSearchResultScreen.this.height - 30, 
					20, mc.textRenderer.fontHeight + 1);
			this.setX(this.getRowLeft());
		}
		
		@Override
		public int getRowLeft() {
			return (int) (FullTextSearchResultScreen.this.width * 0.5 + 4);
		}
		
		@Override
		public int getRowWidth() {
			return this.width;
		}
		
		@Override
		public int getScrollbarX() {
			return (int) (FullTextSearchResultScreen.this.width * 0.88);
		}
		
		public void setSession(Session.Summary summary, List<Pair<Integer, Session.Line>> lines) {
			this.currentSessionSummary = summary;
			this.clearEntries();
			OrderedText title = I18N.translateAsText("gui.filter.matchcnt", lines.size()).asOrderedText();
			this.addEntry(new Entry(title, null, -1));
			for (Pair<Integer, Session.Line> e : lines) {
				this.addEntry(new Entry(Text.empty().asOrderedText(), null, -1));
				ChatMessages.breakRenderedChatMessageLines(e.getRight().message, 
						this.width - 10, 
						FullTextSearchResultScreen.this.textRenderer).forEach((t) -> {
							this.addEntry(new Entry(t, e.getRight(), e.getLeft()));
						});
			}
		}

		private class Entry extends ElementListWidget.Entry<Entry> {
			private final OrderedText text;
			private final Line owner;
			private final int ordinalInSession;
			private long lastClick = 0;
			
			public Entry(OrderedText text, Session.Line owner, int ord) {
				this.text = text;
				this.owner = owner;
				this.ordinalInSession = ord;
			}
			
			public String getFormattedTime() {
				return (this.owner.time == 0L) ? I18N.translate("gui.unknowntime") : 
						Instant.ofEpochMilli(this.owner.time)
								.atZone(MessageList.this.currentSessionSummary.timeZone.toZoneId())
								.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			}
			
			@Override
			public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
				int x = this.getX();
				int y = this.getY();
				TextRenderer tr = FullTextSearchResultScreen.this.textRenderer;
				ctx.drawTextWithShadow(tr, this.text, x + 4, y, 0xFFFFFFFF);
				ctx.fill(x + 1, y, x + 3, y + 10, this.owner == null ? 0 : this.owner.getMarkColor());
				if (hovered) {
					if(this.owner != null && mouseX - x < 4) {
						String time = this.getFormattedTime();
						this.renderToolTip(ctx, tr, time, mouseX, mouseY);
					} else {
						Text tip = this.getToolTip(mouseX, mouseY);
						if(tip != null) {
							this.renderToolTip(ctx, tr, tip, mouseX, mouseY);
						}
					}
				}
			}
			
			private void renderToolTip(DrawContext ctx, TextRenderer tr, String text, int mouseX, int mouseY) {
				ctx.drawOrderedTooltip(tr, 
						ChatMessages.breakRenderedChatMessageLines(Text.literal(text), 
								(int) (MessageList.this.width * 0.7), tr),
						mouseX, mouseY);
			}

			private void renderToolTip(DrawContext ctx, TextRenderer tr, Text text, int mouseX, int mouseY) {
				ctx.drawOrderedTooltip(tr, 
						ChatMessages.breakRenderedChatMessageLines(text, 
								(int) (MessageList.this.width * 0.7), tr), 
						mouseX, mouseY);
			}
			
			@SuppressWarnings("deprecation")
			@Nullable
			public Style getStyleAt(TextRenderer tr, OrderedText text, int x) {
				TextHandler.WidthLimitingVisitor widthLimitingVisitor = 
						tr.getTextHandler().new WidthLimitingVisitor((float)x);
				MutableObject<Style> mutableObject = new MutableObject<>();
				text.accept((index, style, codePoint) -> {
					if (!widthLimitingVisitor.accept(index, style, codePoint)) {
						mutableObject.setValue(style);
						return false;
					} else {
						return true;
					}
				});
				return mutableObject.getValue();
			}
			
			@Nullable
			private Text getToolTip(double mouseX, double mouseY) {
				TextRenderer tr = FullTextSearchResultScreen.this.textRenderer;
				double scale = FullTextSearchResultScreen.this.client.getWindow().getScaleFactor();
				int pos = (int) Math.floor(mouseX - (MessageList.this.getX() + 4) * scale);
				Style style = this.getStyleAt(tr, this.text, pos);
				if (style != null) {
					HoverEvent he;
					boolean altDown = GLFW.glfwGetKey(
							MinecraftClient.getInstance().getWindow().getHandle(), 
							GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
					if ((he = style.getHoverEvent()) != null && !altDown) {
						return TextEventContentExtractor.getHoverEventContent(he);
					}
					
					ClickEvent ce;
					if ((ce = style.getClickEvent()) != null) {
						return Text.literal(TextEventContentExtractor.getClickEventContent(ce));
					}
				}
				
				return null;
			}
			
			@Override
			public boolean mouseClicked(Click click, boolean doubled) {
				if (click.hasCtrlOrCmd()) {
					Text tip = this.getToolTip(click.x(), click.y());
					if(tip != null) {
						FullTextSearchResultScreen.this.client.keyboard.setClipboard(tip.getString());
						return true;
					}
				}
				
				if (this.owner != null && Util.getMeasuringTimeMs() - this.lastClick < 1000) {
					GuiUtils.loadSession(FullTextSearchResultScreen.this.client, 
							MessageList.this.currentSessionSummary, 
							FullTextSearchResultScreen.this, 
							this.ordinalInSession);
					return true;
				}
				
				this.lastClick = Util.getMeasuringTimeMs();
				return false;
			}

			@Override
			public List<? extends Element> children() {
				return Collections.emptyList();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return Collections.emptyList();
			}
		}
	}
}
