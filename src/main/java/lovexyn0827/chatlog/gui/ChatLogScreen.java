package lovexyn0827.chatlog.gui;

import java.time.Instant;

import lovexyn0827.chatlog.Session;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class ChatLogScreen extends Screen {
	private final Session session;
	private ChatLogWidget chatlogs;
	
	protected ChatLogScreen(Session.Summary s) {
		super(new LiteralText(s.saveName));
		this.session = Session.load(s);
	}

	@Override
	protected void init() {
		this.width = (int) (this.client.getWindow().getWidth() * 0.8F);
		this.chatlogs = new ChatLogWidget(this.client, this.session);
		this.addChild(this.chatlogs);
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		this.chatlogs.render(matrices, mouseX, mouseY, delta);
		super.render(matrices, mouseX, mouseY, delta);
	}
	
	private final class ChatLogWidget extends EntryListWidget<ChatLogWidget.Entry> {
		public ChatLogWidget(MinecraftClient client, Session session) {
			super(client, ChatLogScreen.this.width, 
					ChatLogScreen.this.height, 
					20, ChatLogScreen.this.height - 20, client.textRenderer.fontHeight + 1);
			session.getAllMessages().forEach((l) -> {
				ChatMessages.breakRenderedChatMessageLines(l.message, ChatLogScreen.this.width - 4, 
						ChatLogScreen.this.textRenderer).forEach((t) -> this.addEntry(new Entry(t, l.time)));
			});
		}
		
		@Override
		public int getRowWidth() {
			return (int) (this.client.getWindow().getWidth() * 0.8F);
		}
		
		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
			amount *= (double)this.itemHeight / 2.0 * (Screen.hasControlDown() ? 1.0 : 16.0);
			this.setScrollAmount(this.getScrollAmount() - amount);
			return true;
		}

		private final class Entry extends EntryListWidget.Entry<Entry> {
			private final OrderedText line;
			private final long time;
			
			protected Entry(OrderedText t, long time) {
				this.line = t;
				this.time = time;
			}
			
			@Override
			public void render(MatrixStack ms, int j, int y, int x, 
					int width, int height, int mouseX, int mouseY, boolean hovering, float var10) {
				TextRenderer tr = ChatLogScreen.this.textRenderer;
				tr.drawWithShadow(ms, this.line, x + 4, y, 0xFFFFFFFF);
				DrawableHelper.fill(ms, x + 1, y, x + 3, y + 9, 0xFF31F38B);
				if(hovering) {
					double scale = ChatLogScreen.this.client.getWindow().getScaleFactor();
					int pos = (int) Math.floor(mouseX - 4 * scale);
					Style style = tr.getTextHandler().getStyleAt(line, pos);
					if(style != null) {
						HoverEvent he;
						boolean hasHoverText = false;
						if((he = style.getHoverEvent()) != null) {
							if(he.getAction() == HoverEvent.Action.SHOW_TEXT) {
								hasHoverText = true;
								this.renderToolTip(ms, tr, he.getValue(HoverEvent.Action.SHOW_TEXT), mouseX, mouseY);
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
					
					String time = (this.time == 0L) ? "UNKNOWN TIME" : Instant.ofEpochMilli(this.time).toString();
					if(mouseX - x < 4) {
						this.renderToolTip(ms, tr, time, mouseX, mouseY - 12);
					}
				}
			}
			
			private void renderToolTip(MatrixStack ms, TextRenderer tr, String value, int mouseX, int mouseY) {
				ChatLogScreen.this.renderOrderedTooltip(ms, 
						ChatMessages.breakRenderedChatMessageLines(new LiteralText(value), width / 4, tr), 
						mouseX, mouseY);
			}

			private void renderToolTip(MatrixStack ms, TextRenderer tr, Text text, int mouseX, int mouseY) {
				ChatLogScreen.this.renderOrderedTooltip(ms, 
						ChatMessages.breakRenderedChatMessageLines(text, width / 4, tr), 
						mouseX, mouseY);
			}
		}
	}
}
