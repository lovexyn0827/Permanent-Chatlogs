package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.Session.Line;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class ChatLogScreen extends Screen {
	private final Session session;
	private final ZoneId timeZone;
	private ChatLogWidget chatlogs;
	private SearchFieldWidget searchField;
	private CyclingButtonWidget<SearchingMode> searchBarModeChooser;
	
	protected ChatLogScreen(Session.Summary metadata, Session session) {
		super(Text.literal(metadata.saveName));
		this.session = session;
		this.timeZone = metadata.timeZone.toZoneId();
	}

	@Override
	protected void init() {
		this.width = (int) (this.client.getWindow().getWidth() * 0.8F);
		this.chatlogs = new ChatLogWidget(this.client, this.session);
		this.searchField = new SearchFieldWidget(this.textRenderer);
		this.addDrawableChild(this.searchField);
		this.addDrawableChild(this.chatlogs);
		this.searchBarModeChooser = CyclingButtonWidget
				.<SearchingMode>builder(SearchingMode::displayedText)
				.values(SearchingMode.values())
				.initially(SearchingMode.TEXT)
				.build(2, 0, (int) (this.client.getWindow().getScaledWidth() * 0.2F) - 4, 20, 
						ScreenTexts.EMPTY, (b, v) -> this.chatlogs.filter(this.searchField.getText()));
		ButtonWidget extractBtn = ButtonWidget.builder(I18N.translateAsText("gui.extract"), 
				(btn) -> {
					List<Session.Line> delims = this.chatlogs.collectDelimiters();
					SystemToast warning;
					switch (delims.size()) {
					case 0:
						warning = new SystemToast(new SystemToast.Type(), 
								I18N.translateAsText("gui.extract.nodelim"), 
								I18N.translateAsText("gui.extract.nodelim.desc"));
						MinecraftClient.getInstance().getToastManager().add(warning);
						break;
					case 1:
						ConfirmScreen endChooser = new ConfirmScreen((before) -> {
									Session chosen;
									if (before) {
										chosen = this.session.clip(null, delims.get(0));
									} else {
										chosen = this.session.clip(delims.get(0), null);
									}
									
									this.saveExtractedSession(chosen);
									this.client.setScreen(this);
								}, ScreenTexts.EMPTY, 
								I18N.translateAsText("gui.extract.choend"), 
								I18N.translateAsText("gui.extract.before"), 
								I18N.translateAsText("gui.extract.after"));
						this.client.setScreen(endChooser);
						break;
					case 2:
						this.saveExtractedSession(this.session.clip(delims.get(0), delims.get(1)));
						break;
					default:
						warning = new SystemToast(new SystemToast.Type(), 
								I18N.translateAsText("gui.extract.muldelim"), 
								I18N.translateAsText("gui.extract.muldelim.desc"));
						MinecraftClient.getInstance().getToastManager().add(warning);
					}
				})
				.dimensions((int) (this.client.getWindow().getScaledWidth() * 0.8F) + 2, 0, 
						(int) (this.client.getWindow().getScaledWidth() * 0.2F) - 4, 20)
				.build();
		this.addDrawableChild(this.searchBarModeChooser);
		this.addDrawableChild(extractBtn);
	}
	
	private void saveExtractedSession(Session s) {
		s.save();
	}
	
	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseY, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		this.chatlogs.render(context, mouseX, mouseY, delta);
		this.searchField.render(context, mouseX, mouseY, delta);
	}
	
	@Override
	public void close() {
		this.client.setScreen(new SessionListScreen());
	}
	
	private final class ChatLogWidget extends ElementListWidget<ChatLogWidget.Entry> {
		private final List<Entry> allEntries;
		
		public ChatLogWidget(MinecraftClient client, Session session) {
			super(client, ChatLogScreen.this.client.getWindow().getScaledWidth(), 
					ChatLogScreen.this.height - 40, 20, client.textRenderer.fontHeight + 1);
			session.getAllMessages().forEach((l) -> {
				boolean[] firstLine = new boolean[] { true };
				ChatMessages.breakRenderedChatMessageLines(l.message, 
						ChatLogScreen.this.client.getWindow().getScaledWidth() - 14, 
						ChatLogScreen.this.textRenderer).forEach((t) -> {
							this.addEntry(new Entry(l, t, l.time, firstLine[0]));
							firstLine[0] = false;
						});
			});
			this.allEntries = ImmutableList.copyOf(this.children());
		}
		
		public List<Line> collectDelimiters() {
			return this.allEntries.stream()
					.filter((e) -> e.isDelimiter)
					.map((e) -> e.owner)
					.distinct()
					.collect(Collectors.toList());
		}

		@Override
		public int getRowWidth() {
			return ChatLogScreen.this.client.getWindow().getScaledWidth();
		}
		
		@Override
		protected int getScrollbarPositionX() {
			return this.getRight() - 5;
		}
		
		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			verticalAmount *= (double)this.itemHeight / 2.0 * (Screen.hasControlDown() ? 
					(Screen.hasAltDown() ? 160 : 32) : 4.0);
			this.setScrollAmount(this.getScrollAmount() - verticalAmount);
			return true;
		}
		
		protected void filter(String in) {
			this.replaceEntries(this.allEntries.stream()
					.filter((e) -> {
						switch (ChatLogScreen.this.searchBarModeChooser.getValue()) {
						case TEXT:
							return e.lineStr.contains(in);
						case TIME:
							return e.getFormattedTime().contains(in);
						case SENDER:
							return true;	// TODO
						case EVENT:
							return e.owner instanceof Session.Event && e.lineStr.contains(in);
						default:
							return true;
						}
					})
					.collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
			this.setScrollAmount(0);
		}

		private final class Entry extends ElementListWidget.Entry<Entry> {
			private final Session.Line owner;
			private final OrderedText line;
			private final String lineStr;
			private final long time;
			private final boolean firstLine;
			private boolean isDelimiter = false;
			
			protected Entry(Session.Line owner, OrderedText t, long time, boolean firstLine) {
				this.owner = owner;
				this.line = t;
				this.time = time;
				this.firstLine = firstLine;
				StringBuilder sb = new StringBuilder();
				t.accept((idx, style, cp) -> {
					sb.append((char) cp);
					return true;
				});
				this.lineStr = sb.toString();
			}
			
			public String getFormattedTime() {
				return (this.time == 0L) ? I18N.translate("gui.unknowntime") : 
						Instant.ofEpochMilli(this.time)
								.atZone(ChatLogScreen.this.timeZone)
								.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			}

			@Override
			public void render(DrawContext ctx, int j, int y, int x, 
					int width, int height, int mouseX, int mouseY, boolean hovering, float var10) {
				TextRenderer tr = ChatLogScreen.this.textRenderer;
				ctx.drawTextWithShadow(tr, this.line, x + 4, y, 0xFFFFFFFF);
				if (this.isDelimiter) {
					int textWidth = ChatLogScreen.this.client.getWindow().getScaledWidth() - 14;
					ctx.drawHorizontalLine(x + 4, x + textWidth, y - 1, 0xFFFF0000);
				}
				
				ctx.fill(x + 1, y + (this.firstLine ? 2 : 0), x + 3, y + 10, this.owner.getMarkColor());
				if(hovering) {
					if(mouseX - x < 4) {
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
						ChatMessages.breakRenderedChatMessageLines(Text.literal(text), width / 2, tr), 
						mouseX, mouseY);
			}

			private void renderToolTip(DrawContext ctx, TextRenderer tr, Text text, int mouseX, int mouseY) {
				ctx.drawOrderedTooltip(tr, 
						ChatMessages.breakRenderedChatMessageLines(text, width / 2, tr), 
						mouseX, mouseY);
			}

			@Override
			public List<? extends Element> children() {
				return new ArrayList<>();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return new ArrayList<>();
			}
			
			@Nullable
			private Text getToolTip(double mouseX, double mouseY) {
				TextRenderer tr = ChatLogScreen.this.textRenderer;
				double scale = ChatLogScreen.this.client.getWindow().getScaleFactor();
				int pos = (int) Math.floor(mouseX - 4 * scale);
				Style style = tr.getTextHandler().getStyleAt(line, pos);
				if(style != null) {
					HoverEvent he;
					boolean hasHoverText = false;
					if((he = style.getHoverEvent()) != null && !Screen.hasAltDown()) {
						if(he.getAction() == HoverEvent.Action.SHOW_TEXT) {
							hasHoverText = true;
							return he.getValue(HoverEvent.Action.SHOW_TEXT);
						}
					}
					
					ClickEvent ce;
					if((ce = style.getClickEvent()) != null) {
						if(!hasHoverText) {
							return Text.literal(ce.getValue());
						}
					}
				}
				
				return null;
			}
			
			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if(Screen.hasControlDown()) {
					Text tip = this.getToolTip(mouseX, mouseY);
					if(tip != null) {
						ChatLogScreen.this.client.keyboard.setClipboard(tip.getString());
						return true;
					}
				}
				
				if (Screen.hasShiftDown()) {
					this.isDelimiter ^= true;
				}
				
				return false;
			}
		}
	}
	
	private final class SearchFieldWidget extends TextFieldWidget {
		public SearchFieldWidget(TextRenderer textRenderer) {
			super(textRenderer,  
					(int) (ChatLogScreen.this.client.getWindow().getScaledWidth() * 0.2F), 2, 
					(int) (ChatLogScreen.this.client.getWindow().getScaledWidth() * 0.6F), 16, 
					I18N.translateAsText("gui.search")
			);
			this.setChangedListener(ChatLogScreen.this.chatlogs::filter);
		}
	}
	
	private enum SearchingMode {
		TEXT, 
		TIME, 
		SENDER, 
		EVENT;
		
		protected Text displayedText() {
			return I18N.translateAsText("gui.search.mode." + this.name().toLowerCase());
		}
	}
}
