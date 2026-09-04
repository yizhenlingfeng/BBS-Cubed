package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.BoneCollapseHandler;
import gbeic.bbsplusplus.api.PoseTabStateProvider;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 给 replay 编辑器 pose 展开后的<b>骨骼行</b>加"折叠子分组"能力（CML 功能移植）。
 *
 * <p>FS 的 dope sheet 已有 pose 轨道级展开（poseTabRoots/expandedPoseTabs +
 * 行右侧开关，保持原样），本 mixin 补齐骨骼行自身的层级折叠：
 * <ul>
 *   <li>{@code configurePoseTabs} HEAD —— 从层级序骨骼列表+深度预计算每行的
 *       "祖先链"与"是否有子骨骼"（O(1) 查询，避免每帧 O(n²)）；</li>
 *   <li>{@code isVisible} RETURN —— 任一祖先骨骼被折叠则隐藏该行；布局
 *       （calculateLayout/sheetYCache）、点击、渲染全都经 isVisible，一处
 *       过滤全链生效；</li>
 *   <li>{@code getSheetIndent} RETURN —— 有子骨骼的行文字右移 20px，给左侧
 *       折叠箭头腾位（对齐 CML：箭头 16px + 4px 间距，位于缩进区、文字之前）；</li>
 *   <li>{@code renderSheetLabel} TAIL —— 在文字左侧画 CML 同款 chevron 图标
 *       （collapsed=›，expanded=⌄；像素取自 CML 图集 (0,160)/(16,160)，FS 图集
 *       无此图标，故随插件自带 32x16 小纹理），并缓存箭头 x 供点击判定；</li>
 *   <li>折叠箭头点击 —— 经 {@link BoneCollapseHandler} duck 接口由
 *       {@code UIKeyframesMixin} 在事件链上游（subMouseClicked，先于任何
 *       dope sheet 层拦截，如 BBS-PoseCurve-Addon 的打开曲线）调用；命中则
 *       切换折叠并清除被隐藏后代行的关键帧选择（对齐 togglePoseTab 行为）。</li>
 * </ul></p>
 *
 * <p>折叠状态以 sheet id（"pose:bone" 形式，跨列表重建稳定）记忆于本实例，
 * dope sheet 生命周期内保持。</p>
 */
@Mixin(value = UIKeyframeDopeSheet.class, priority = 2000, remap = false)
public abstract class UIKeyframeDopeSheetMixin implements BoneCollapseHandler, PoseTabStateProvider
{
    /* CML 折叠图标 16px + 4px 间距（对齐 CML 的 arrow.w + 4 文字偏移） */
    @Unique
    private static final int bbspp_cml$ARROW_ROOM = 20;

    @Unique
    private static final Link bbspp_cml$COLLAPSE_TEXTURE = Link.assets("bbspp/textures/collapse.png");

    @Unique
    private static final Icon bbspp_cml$COLLAPSED = new Icon(bbspp_cml$COLLAPSE_TEXTURE, "bbspp_collapsed", 0, 0, 16, 16, 32, 16);

    @Unique
    private static final Icon bbspp_cml$UNCOLLAPSED = new Icon(bbspp_cml$COLLAPSE_TEXTURE, "bbspp_uncollapsed", 16, 0, 16, 16, 32, 16);

    @Shadow(remap = false)
    private Map<UIKeyframeSheet, Integer> sheetYCache;

    @Shadow(remap = false)
    private List<UIKeyframeElement> elements;

    @Shadow(remap = false)
    private List<UIKeyframeSheet> sheets;

    @Shadow(remap = false)
    private Map<UIKeyframeSheet, UIKeyframeSheet> poseTabRoots;

    @Shadow(remap = false)
    private Set<UIKeyframeSheet> expandedPoseTabs;

    @Shadow(remap = false)
    private double trackHeight;

    @Shadow(remap = false)
    public abstract int getDopeSheetY(UIKeyframeSheet sheet);

    @Shadow(remap = false)
    protected abstract void updateScrollSize();

    @Shadow(remap = false)
    protected abstract int getSheetIndent(UIKeyframeSheet sheet);

    @Unique
    private final Set<String> bbspp_cml$collapsedBoneIds = new HashSet<>();

    @Unique
    private final Map<UIKeyframeSheet, List<String>> bbspp_cml$ancestorIds = new HashMap<>();

    @Unique
    private final Set<UIKeyframeSheet> bbspp_cml$boneParents = new HashSet<>();

    @Unique
    private final Map<UIKeyframeSheet, List<UIKeyframeSheet>> bbspp_cml$descendants = new HashMap<>();

