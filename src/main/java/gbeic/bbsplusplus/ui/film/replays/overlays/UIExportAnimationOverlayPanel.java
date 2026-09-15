package gbeic.bbsplusplus.ui.film.replays.overlays;

import com.google.gson.JsonObject;
import gbeic.bbsplusplus.ui.film.replays.ExportUIKeys;
import gbeic.bbsplusplus.utils.AnimationFileOperations;
import gbeic.bbsplusplus.utils.IKAnimationExporter;
import gbeic.bbsplusplus.utils.PoseAnimationExporter;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * "导出为动画"弹窗:把选中的 pose 关键帧写进模型目录下的 .animation.json
 * (Bedrock 格式,Blockbench 可直接导入),同时是这些文件的动作管理器。
 *
 * <p>列表把每个动画文件显示为默认折叠的分组(点击切换展开,展开后列出文件内
 * 已保存的动作);上方搜索框同时匹配文件名与动作名(命中动作时强制展开其分组);
 * 再上方一排图标按钮(重命名/删除/复制/粘贴/翻转/打开文件夹)作用于选中的动作,
 * 交互对齐姿势预设面板({@code UIDataContextMenu})。目标文件始终以文件名输入
 * 框为准,选中列表行只是快捷填充。写盘后 BBS 的 assets watchdog 会自动重载
 * 模型,动作立即可用。</p>
 */
public class UIExportAnimationOverlayPanel extends UIOverlayPanel
{
    private static final String SUFFIX = ".animation.json";

    /* 动作剪贴板(静态,跨面板实例存续) */
    private static String clipboardName;
    private static JsonObject clipboardAnimation;

    public UIIcon rename;
    public UIIcon remove;
    public UIIcon copy;
    public UIIcon paste;
    public UIIcon openFolder;

    public UITextbox search;
    public UIAnimationTreeList files;
    public UITextbox fileName;
    public UITextbox animationName;
    public UIToggle loop;
    public UIToggle bakeIK;
    public UIButton export;

    private final ModelForm modelForm;
    private final UIKeyframeSheet sheet;
    private final List<UIKeyframeSheet> boneSheets;
    private final FormProperties properties;
    private final boolean hasIK;

    private final String defaultFileName;
    private final File folder;
    private final List<FileEntry> entries = new ArrayList<>();

    private Row selected;

    /** 整体 pose 轨道导出({@code Keyframe<Pose>}) */
    public UIExportAnimationOverlayPanel(ModelForm modelForm, UIKeyframeSheet sheet)
    {
        this(modelForm, sheet, null, null);
    }

    public UIExportAnimationOverlayPanel(ModelForm modelForm, UIKeyframeSheet sheet, FormProperties properties)
    {
        this(modelForm, sheet, null, properties);
    }

    /** 肢体轨道导出(每条 sheet 是一根骨骼的 {@code Keyframe<PoseTransform>} 轨道) */
    public UIExportAnimationOverlayPanel(ModelForm modelForm, List<UIKeyframeSheet> boneSheets)
    {
        this(modelForm, null, boneSheets, null);
    }

    public UIExportAnimationOverlayPanel(ModelForm modelForm, List<UIKeyframeSheet> boneSheets, FormProperties properties)
    {
        this(modelForm, null, boneSheets, properties);
    }

