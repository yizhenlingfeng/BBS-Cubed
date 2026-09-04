package gbeic.bbsplusplus.premiere.util;

import java.io.File;
import java.util.Locale;

/**
 * Premiere / xmeml 兼容的 file pathurl。
 * 标准形式: {@code file://localhost/I:/path/to/file.mp4}
 */
public final class PremierePathUrl
{
    private PremierePathUrl()
    {}

    /**
     * 将本地文件转为 Premiere 可解析的 pathurl。
     * Windows 示例: {@code file://localhost/I:/BaiduSyncdisk/clip.mp4}
     */
    public static String fromFile(File file)
    {
        if (file == null)
        {
            return "";
        }

        String abs = file.getAbsolutePath().replace('\\', '/');

        /* Windows: C:/... → file://localhost/C:/... */
        if (abs.length() >= 2 && abs.charAt(1) == ':')
        {
            char drive = Character.toUpperCase(abs.charAt(0));
            String rest = abs.length() > 2 ? abs.substring(2) : "";
            if (!rest.startsWith("/"))
            {
                rest = "/" + rest;
            }
            return "file://localhost/" + drive + ":" + encodePath(rest);
        }

        /* UNC: //server/share/... */
        if (abs.startsWith("//"))
        {
            return "file:" + encodePath(abs);
        }

        /* POSIX absolute */
        if (!abs.startsWith("/"))
        {
            abs = "/" + abs;
        }
        return "file://localhost" + encodePath(abs);
    }

    /**
     * 百分号编码路径段,保留 '/'。
     * 盘符冒号由调用方直接拼接,不进入此方法。
     */
    private static String encodePath(String path)
    {
        StringBuilder sb = new StringBuilder(path.length() + 16);

        for (int i = 0; i < path.length(); )
        {
            int cp = path.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == '/')
            {
                sb.append('/');
            }
            else if (isUnreserved(cp))
            {
                sb.appendCodePoint(cp);
            }
            else
            {
                byte[] bytes = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte b : bytes)
                {
                    sb.append('%');
                    sb.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
                }
            }
        }

        return sb.toString();
    }

    private static boolean isUnreserved(int cp)
    {
        return (cp >= 'A' && cp <= 'Z')
            || (cp >= 'a' && cp <= 'z')
            || (cp >= '0' && cp <= '9')
            || cp == '-' || cp == '_' || cp == '.' || cp == '~';
    }
}
