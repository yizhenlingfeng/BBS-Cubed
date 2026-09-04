package gbeic.bbsplusplus.utils;

import gbeic.bbsplusplus.api.MolangSharedProvider;
import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.math.molang.MolangParser;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * molang variable.* 变量的按模型隔离(修复 BBS 的同名变量跨模型互通 bug):
 * BBS 全局唯一 MolangParser,动画表达式编译期绑定同一 Variable 实例,任何
 * 模型的赋值都会串到其他模型。这里在每个模型应用动作前后做换入/换出 ——
 * 换入时备份全局值并写入该模型自己保存的值,换出时把新值存回该模型的
 * 作用域并恢复全局备份,对全局零污染。
 *
 * <p>form 上开了"variable 动作模型互通"(molangShared)的模型跳过换入换出,
 * 保持原版共享行为;两个都开互通的模型之间照旧互通。作用域按
 * {@link IModelInstance} 弱引用存放,模型卸载自动清理。仅处理
 * "variable." 前缀的自定义变量(query.* 由 MolangHelper 每帧刷新,天然
 * per-entity,不需要也不能动)。变量注册可能由联机同步与渲染重叠触发，
 * 因此每次换入/换出都基于变量表的稳定快照。</p>
 */
public class MolangVariableScopes
{
    private static final Map<IModelInstance, Map<String, Double>> SCOPES = new WeakHashMap<>();

    private static Map<String, Double> backup;
    private static IModelInstance current;

    public static void beforeApply(IModelInstance armature)
    {
        if (current != null || armature == null || isShared(armature))
        {
            return;
        }

        MolangParser parser = getParser(armature);

        if (parser == null)
        {
            return;
        }

        Map<String, Double> scope = SCOPES.computeIfAbsent(armature, (key) -> new HashMap<>());

        current = armature;
        backup = new HashMap<>();

        for (Variable variable : snapshot(parser))
        {
            String name = variable.getName();

            if (!name.startsWith("variable."))
            {
                continue;
            }

            backup.put(name, variable.doubleValue());

            Double own = scope.get(name);

            variable.set(own == null ? 0D : own);
        }
    }

    public static void afterApply(IModelInstance armature)
    {
        if (current == null || current != armature)
        {
            return;
        }

        MolangParser parser = getParser(armature);
        Map<String, Double> scope = SCOPES.get(armature);

        if (parser != null && scope != null)
        {
            for (Variable variable : snapshot(parser))
            {
                String name = variable.getName();

                if (!name.startsWith("variable."))
                {
                    continue;
                }

                scope.put(name, variable.doubleValue());

                Double previous = backup.get(name);

                if (previous != null)
                {
                    variable.set(previous);
                }
                else
                {
                    /* The variable was first registered while this model scope was active. */
                    variable.set(0D);
                }
            }
        }

        current = null;
        backup = null;
    }

    private static boolean isShared(IModelInstance armature)
    {
        if (armature instanceof ModelInstance model
            && model.form instanceof ModelForm form
            && form instanceof MolangSharedProvider provider
            && provider.bbspp_cml$getMolangShared() != null)
        {
            return provider.bbspp_cml$getMolangShared().get();
        }

        return false;
    }

    private static MolangParser getParser(IModelInstance armature)
    {
        return armature.getModel() instanceof Model model ? model.parser : null;
    }

    private static List<Variable> snapshot(MolangParser parser)
    {
        return new ArrayList<>(parser.variables.values());
    }
}
