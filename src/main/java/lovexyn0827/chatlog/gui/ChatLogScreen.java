package lovexyn0827.chatlog.gui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import lovexyn0827.chatlog.Session;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
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
	private ChatLogWidget chatlogs;
	private SearchFieldWidget searchField;
	
	protected ChatLogScreen(Session.Summary s) {
		super(new LiteralText(s.saveName));
		this.session = Session.load(s);
	}

	@Override
	protected void init() {
		this.width = (int) (this.client.getWindow().getWidth() * 0.8F);
		this.chatlogs = new ChatLogWidget(this.client, this.session);
		this.searchField = new SearchFieldWidget(this.textRenderer);
		this.addDrawableChild(this.searchField);
		this.addDrawableChild(this.chatlogs);
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		this.chatlogs.render(matrices, mouseX, mouseY, delta);
		this.searchField.render(matrices, mouseX, mouseY, delta);
		super.render(matrices, mouseX, mouseY, delta);
	}
	
	@Override
	public void close() {
		this.client.setScreen(new SessionListScreen());
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
		
		@Override
		public void appendNarrations(NarrationMessageBuilder var1) {
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
						String time = (this.time == 0L) ? "UNKNOWN TIME" : Instant.ofEpochMilli(this.time).toString();
						this.renderToolTip(ms, tr, time, mouseX, mouseY);
					} else {
						double scale = ChatLogScreen.this.client.getWindow().getScaleFactor();
						int pos = (int) Math.floor(mouseX - 4 * scale);
						Style style = tr.getTextHandler().getStyleAt(line, pos);
						if(style != null) {
							HoverEvent he;
							boolean hasHoverText = false;
							if((he = style.getHoverEvent()) != null) {
								if(he.getAction() == HoverEvent.Action.SHOW_TEXT) {
									hasHoverText = true;
									Text text = he.getValue(HoverEvent.Action.SHOW_TEXT);
									this.renderToolTip(ms, tr, text, mouseX, mouseY);
								}
							}
							
							ClickEvent ce;
							if((ce = style.getClickEvent()) != null) {
								ChatLogScreen.this.handleTextClick(style);
								if(!hasHoverText) {
									this.renderToolTip(ms, tr, ce.getValue(), mouseX, mouseY);
								}
							}
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
		}
	}
	
	private final class SearchFieldWidget extends TextFieldWidget {
		public SearchFieldWidget(TextRenderer textRenderer) {
			super(textRenderer,  
					(int) (ChatLogScreen.this.client.getWindow().getScaledWidth() * 0.2F), 2, 
					(int) (ChatLogScreen.this.client.getWindow().getScaledWidth() * 0.6F), 14, 
					new LiteralText("Search")
			);
			this.setChangedListener(ChatLogScreen.this.chatlogs::filter);
		}
	}
}
