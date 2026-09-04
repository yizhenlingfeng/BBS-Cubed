package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * ModelForm 的"variable 动作模型互通"开关访问接口(由 {@code ModelFormMixin}
 * 实现)。默认 false = 修复后的隔离行为(每个模型有自己的 molang variable.*
 * 变量世界);true = 回到 BBS 原版的全局共享行为(同名变量跨模型互通)。
 */
public interface MolangSharedProvider
{
    public ValueBoolean bbspp_cml$getMolangShared();
}
