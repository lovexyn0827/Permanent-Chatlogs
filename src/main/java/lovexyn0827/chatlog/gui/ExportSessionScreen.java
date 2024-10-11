package lovexyn0827.chatlog.gui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import lovexyn0827.chatlog.Session;
import lovexyn0827.chatlog.Session.Summary;
import lovexyn0827.chatlog.export.ExportConfig;
import lovexyn0827.chatlog.export.FormatAdapter;
import lovexyn0827.chatlog.i18n.I18N;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.util.Util;

public class ExportSessionScreen extends Screen {
	private static final File EXPORT_FOLDER = Util.make(() -> {
		File f = new File("chatlogs/export");
		if (f.isDirectory()) {
			return f;
		}
		
		if (!f.mkdir()) {
			return null;
		} else {
			return f;
		}
	});
	private TextFieldWidget fileName;
	private CyclingButtonWidget<FormatAdapter.Factory<?>> format;
	private CyclingButtonWidget<Boolean> openAfterExport;
	private final Summary sessionMeta;
	
	protected ExportSessionScreen(Summary summary) {
		super(I18N.translateAsText("gui.export"));
		this.sessionMeta = summary;
	}
	
	@Override
	protected void init() {
		this.fileName = new TextFieldWidget(this.textRenderer, 
				(int) (width * 0.3F), (int) (height * 0.25F), 
				(int) (width * 0.4F), 14, 
				I18N.translateAsText("gui.export.name"));
		this.fileName.setText(Util.getFormattedCurrentTime());
		this.format = CyclingButtonWidget.<FormatAdapter.Factory<?>>builder(FormatAdapter.Factory::getDisplayedText)
				.values(FormatAdapter.FORMAT_FACTORIES)
				.initially(FormatAdapter.FORMAT_FACTORIES.get(0))
				.build((int) (width * 0.3F), (int) (height * 0.25F) + 25, 
						(int) (width * 0.4F), 20, I18N.translateAsText("gui.export.format"));
		this.openAfterExport = CyclingButtonWidget.onOffBuilder(ScreenTexts.YES, ScreenTexts.NO)
				.initially(false)
				.build((int) (width * 0.3F), (int) (height * 0.25F) + 50, 
						(int) (width * 0.4F), 20, I18N.translateAsText("gui.export.open"));
		this.addDrawableChild(this.fileName);
		this.addDrawableChild(this.format);
		this.addDrawableChild(this.openAfterExport);
		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, (btn) -> this.export())
				.dimensions((int) (width * 0.3F), (int) (height * 0.25F) + 75, 
						(int) (width * 0.19F), 20)
				.build());
		this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, (btn) -> this.close())
				.dimensions((int) (width * 0.51F), (int) (height * 0.25F) + 75, 
						(int) (width * 0.19F), 20)
				.build());
	}

	private void export() {
		if (EXPORT_FOLDER == null) {
			SystemToast warning = new SystemToast(new SystemToast.Type(), 
					I18N.translateAsText("gui.export.nodir"), 
					I18N.translateAsText("gui.export.nodir.desc"));
			MinecraftClient.getInstance().getToastManager().add(warning);
			return;
		}
		
		String extension = this.format.getValue().getExtension();
		Session session = this.sessionMeta.load();
		if (session == null) {
			SystemToast warning = new SystemToast(new SystemToast.Type(), 
					I18N.translateAsText("gui.sload.failure"), 
					I18N.translateAsText("gui.sload.failure.desc"));
			MinecraftClient.getInstance().getToastManager().add(warning);
		}
		
		File target = new File(EXPORT_FOLDER, this.fileName.getText() + "." + extension);
		try (BufferedWriter w = new BufferedWriter(new FileWriter(target))) {
			FormatAdapter fmt = this.format.getValue().create(
					w, this.sessionMeta, session, new ExportConfig(false, true));
			fmt.write();
		} catch (Exception e) {
			e.printStackTrace();
			SystemToast warning = new SystemToast(new SystemToast.Type(), 
					I18N.translateAsText("gui.export.fail"), 
					I18N.translateAsText("gui.export.fail.desc"));
			MinecraftClient.getInstance().getToastManager().add(warning);
			return;
		}
		
		if (this.openAfterExport.getValue()) {
			Util.getOperatingSystem().open(target);
		}
		
		this.close();
	}
	
	@Override
	public void close() {
		this.client.setScreen(new SessionListScreen());
	}
}
