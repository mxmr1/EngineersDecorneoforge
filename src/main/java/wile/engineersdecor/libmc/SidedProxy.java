/*
 * @file SidedProxy.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General client/server sidedness selection proxy (updated for NeoForge 21.1.209).
 */
package wile.engineersdecor.libmc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader; // ✅ современный способ определить сторону
import javax.annotation.Nullable;
import java.util.Optional;

public class SidedProxy
{
    @Nullable
    public static Player getPlayerClientSide()
    { return proxy.getPlayerClientSide(); }

    @Nullable
    public static Level getWorldClientSide()
    { return proxy.getWorldClientSide(); }

    @Nullable
    public static Minecraft mc()
    { return proxy.mc(); }

    public static Optional<Boolean> isCtrlDown()
    { return proxy.isCtrlDown(); }

    public static Optional<Boolean> isShiftDown()
    { return proxy.isShiftDown(); }

    public static Optional<String> getClipboard()
    { return proxy.getClipboard(); }

    public static boolean setClipboard(String text)
    { return proxy.setClipboard(text); }

    // --------------------------------------------------------------------------------------------------------

    // ✅ DistExecutor больше нет — теперь просто проверяем Dist через FMLLoader
    private static final ISidedProxy proxy = createProxy();

    private static ISidedProxy createProxy() {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            return new ClientProxy();
        } else {
            return new ServerProxy();
        }
    }

    // --------------------------------------------------------------------------------------------------------

    private interface ISidedProxy
    {
        default @Nullable Player getPlayerClientSide() { return null; }
        default @Nullable Level getWorldClientSide() { return null; }
        default @Nullable Minecraft mc() { return null; }
        default Optional<Boolean> isCtrlDown() { return Optional.empty(); }
        default Optional<Boolean> isShiftDown() { return Optional.empty(); }
        default Optional<String> getClipboard() { return Optional.empty(); }
        default boolean setClipboard(String text) { return false; }
    }

    private static final class ClientProxy implements ISidedProxy
    {
        @Override
        public @Nullable Player getPlayerClientSide() { return Minecraft.getInstance().player; }

        @Override
        public @Nullable Level getWorldClientSide() { return Minecraft.getInstance().level; }

        @Override
        public @Nullable Minecraft mc() { return Minecraft.getInstance(); }

        @Override
        public Optional<Boolean> isCtrlDown() { return Optional.of(Auxiliaries.isCtrlDown()); }

        @Override
        public Optional<Boolean> isShiftDown() { return Optional.of(Auxiliaries.isShiftDown()); }

        @Override
        public Optional<String> getClipboard() {
            Minecraft mc = mc();
            if (mc == null) return Optional.empty();
            return Optional.of(net.minecraft.client.gui.font.TextFieldHelper.getClipboardContents(mc));
        }

        @Override
        public boolean setClipboard(String text) {
            Minecraft mc = mc();
            if (mc == null) return false;
            net.minecraft.client.gui.font.TextFieldHelper.setClipboardContents(mc, text);
            return true;
        }
    }

    private static final class ServerProxy implements ISidedProxy { }

}
