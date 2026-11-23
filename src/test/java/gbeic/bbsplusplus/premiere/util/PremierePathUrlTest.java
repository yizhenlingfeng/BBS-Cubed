package gbeic.bbsplusplus.premiere.util;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 特征测试：固定 xmeml file pathurl 的生成规则（Windows 盘符、保留字符编码）。
 */
class PremierePathUrlTest
{
    @Test
    void nullFileProducesEmptyString()
    {
        assertEquals("", PremierePathUrl.fromFile(null));
    }

    @Test
    void windowsDrivePathsGetLocalhostPrologue()
    {
        File file = requireWindowsDrivePath("C:/clips/input.mp4");

        assertEquals("file://localhost/C:/clips/input.mp4", PremierePathUrl.fromFile(file));
    }

    @Test
    void spacesAndNonAsciiArePercentEncodedAsUtf8()
    {
        File file = requireWindowsDrivePath("C:/clips/my clip/影片.mp4");

        String url = PremierePathUrl.fromFile(file);

        assertTrue(url.startsWith("file://localhost/C:/clips/my%20clip/"), url);
        /* 影 = E5 BD B1，片 = E7 89 87 */
        assertTrue(url.endsWith("%E5%BD%B1%E7%89%87.mp4"), url);
    }

    @Test
    void unreservedCharactersAreKeptAsIs()
    {
        File file = requireWindowsDrivePath("C:/a-b_c.d~e/f.mp4");

        assertEquals("file://localhost/C:/a-b_c.d~e/f.mp4", PremierePathUrl.fromFile(file));
    }

    /**
     * 只在当前平台把该路径解析成盘符绝对路径时才继续，避免在非 Windows 上产生假失败。
     */
    private static File requireWindowsDrivePath(String path)
    {
        File file = new File(path);
        String absolute = file.getAbsolutePath();

        assumeTrue(absolute.length() >= 2 && absolute.charAt(1) == ':');

        return file;
    }
}
