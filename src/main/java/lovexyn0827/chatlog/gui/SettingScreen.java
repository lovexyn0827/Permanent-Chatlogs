package lovexyn0827.chatlog.gui;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import lovexyn0827.chatlog.config.Option;
import lovexyn0827.chatlog.config.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class SettingScreen extends Screen {
	private OptionListWidget optionList;
	
	protected SettingScreen() {
		super(Text.literal("Settings"));
	}

	@Override
	public void init() {
		this.optionList = new OptionListWidget(this.client);
		for(Field f : Options.class.getDeclaredFields()) {
			Option o = f.getAnnotation(Option.class);
			if(o == null) {
				continue;
			}
			
			this.optionList.addOption(f);
		}
		
		this.addDrawableChild(this.optionList);
	}
	
	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackgroundTexture(ctx);
		super.render(ctx, mouseX, mouseY, delta);
		this.optionList.render(ctx, mouseX, mouseY, delta);
	}
	
	@Override
	public void close() {
		this.client.setScreen(new SessionListScreen());
	}
	
	private final class OptionListWidget extends EntryListWidget<OptionListWidget.Entry> {
		public OptionListWidget(MinecraftClient client) {
			super(client, SettingScreen.this.width, SettingScreen.this.height - 32, 16, 18);
		}
		
		@Override
		public int getRowWidth() {
			return (int) (this.client.getWindow().getScaledWidth() * 0.8F);
		}
		
		protected int addOption(Field f) {
			Entry e = new Entry(f);
			SettingScreen.this.addDrawableChild(e.textField);
			return this.addEntry(e);
		}

		private final class Entry extends ElementListWidget.Entry<Entry> {
			private final Text name;
			protected final TextFieldWidget textField;
			
			protected Entry(Field f) {
				this.name = Text.literal(f.getName());
				int width = SettingScreen.this.client.getWindow().getScaledWidth();
				this.textField = new TextFieldWidget(SettingScreen.this.textRenderer, 
						(int) (width * 0.35), 1, 
						(int) (width * 0.5), 14, this.name) {
					@Override
					public void setFocused(boolean focused) {
						super.setFocused(focused);
						Options.set(Entry.this.name.getString(), this.getText());
					}
				};
				try {
					this.textField.setText(f.get(null).toString());
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
				
				this.textField.setChangedListener((s) -> {
					Options.set(Entry.this.name.getString(), s);
				});
			}

			@Override
			public void render(DrawContext ctx, int i, int y, int x, 
					int width, int height, int var7, int var8, boolean var9, float var10) {
				ctx.drawText(SettingScreen.this.textRenderer, this.name, x, y + 5, 0xFF31F38B, false);
				this.textField.setY(y);
				this.textField.render(ctx, var7, var8, var10);
			}

			@Override
			public List<? extends Element> children() {
				return new ArrayList<>();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return new ArrayList<>();
			}
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder var1) {
		}
	}
}
