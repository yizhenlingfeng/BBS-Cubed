package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.IMBlockerCompat;
import gbeic.bbsplusplus.client.structure.StructureStickSelection;
import gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * MinecraftClient Mixin
 *
 * 拦截选区工具的右键/左键/长按挖掘，并维护输入法兼容层的窗口焦点状态。
 */

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        IMBlockerCompat.tickPendingRestore();
    }

    /**
     * 注入目标：{@link MinecraftClient#onWindowFocusChanged(boolean)} 的末尾。
     * <p>
     * 当玩家在 BBS 文本输入框中切到其它程序再切回 Minecraft 时，BBS 文本框仍保持焦点，
     * 但不会再次调用文本框的 focus()。这里在窗口重新激活后根据兼容层保存的文本输入焦点状态
     * 重新通知 IMBlocker，避免输入法被继续锁定为英文。
     * </p>
     */
    @Inject(method = "onWindowFocusChanged", at = @At("TAIL"))
    private void bbspp$restoreIMBlockerTextInputState(boolean focused, CallbackInfo ci) {
        IMBlockerCompat.restoreTextInputAfterWindowFocus(focused);
    }

    /**
     * 注入目标：{@link MinecraftClient#doItemUse()} 入口。
     * 注入原因：选区工具用「使用」键做框选，如果不拦住原版逻辑，手里拿着工具对着方块按右键会去触发方块交互。
     * 修改行为：手持 BBS++ 接管的选区工具且没有打开界面时取消原版的物品使用。
     */
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void bbspp$blockStructureStickUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (StructureStickSelection.shouldBlockUse(client) || VFXDestructionWandSelection.shouldBlockUse(client)) {
            ci.cancel();
        }
    }

    /**
     * 注入目标：{@link MinecraftClient#doAttack()} 入口。
     * 注入原因：选区工具用「攻击」键改终点和清除选区，不能真的去打方块或实体。
     * 修改行为：手持 BBS++ 接管的选区工具且没有打开界面时直接返回 true（表示已处理），跳过原版攻击。
     */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void bbspp$blockStructureStickAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (StructureStickSelection.shouldBlockAttack(client) || VFXDestructionWandSelection.shouldBlockAttack(client)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 注入目标：{@link MinecraftClient#handleBlockBreaking(boolean)} 入口。
     * 注入原因：长按攻击键会持续触发挖掘逻辑，选区工具需要长按来清除选区，必须一并拦住。
     * 修改行为：手持 BBS++ 接管的选区工具且没有打开界面时取消挖掘。
     */
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void bbspp$blockStructureStickBreaking(boolean breaking, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (StructureStickSelection.shouldBlockAttack(client) || VFXDestructionWandSelection.shouldBlockAttack(client)) {
            ci.cancel();
        }
    }
}