    /* 渲染时记录每个骨骼父行的箭头 x（嵌套 group 的 offset 只有渲染期可知），点击判定用 */
    @Unique
    private final Map<UIKeyframeSheet, Integer> bbspp_cml$arrowX = new HashMap<>();

    @Override
    public boolean bbspp_cml$isExpandedPoseChild(UIKeyframeSheet sheet)
    {
        UIKeyframeSheet root = this.poseTabRoots.get(sheet);

        if (root == null || !this.expandedPoseTabs.contains(root))
        {
            return false;
        }

        if (!bbspp_cml$collapseEnabled())
        {
            return true;
        }

        List<String> ancestors = this.bbspp_cml$ancestorIds.get(sheet);

        if (ancestors != null)
        {
            for (String ancestor : ancestors)
            {
                if (this.bbspp_cml$collapsedBoneIds.contains(ancestor))
                {
                    return false;
                }
            }
        }

        return true;
    }

    @Inject(method = "configurePoseTabs", at = @At("HEAD"), remap = false)
    private void bbspp_cml$indexBoneHierarchy(Map<UIKeyframeSheet, List<UIKeyframeSheet>> tabs, Map<UIKeyframeSheet, Integer> depths, Set<String> expandedPoseIds, CallbackInfo ci)
    {
        this.bbspp_cml$placePoseChildrenAfterParents(tabs);
        this.bbspp_cml$ancestorIds.clear();
        this.bbspp_cml$boneParents.clear();
        this.bbspp_cml$descendants.clear();
        this.bbspp_cml$arrowX.clear();

        for (List<UIKeyframeSheet> bones : tabs.values())
        {
            /* 层级序列表 + 深度 → 用栈重建父链 */
            List<UIKeyframeSheet> stack = new ArrayList<>();

            for (int i = 0; i < bones.size(); i++)
            {
                UIKeyframeSheet bone = bones.get(i);
                int depth = Math.max(0, depths.getOrDefault(bone, 0));

                while (!stack.isEmpty() && Math.max(0, depths.getOrDefault(stack.get(stack.size() - 1), 0)) >= depth)
                {
                    stack.remove(stack.size() - 1);
                }

                if (!stack.isEmpty())
                {
                    List<String> ancestors = new ArrayList<>();

                    for (UIKeyframeSheet ancestor : stack)
                    {
                        ancestors.add(ancestor.id);
                        this.bbspp_cml$descendants.computeIfAbsent(ancestor, (k) -> new ArrayList<>()).add(bone);
                    }

                    this.bbspp_cml$ancestorIds.put(bone, ancestors);
                }

                int next = i + 1;

                if (next < bones.size() && Math.max(0, depths.getOrDefault(bones.get(next), 0)) > depth)
                {
                    this.bbspp_cml$boneParents.add(bone);
                }

                stack.add(bone);
            }
        }
    }

    /**
     * Bone tracks are appended after all regular tracks by BBS. Move only the
     * tracks that belong to a pose tab so every expanded bone row starts right
     * below its pose row, while preserving the order of all unrelated rows.
     */
    @Unique
    private void bbspp_cml$placePoseChildrenAfterParents(Map<UIKeyframeSheet, List<UIKeyframeSheet>> tabs)
    {
        if (tabs.isEmpty())
        {
            return;
        }

        Set<UIKeyframeSheet> children = new HashSet<>();

        for (List<UIKeyframeSheet> bones : tabs.values())
        {
            children.addAll(bones);
        }

        List<UIKeyframeSheet> reorderedSheets = new ArrayList<>(this.sheets.size());

        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (children.contains(sheet))
            {
                continue;
            }

            reorderedSheets.add(sheet);

            List<UIKeyframeSheet> bones = tabs.get(sheet);

            if (bones != null)
            {
                reorderedSheets.addAll(bones);
            }
        }

