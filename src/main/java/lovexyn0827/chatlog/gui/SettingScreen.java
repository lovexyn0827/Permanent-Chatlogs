package lovexyn0827.chatlog.gui;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import lovexyn0827.chatlog.config.Option;
import lovexyn0827.chatlog.config.OptionType;
import lovexyn0827.chatlog.config.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ChatMessages;
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
			if (f.getAnnotationsByType(Option.class)[0].type() == OptionType.BOOLEAN) {
				return this.addEntry(new BooleanEntry(f));
			} else {
				return this.addEntry(new TextEntry(f));
			}
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder var1) {
		}
		
		private class Entry extends ElementListWidget.Entry<Entry> {
			protected final Text name;
			
			protected Entry(Field f) {
				this.name = Text.literal(f.getName());
			}
			
			@Override
			public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
				int y = this.getY();
				int xOffset = ((int) (this.getWidth() * 0.25));
				ctx.drawText(SettingScreen.this.textRenderer, this.name, xOffset, y + 5, 0xFF31F38B, false);
				if (hovered && mouseX < ctx.getScaledWindowWidth() * 0.5) {
					ctx.drawOrderedTooltip(SettingScreen.this.textRenderer, 
							ChatMessages.breakRenderedChatMessageLines(
									Options.getToolTip(this.name.getString()), this.getWidth() / 2, 
									SettingScreen.this.textRenderer), 
							mouseX, mouseY);
				}
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
		
		private class TextEntry extends Entry {
			private final TextFieldWidget valueSelector;
			
			protected TextEntry(Field f) {
				super(f);
				int width = SettingScreen.this.client.getWindow().getScaledWidth();
				this.valueSelector = new TextFieldWidget(SettingScreen.this.textRenderer, 
						(int) (width * 0.55), 1, 
						(int) (width * 0.20), 14, this.name);
				try {
					this.valueSelector.setText(f.get(null).toString());
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				
				this.valueSelector.setChangedListener((s) -> {
					Options.set(this.name.getString(), s);
				});
				SettingScreen.this.addDrawableChild(this.valueSelector);
			}
			
			@Override
			public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
				int y = this.getY();
				super.render(ctx, mouseX, mouseY, hovered, deltaTicks);
				this.valueSelector.setY(y);
				this.valueSelector.render(ctx, mouseX, mouseY, deltaTicks);
			}
		}
		
		private class BooleanEntry extends Entry {
			private final CheckboxWidget valueSelector;
			
			protected BooleanEntry(Field f) {
				super(f);
				boolean toggled;
				try {
					toggled = Boolean.valueOf(f.get(null).toString());
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				
				this.valueSelector = CheckboxWidget.builder(Text.empty(), SettingScreen.this.textRenderer)
						.pos((int) (width * 0.75) - 16, 1)
						.checked(toggled)
						.callback((widget, checked) -> {
							Options.set(BooleanEntry.this.name.getString(), Boolean.toString(checked));
						})
						.build();
				SettingScreen.this.addDrawableChild(this.valueSelector);
			}
			
			@Override
			public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
				int y = this.getY();
				super.render(ctx, mouseX, mouseY, hovered, deltaTicks);
				this.valueSelector.setY(y);
				this.valueSelector.render(ctx, mouseX, mouseY, deltaTicks);
			}
		}
	}
}