    private UIExportAnimationOverlayPanel(ModelForm modelForm, UIKeyframeSheet sheet,
        List<UIKeyframeSheet> boneSheets, FormProperties properties)
    {
        super(ExportUIKeys.TITLE);

        this.modelForm = modelForm;
        this.sheet = sheet;
        this.boneSheets = boneSheets;
        this.properties = properties;

        ModelInstance model = ModelFormRenderer.getModel(modelForm);
        this.hasIK = IKAnimationExporter.hasIK(model, modelForm);
        String modelId = model == null ? "" : model.id;
        int slash = modelId.lastIndexOf('/');

        this.defaultFileName = slash >= 0 ? modelId.substring(slash + 1) : (modelId.isEmpty() ? "exported" : modelId);
        this.folder = modelId.isEmpty() ? null : BBSMod.getProvider().getFile(Link.assets(ModelManager.MODELS_PREFIX + modelId));

        /* 图标操作行 */
        this.rename = new UIIcon(Icons.EDIT, (b) -> this.renameAnimation());
        this.rename.tooltip(UIKeys.GENERAL_RENAME);
        this.remove = new UIIcon(Icons.REMOVE, (b) -> this.removeAnimation());
        this.remove.tooltip(UIKeys.GENERAL_REMOVE);
        this.copy = new UIIcon(Icons.COPY, (b) -> this.copyAnimation());
        this.copy.tooltip(ExportUIKeys.COPY);
        this.paste = new UIIcon(Icons.PASTE, (b) -> this.pasteAnimation());
        this.paste.tooltip(ExportUIKeys.PASTE);
        this.openFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            if (this.folder != null)
            {
                UIUtils.openFolder(this.folder);
            }
        });
        this.openFolder.tooltip(ExportUIKeys.OPEN_FOLDER);

        /* 搜索 + 树形列表 */
        this.search = new UITextbox((t) -> this.rebuild());
        this.search.placeholder(ExportUIKeys.SEARCH);

        this.files = new UIAnimationTreeList((l) -> this.pickRow(l.isEmpty() ? null : l.get(0)));
        this.files.h(UIStringList.DEFAULT_HEIGHT * 7);
        this.files.background();

        /* 导出目标 */
        this.fileName = new UITextbox();
        this.fileName.path();
        this.fileName.setText(this.defaultFileName);
        this.fileName.tooltip(ExportUIKeys.FILE_NAME);
        this.fileName.placeholder(ExportUIKeys.FILE_NAME);

        this.animationName = new UITextbox();
        this.animationName.setText("pose_" + this.defaultFileName);
        this.animationName.tooltip(ExportUIKeys.ANIMATION_NAME);
        this.animationName.placeholder(ExportUIKeys.ANIMATION_NAME);

        this.loop = new UIToggle(ExportUIKeys.LOOP, (b) -> {});
        this.bakeIK = new UIToggle(ExportUIKeys.BAKE_IK, this.hasIK, (b) -> {});
        this.bakeIK.setEnabled(this.hasIK);
        this.bakeIK.tooltip(ExportUIKeys.BAKE_IK_TOOLTIP);

        this.export = new UIButton(ExportUIKeys.EXPORT, (b) -> this.doExport());

        this.scan();
        this.rebuild();
        this.updateButtons();

        /* 图标行手动绝对定位成紧凑靠左的 20x20 按钮 —— UI.row 会把子元素
         * 均分整行宽度,按钮间距和点击范围都会被拉大 */
        UIElement iconBar = new UIElement();
        UIIcon[] icons = {this.rename, this.remove, this.copy, this.paste, this.openFolder};

        iconBar.h(20);

        for (int i = 0; i < icons.length; i++)
        {
            icons[i].relative(iconBar).x(i * 20).wh(20, 20);
            iconBar.add(icons[i]);
        }

        UIScrollView scroll = UI.scrollView(5, 6,
            iconBar,
            this.search,
            this.files,
            this.fileName,
            this.animationName,
            this.loop,
            this.bakeIK,
            this.export
        );

        scroll.full(this.content);
        this.content.add(scroll);
    }

    /* ---------- 数据扫描与列表重建 ---------- */

    /** 重扫模型目录下的动画文件及其动作,保留已有分组的展开状态 */
    private void scan()
    {
        List<FileEntry> old = new ArrayList<>(this.entries);

        this.entries.clear();

        ModelInstance model = ModelFormRenderer.getModel(this.modelForm);

        if (model == null || model.id.isEmpty())
        {
            return;
        }

        String prefix = ModelManager.MODELS_PREFIX + model.id + "/";

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets(ModelManager.MODELS_PREFIX + model.id), true))
        {
            if (!link.path.endsWith(SUFFIX))
            {
                continue;
            }

            FileEntry entry = new FileEntry();

            entry.link = link;
            entry.relative = link.path.startsWith(prefix) ? link.path.substring(prefix.length()) : link.path;
            entry.animations.addAll(AnimationFileOperations.listAnimations(BBSMod.getProvider().getFile(link)));

            for (FileEntry previous : old)
            {
                if (previous.relative.equals(entry.relative))
                {
                    entry.expanded = previous.expanded;

                    break;
                }
            }

            this.entries.add(entry);
        }
    }

    /** 按搜索词重建行:命中动作名的分组强制展开且只显示命中的动作 */
    private void rebuild()
    {
        String query = this.search.getText().trim().toLowerCase();
        List<Row> rows = new ArrayList<>();

        for (FileEntry entry : this.entries)
        {
            if (query.isEmpty())
            {
                rows.add(new Row(entry, null));

                if (entry.expanded)
                {
                    for (String animation : entry.animations)
                    {
                        rows.add(new Row(entry, animation));
                    }
                }

                continue;
            }

            boolean fileMatches = entry.relative.toLowerCase().contains(query);
            List<String> matched = new ArrayList<>();

            for (String animation : entry.animations)
            {
                if (animation.toLowerCase().contains(query))
                {
                    matched.add(animation);
                }
            }

            if (!fileMatches && matched.isEmpty())
            {
                continue;
            }

            rows.add(new Row(entry, null));

            List<String> children = matched.isEmpty() ? (entry.expanded ? entry.animations : List.of()) : matched;

            for (String animation : children)
            {
                rows.add(new Row(entry, animation));
            }
        }

        this.files.clear();
        this.files.add(rows);
        this.files.update();
    }

    private void pickRow(Row row)
    {
        this.selected = row;

        if (row != null)
        {
            if (row.animation == null)
            {
                row.file.expanded = !row.file.expanded;
                this.setFileName(row.file);
                this.rebuild();
                this.reselect(row.file, null);
            }
            else
            {
                this.setFileName(row.file);
                this.animationName.setText(row.animation);
            }
        }

        this.updateButtons();
    }

    private void setFileName(FileEntry entry)
    {
        this.fileName.setText(entry.relative.endsWith(SUFFIX)
            ? entry.relative.substring(0, entry.relative.length() - SUFFIX.length())
            : entry.relative);
    }

    /** rebuild 之后按语义(文件+动作名)恢复选中 */
    private void reselect(FileEntry entry, String animation)
    {
        List<Row> rows = this.files.getList();

        for (int i = 0; i < rows.size(); i++)
        {
            Row row = rows.get(i);

            if (row.file == entry && (animation == null ? row.animation == null : animation.equals(row.animation)))
            {
                this.files.setIndex(i);
                this.selected = row;

                return;
            }
        }

        this.selected = null;
    }

    private void updateButtons()
    {
        boolean animation = this.selected != null && this.selected.animation != null;

        this.rename.setEnabled(animation);
        this.remove.setEnabled(animation);
        this.copy.setEnabled(animation);
        this.paste.setEnabled(this.selected != null && clipboardAnimation != null);
        this.openFolder.setEnabled(this.folder != null);
    }

    private void refresh(FileEntry entry, String animation)
    {
        this.scan();
        this.rebuild();

        if (entry != null)
        {
            this.reselect(entry, animation);
        }

        this.updateButtons();
    }

    /* ---------- 动作操作 ---------- */

    private File fileOf(FileEntry entry)
    {
        return entry == null || entry.link == null ? null : BBSMod.getProvider().getFile(entry.link);
    }

    private void renameAnimation()
    {
        Row row = this.selected;

        if (row == null || row.animation == null)
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(UIKeys.GENERAL_RENAME, UIKeys.PANELS_MODALS_RENAME, (newName) ->
        {
            newName = newName.trim();

            if (newName.isEmpty() || newName.equals(row.animation))
            {
                return;
            }

            try
            {
                AnimationFileOperations.renameAnimation(this.fileOf(row.file), row.animation, newName);

                if (this.animationName.getText().equals(row.animation))
                {
                    this.animationName.setText(newName);
                }

                row.file.expanded = true;
                this.refresh(row.file, newName);
            }
            catch (Exception e)
            {
                this.error(e);
            }
        });

        panel.text.setText(row.animation);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void removeAnimation()
    {
        Row row = this.selected;

        if (row == null || row.animation == null)
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(UIKeys.GENERAL_REMOVE, UIKeys.PANELS_MODALS_REMOVE, (confirm) ->
        {
            if (!confirm)
            {
                return;
            }

            try
            {
                AnimationFileOperations.deleteAnimation(this.fileOf(row.file), row.animation);
                this.refresh(row.file, null);
            }
            catch (Exception e)
            {
                this.error(e);
            }
        }));
    }

    private void copyAnimation()
    {
        Row row = this.selected;

        if (row == null || row.animation == null)
        {
            return;
        }

        try
        {
            clipboardAnimation = AnimationFileOperations.copyAnimation(this.fileOf(row.file), row.animation);
            clipboardName = row.animation;
            this.updateButtons();
        }
        catch (Exception e)
        {
            this.error(e);
        }
    }

    private void pasteAnimation()
    {
        Row row = this.selected;

        if (row == null || clipboardAnimation == null)
        {
            return;
        }

        try
        {
            String pasted = AnimationFileOperations.pasteAnimation(this.fileOf(row.file), clipboardName, clipboardAnimation);

            row.file.expanded = true;
            this.refresh(row.file, pasted);
        }
        catch (Exception e)
        {
            this.error(e);
        }
    }

    /* ---------- 导出 ---------- */

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doExport()
    {
        UIContext context = this.getContext();
        ModelInstance model = ModelFormRenderer.getModel(this.modelForm);

        if (model == null)
        {
            return;
        }

        if (this.folder == null)
        {
            this.message(context, ExportUIKeys.FAILED.format("model folder isn't writable"));

            return;
        }

        String name = sanitize(this.fileName.getText());

        if (name.isEmpty())
        {
            name = this.defaultFileName.isEmpty() ? "exported" : this.defaultFileName;
        }

        String animation = this.animationName.getText().trim();

        if (animation.isEmpty())
        {
            animation = "pose_animation";
        }

        File target = new File(this.folder, name + SUFFIX);

        try
        {
            boolean bakeIK = this.hasIK && this.bakeIK.getValue();
            List<Keyframe<IKControls>> controls = bakeIK ? this.getIKControls() : List.of();

            if (this.sheet != null)
            {
                List<Keyframe<Pose>> keyframes = (List) this.sheet.selection.getSelected();

                if (bakeIK)
                {
                    IKAnimationExporter.exportPose(target, animation, this.loop.getValue(), model,
                        this.modelForm, keyframes, model.model.getGroupKeysInHierarchyOrder(), controls);
                }
                else
                {
                    PoseAnimationExporter.export(target, animation, this.loop.getValue(), keyframes,
                        model.model.getGroupKeysInHierarchyOrder());
                }
            }
            else
            {
                Map<String, List<Keyframe<PoseTransform>>> tracks = new LinkedHashMap<>();

                for (UIKeyframeSheet boneSheet : this.boneSheets)
                {
                    TrackId path = TrackId.parse(boneSheet.id, TrackKind.BONE);

                    if (path == null || boneSheet.selection.getSelected().isEmpty())
                    {
                        continue;
                    }

                    tracks.put(path.subject(), (List) boneSheet.selection.getSelected());
                }

                if (bakeIK)
                {
                    IKAnimationExporter.exportBoneTracks(target, animation, this.loop.getValue(), model,
                        this.modelForm, tracks, model.model.getGroupKeysInHierarchyOrder(), controls);
                }
                else
                {
                    PoseAnimationExporter.exportBoneTracks(target, animation, this.loop.getValue(), tracks);
                }
            }

            this.close();
            this.message(context, (bakeIK ? ExportUIKeys.SUCCESS_IK : ExportUIKeys.SUCCESS)
                .format(animation, target.getName()));
        }
        catch (Exception e)
        {
            this.message(context, ExportUIKeys.FAILED.format(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Keyframe<IKControls>> getIKControls()
    {
        if (this.properties == null)
        {
            return List.of();
        }

        String path = FormUtils.getPath(this.modelForm);
        String id = path == null || path.isEmpty()
            ? "ik_controls"
            : path + FormUtils.PATH_SEPARATOR + "ik_controls";
        KeyframeChannel channel = this.properties.get(TrackId.parse(id));

        if (channel == null || channel.getFactory() != KeyframeFactories.IK)
        {
            return List.of();
        }

        List<Keyframe<IKControls>> keyframes = new ArrayList<>((List) channel.getKeyframes());
        keyframes.removeIf((keyframe) -> keyframe == null || keyframe.getValue() == null);
        keyframes.sort(java.util.Comparator.comparingDouble(Keyframe::getTick));

        return keyframes;
    }

    private void error(Exception e)
    {
        this.message(this.getContext(), ExportUIKeys.FAILED.format(e.getMessage() == null ? e.toString() : e.getMessage()));
    }

    private void message(UIContext context, IKey message)
    {
        UIOverlay.addOverlay(context, new UIMessageOverlayPanel(ExportUIKeys.TITLE, message));
    }

    /** 去掉 Windows 非法文件名字符,保留 '/' 以支持写入子目录 */
    private static String sanitize(String name)
    {
        return name.trim().replaceAll("[\\\\:*?\"<>|]", "").replaceAll("\\.\\.", "");
    }

    /* ---------- 数据模型 ---------- */

    private static class FileEntry
    {
        public Link link;
        public String relative;
        public final List<String> animations = new ArrayList<>();
        public boolean expanded;
    }

    private static class Row
    {
        public final FileEntry file;
        public final String animation;

        public Row(FileEntry file, String animation)
        {
            this.file = file;
            this.animation = animation;
        }
    }

    /** 文件为可折叠分组、动作为缩进子行的树形列表 */
    private static class UIAnimationTreeList extends UIList<Row>
    {
        public UIAnimationTreeList(Consumer<List<Row>> callback)
        {
            super(callback);

            this.scroll.scrollItemSize = UIStringList.DEFAULT_HEIGHT;
        }

        @Override
        protected String elementToString(UIContext context, int i, Row element)
        {
            if (element.animation == null)
            {
                return (element.file.expanded ? "- " : "+ ") + element.file.relative;
            }

            return "        " + element.animation;
        }
    }
}
