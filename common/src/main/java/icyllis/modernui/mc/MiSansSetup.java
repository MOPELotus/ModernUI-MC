package icyllis.modernui.mc;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** First-run ModernUI screen, using the bundled Source Han Sans until restart. */
public final class MiSansSetup extends Fragment {
    public static volatile boolean required;
    private static Path directory;
    private static SimpleScreen screen;
    private static boolean checked;
    public static synchronized void ensureInstalled(Path path) {
        if (checked) return;
        directory = path;
        required = !MiSansInstaller.missing(path).isEmpty();
        checked = true;
    }
    public static void tick(Minecraft mc) {
        if (!required || mc.gui.overlay() != null) return;
        if (screen == null) {
            screen = new SimpleScreen(new MiSansSetup(), null, null, Component.literal("安装 MiSans")) {
                @Override public void onClose() {}
                @Override public void onBackPressed() {}
            };
        }
        if (mc.gui.screen() != screen) mc.gui.setScreen(screen);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet state) {
        var ctx = requireContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 24, 32, 24);
        layout.setBackground(new ColorDrawable(0xff181b22));
        TextView title = new TextView(ctx);
        title.setText("安装 MiSans 字体");
        title.setTextSize(26);
        title.setTextColor(0xffeeeeee);
        layout.addView(title);
        TextView status = new TextView(ctx);
        status.setText("缺少所需字体，请下载后重启客户端。\n将从小米官网下载四套字体，安装期间请勿关闭游戏。\n点击“下载”即表示同意 MiSans 字体知识产权许可协议。\n临时显示字体：思源黑体（SIL OFL）。");
        status.setTextColor(0xffdddddd);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);
        Button license = new Button(ctx);
        license.setText("查看 MiSans 许可协议");
        license.setOnClickListener(v -> com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(MiSansInstaller.LICENSE_PAGE)));
        layout.addView(license);
        Button download = new Button(ctx);
        download.setText("下载");
        layout.addView(download);
        Button exit = new Button(ctx);
        exit.setText("退出客户端");
        exit.setOnClickListener(v -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop()));
        layout.addView(exit);
        download.setOnClickListener(v -> {
            download.setEnabled(false);
            exit.setEnabled(false);
            CompletableFuture.runAsync(() -> {
                try {
                    MiSansInstaller.install(directory, text -> status.post(() -> status.setText(text)));
                    status.post(() -> {
                        status.setText("四套字体已安装并校验完成。\n请退出并重新启动客户端。");
                        exit.setText("退出并手动重启");
                        exit.setEnabled(true);
                    });
                } catch (Exception e) {
                    ModernUIMod.LOGGER.error("MiSans installation failed", e);
                    status.post(() -> {
                        status.setText("安装失败：" + e.getMessage() + "\n请检查网络后重试。");
                        download.setText("重试下载");
                        download.setEnabled(true);
                        exit.setEnabled(true);
                    });
                }
            });
        });
        return layout;
    }
}