        /* Keep malformed/unmatched input lossless instead of dropping rows. */
        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (!reorderedSheets.contains(sheet))
            {
                reorderedSheets.add(sheet);
            }
        }

        this.sheets.clear();
        this.sheets.addAll(reorderedSheets);

        Set<UIKeyframeElement> directElements = new HashSet<>(this.elements);
        List<UIKeyframeElement> reorderedElements = new ArrayList<>(this.elements.size());

        for (UIKeyframeElement element : this.elements)
        {
            if (element instanceof UIKeyframeSheet sheet && children.contains(sheet))
            {
                continue;
            }

            reorderedElements.add(element);

            if (element instanceof UIKeyframeSheet sheet)
            {
                List<UIKeyframeSheet> bones = tabs.get(sheet);

                if (bones != null)
                {
                    for (UIKeyframeSheet bone : bones)
                    {
                        if (directElements.contains(bone))
                        {
                            reorderedElements.add(bone);
                        }
                    }
                }
            }
        }

        for (UIKeyframeElement element : this.elements)
        {
            if (!reorderedElements.contains(element))
            {
                reorderedElements.add(element);
            }
        }

        this.elements.clear();
        this.elements.addAll(reorderedElements);
    }

    /** BBS_snow 设置里的折叠功能总开关(关闭时不隐藏行/不缩进/不画箭头/不拦点击,等效全展开) */
    @Unique
    private static boolean bbspp_cml$collapseEnabled()
    {
        return CMLSettings.poseKeyframeCollapse == null || CMLSettings.poseKeyframeCollapse.get();
    }

    @Inject(method = "isVisible", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$hideCollapsedDescendants(UIKeyframeSheet sheet, CallbackInfoReturnable<Boolean> cir)
    {
        if (!bbspp_cml$collapseEnabled() || !cir.getReturnValueZ() || this.bbspp_cml$collapsedBoneIds.isEmpty())
        {
            return;
        }

        List<String> ancestors = this.bbspp_cml$ancestorIds.get(sheet);

        if (ancestors == null)
        {
            return;
        }

        for (String ancestor : ancestors)
        {
            if (this.bbspp_cml$collapsedBoneIds.contains(ancestor))
            {
                cir.setReturnValue(false);

                return;
            }
        }
    }

    @Inject(method = "getSheetIndent", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$reserveArrowRoom(UIKeyframeSheet sheet, CallbackInfoReturnable<Integer> cir)
    {
        if (bbspp_cml$collapseEnabled() && this.bbspp_cml$boneParents.contains(sheet))
        {
            cir.setReturnValue(cir.getReturnValueI() + bbspp_cml$ARROW_ROOM);
        }
    }

    @Override
    public boolean bbspp_cml$handleCollapseClick(UIContext context)
    {
        if (!bbspp_cml$collapseEnabled())
        {
            return false;
        }

        for (UIKeyframeSheet sheet : this.bbspp_cml$boneParents)
        {
            Integer arrowX = this.bbspp_cml$arrowX.get(sheet);

            /* sheetYCache 只含当前可见行（隐藏行的箭头不可点） */
            if (arrowX == null || !this.sheetYCache.containsKey(sheet))
            {
                continue;
            }

            int rowY = this.getDopeSheetY(sheet);
            int arrowY = rowY + (int) this.trackHeight / 2 - 8;

            if (context.mouseX >= arrowX && context.mouseX < arrowX + 16 && context.mouseY >= arrowY && context.mouseY < arrowY + 16
                && context.mouseY >= rowY && context.mouseY < rowY + (int) this.trackHeight)
            {
                this.bbspp_cml$toggleBoneCollapse(sheet);
                this.updateScrollSize();

                return true;
            }
        }

        return false;
    }

    @Unique
    private void bbspp_cml$toggleBoneCollapse(UIKeyframeSheet sheet)
    {
        if (!this.bbspp_cml$collapsedBoneIds.remove(sheet.id))
        {
            this.bbspp_cml$collapsedBoneIds.add(sheet.id);

            /* 对齐 togglePoseTab：被隐藏的后代行清除关键帧选择 */
            List<UIKeyframeSheet> descendants = this.bbspp_cml$descendants.get(sheet);

            if (descendants != null)
            {
                for (UIKeyframeSheet descendant : descendants)
                {
                    descendant.selection.clear();
                }
            }
        }
    }

    @Inject(method = "renderSheetLabel", at = @At("TAIL"), remap = false)
    private void bbspp_cml$renderBoneCollapseArrow(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int offset, int y, int w, CallbackInfo ci)
    {
        /* 视野外的行在方法开头 early-return，不会到 TAIL */
        if (!bbspp_cml$collapseEnabled() || !this.bbspp_cml$boneParents.contains(sheet) || this.trackHeight < 12D)
        {
            return;
        }

        /* 文字 x = lx + LABEL_TEXT_LEFT(5) + offset + 注入后缩进（已含 +ARROW_ROOM），
         * 箭头画在文字左侧腾出的 20px 空位里（CML 布局：箭头在缩进区、文字之前）。 */
        int textX = area.x + 5 + offset + this.getSheetIndent(sheet);
        int arrowX = textX - bbspp_cml$ARROW_ROOM;
        int my = y + (int) this.trackHeight / 2;
        boolean collapsed = this.bbspp_cml$collapsedBoneIds.contains(sheet.id);
        Icon arrow = collapsed ? bbspp_cml$COLLAPSED : bbspp_cml$UNCOLLAPSED;

        context.batcher.icon(arrow, arrowX, my - 8);
        this.bbspp_cml$arrowX.put(sheet, arrowX);
    }
}
