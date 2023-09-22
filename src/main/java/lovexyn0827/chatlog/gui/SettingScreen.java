package lovexyn0827.chatlog.gui;

import java.lang.reflect.Field;

import lovexyn0827.chatlog.config.Option;
import lovexyn0827.chatlog.config.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public final class SettingScreen extends Screen {
	private OptionListWidget optionList;
	
	protected SettingScreen() {
		super(new LiteralText("Settings"));
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
		
		this.addChild(this.optionList);
	}
	
	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
		this.optionList.render(matrices, mouseX, mouseY, delta);
	}
	
	@Override
	public void onClose() {
		this.client.openScreen(new SessionListScreen());
	}
	
	private final class OptionListWidget extends EntryListWidget<OptionListWidget.Entry> {
		public OptionListWidget(MinecraftClient client) {
			super(client, SettingScreen.this.width, SettingScreen.this.height, 
					14, SettingScreen.this.height - 16, 18);
		}
		
		@Override
		public int getRowWidth() {
			return (int) (this.client.getWindow().getScaledWidth() * 0.8F);
		}
		
		protected int addOption(Field f) {
			Entry e = new Entry(f);
			SettingScreen.this.addChild(e.textField);
			return this.addEntry(e);
		}

		private final class Entry extends EntryListWidget.Entry<Entry> {
			private final LiteralText name;
			protected final TextFieldWidget textField;
			
			protected Entry(Field f) {
				this.name = new LiteralText(f.getName());
				int width = SettingScreen.this.client.getWindow().getScaledWidth();
				this.textField = new TextFieldWidget(SettingScreen.this.textRenderer, 
						(int) (width * 0.35), 1, 
						(int) (width * 0.5), 14, this.name) {
					@Override
					protected void setFocused(boolean focused) {
						super.setFocused(focused);
						Options.set(Entry.this.name.asString(), this.getText());
					}
				};
				try {
					this.textField.setText(f.get(null).toString());
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
				
				this.textField.setChangedListener((s) -> {
					Options.set(Entry.this.name.asString(), s);
				});
			}

			@Override
			public void render(MatrixStack ms, int i, int y, int x, 
					int width, int height, int var7, int var8, boolean var9, float var10) {
				SettingScreen.this.textRenderer.draw(ms, this.name, x, y + 5, 0xFF31F38B);
				this.textField.y = y;
				this.textField.render(ms, var7, var8, var10);
			}
		}
	}
}
