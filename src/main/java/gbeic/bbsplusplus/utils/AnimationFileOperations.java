package gbeic.bbsplusplus.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bedrock 动画 json 文件的读改写工具:供导出面板管理已保存的动作
 * (列出/重命名/删除/复制粘贴/翻转),{@link PoseAnimationExporter} 的
 * 文件合并写入也走这里的 read/write。
 */
public class AnimationFileOperations
{
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 读取动画文件根对象;文件不存在时返回带 format_version + 空 animations 的骨架 */
    public static JsonObject readRoot(File file) throws IOException
    {
        JsonObject root = new JsonObject();

        if (file.exists())
        {
            try
            {
                root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
            catch (Exception e)
            {
                throw new IOException("Target file isn't a valid JSON object: " + file.getName());
            }
        }

        if (!root.has("format_version"))
        {
            root.addProperty("format_version", "1.8.0");
        }

        if (!root.has("animations") || !root.get("animations").isJsonObject())
        {
            root.add("animations", new JsonObject());
        }

        return root;
    }

    public static void writeRoot(File file, JsonObject root) throws IOException
    {
        File parent = file.getParentFile();

        if (parent != null)
        {
            parent.mkdirs();
        }

        Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    /** 列出文件里的动画名;解析失败(损坏/非动画 json)返回空列表 */
    public static List<String> listAnimations(File file)
    {
        List<String> names = new ArrayList<>();

        if (file == null || !file.exists())
        {
            return names;
        }

        try
        {
            names.addAll(readRoot(file).getAsJsonObject("animations").keySet());
        }
        catch (Exception e)
        {}

        return names;
    }

    /** 重命名动画并保持它在文件中的位置;目标名已存在时抛异常防止静默覆盖 */
    public static void renameAnimation(File file, String from, String to) throws IOException
    {
        JsonObject root = readRoot(file);
        JsonObject animations = root.getAsJsonObject("animations");

        if (!animations.has(from))
        {
            throw new IOException("Animation \"" + from + "\" wasn't found!");
        }

        if (!from.equals(to) && animations.has(to))
        {
            throw new IOException("Animation \"" + to + "\" already exists!");
        }

        JsonObject rebuilt = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : animations.entrySet())
        {
            rebuilt.add(entry.getKey().equals(from) ? to : entry.getKey(), entry.getValue());
        }

        root.add("animations", rebuilt);
        writeRoot(file, root);
    }

    public static void deleteAnimation(File file, String name) throws IOException
    {
        JsonObject root = readRoot(file);

        root.getAsJsonObject("animations").remove(name);
        writeRoot(file, root);
    }

    /** 取动画的深拷贝(复制到剪贴板用) */
    public static JsonObject copyAnimation(File file, String name) throws IOException
    {
        JsonObject root = readRoot(file);
        JsonElement animation = root.getAsJsonObject("animations").get(name);

        if (animation == null || !animation.isJsonObject())
        {
            throw new IOException("Animation \"" + name + "\" wasn't found!");
        }

        return animation.getAsJsonObject().deepCopy();
    }

    /**
     * 把动画粘贴进目标文件,同名时自动追加 _copy 后缀直到不冲突。
     *
     * @return 实际写入的动画名
     */
    public static String pasteAnimation(File file, String name, JsonObject animation) throws IOException
    {
        JsonObject root = readRoot(file);
        JsonObject animations = root.getAsJsonObject("animations");
        String target = name;

        while (animations.has(target))
        {
            target += "_copy";
        }

        animations.add(target, animation.deepCopy());
        writeRoot(file, root);

        return target;
    }
}
