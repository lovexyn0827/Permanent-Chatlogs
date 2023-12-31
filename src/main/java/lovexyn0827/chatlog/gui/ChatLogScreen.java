package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class ChatLogScreen extends Screen {
	private final Session session;
	private final ZoneId timeZone;
	private ChatLogWidget chatlogs;
	private SearchFieldWidget searchField;
	
	protected ChatLogScreen(Session.Summary s) {
		super(new LiteralText(s.saveName));
		this.session = Session.load(s);
		this.timeZone = s.timeZone.toZoneId();
	}

	@Override
	protected void init() {
		this.width = (int) (this.client.getWindow().getWidth() * 0.8F);
		this.chatlogs = new ChatLogWidget(this.client, this.session);
		this.searchField = new SearchFieldWidget(this.textRenderer);
		this.addChild(this.searchField);
		this.addChild(this.chatlogs);
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		this.chatlogs.render(matrices, mouseX, mouseY, delta);
		this.searchField.render(matrices, mouseX, mouseY, delta);
		super.render(matrices, mouseX, mouseY, delta);
	}
	
	@Override
	public void onClose() {
		this.client.openScreen(new SessionListScreen());
	}
	
	private final class ChatLogWidget extends EntryListWidget<ChatLogWidget.Entry> {
		private final List<Entry> allEntries;
		
		public ChatLogWidget(MinecraftClient client, Session session) {
			super(client, ChatLogScreen.this.client.getWindow().getScaledWidth(), 
					ChatLogScreen.this.height, 
					20, ChatLogScreen.this.height - 20, client.textRenderer.fontHeight + 1);
			session.getAllMessages().forEach((l) -> {
				ChatMessages.breakRenderedChatMessageLines(l.message, 
						ChatLogScreen.this.client.getWindow().getScaledWidth() - 14, 
						ChatLogScreen.this.textRenderer).forEach((t) -> this.addEntry(new Entry(t, l.time)));
			});
			this.allEntries = ImmutableList.copyOf(this.children());
		}
		
		@Override
		public int getRowWidth() {
			return ChatLogScreen.this.client.getWindow().getScaledWidth();
		}
		
		@Override
		protected int getScrollbarPositionX() {
			return this.right - 5;
		}
		
		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
			amount *= (double)this.itemHeight / 2.0 * (Screen.hasControlDown() ? 
					(Screen.hasAltDown() ? 160 : 32) : 4.0);
			this.setScrollAmount(this.getScrollAmount() - amount);
			return true;
		}
		
		protected void filter(String in) {
			this.replaceEntries(this.allEntries.stream()
					.filter((e) -> e.lineStr.contains(in))
					.collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
			this.setScrollAmount(0);
		}

		private final class Entry extends EntryListWidget.Entry<Entry> {
			private final OrderedText line;
			private final String lineStr;
			private final long time;
			
			protected Entry(OrderedText t, long time) {
				this.line = t;
				this.time = time;
				StringBuilder sb = new StringBuilder();
				t.accept((idx, style, cp) -> {
					sb.append((char) cp);
					return true;
				});
				this.lineStr = sb.toString();
			}
			
			@Override
			public void render(MatrixStack ms, int j, int y, int x, 
					int width, int height, int mouseX, int mouseY, boolean hovering, float var10) {
				TextRenderer tr = ChatLogScreen.this.textRenderer;
				tr.drawWithShadow(ms, this.line, x + 4, y, 0xFFFFFFFF);
				DrawableHelper.fill(ms, x + 1, y, x + 3, y + 9, 0xFF31F38B);
				if(hovering) {
					if(mouseX - x < 4) {
						String time = (this.time == 0L) ? I18N.translate("gui.unknowntime") : 
							Instant.ofEpochMilli(this.time)
									.atZone(ChatLogScreen.this.timeZone)
									.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
						this.renderToolTip(ms, tr, time, mouseX, mouseY);
					} else {
						Text tip = this.getToolTip(mouseX, mouseY);
						if(tip != null) {
							this.renderToolTip(ms, tr, tip, mouseX, mouseY);
						}
					}
				}
			}
			
			private void renderToolTip(MatrixStack ms, TextRenderer tr, String value, int mouseX, int mouseY) {
				ChatLogScreen.this.renderOrderedTooltip(ms, 
						ChatMessages.breakRenderedChatMessageLines(new LiteralText(value), width / 2, tr), 
						mouseX, mouseY);
			}

			private void renderToolTip(MatrixStack ms, TextRenderer tr, Text text, int mouseX, int mouseY) {
				ChatLogScreen.this.renderOrderedTooltip(ms, 
						ChatMessages.breakRenderedChatMessageLines(text, width / 2, tr), 
						mouseX, mouseY);
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
							return new LiteralText(ce.getValue());
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
				
				return false;
			}
		}
	}
	
	private final class SearchFieldWidget extends TextFieldWidget {
		public SearchFieldWidget(TextRenderer textRenderer) {
			super(textRenderer,  
					(int) (ChatLogScreen.this.client.getWindow().getScaledWidth() * 0.2F), 2, 
					(int) (ChatLogScreen.this.client.getWindow().getScaledWidth() * 0.6F), 14, 
					I18N.translateAsText("gui.search")
			);
			this.setChangedListener(ChatLogScreen.this.chatlogs::filter);
		}
	}
}
