package gbeic.bbsplusplus.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 特征测试：固定自然排序（资源管理器式）行为，供文件列表排序回归使用。
 */
class AlphanumComparatorTest
{
    private final AlphanumComparator comparator = new AlphanumComparator();

    @Test
    void numericChunksCompareByValueNotByText()
    {
        assertTrue(comparator.compare("效果2", "效果10") < 0);
        assertTrue(comparator.compare("效果10", "效果2") > 0);
        assertEquals(0, comparator.compare("效果2", "效果2"));
        assertTrue(comparator.compare("a2", "a10") < 0);
    }

    @Test
    void textChunksCompareCaseInsensitively()
    {
        assertEquals(0, comparator.compare("abc", "ABC"));
        assertTrue(comparator.compare("abc", "abd") < 0);
    }

    @Test
    void mixedChunksFallBackToLengthWhenPrefixesMatch()
    {
        assertTrue(comparator.compare("a", "ab") < 0);
        assertTrue(comparator.compare("a1", "a1b") < 0);
    }

    @Test
    void leadingZerosCompareByChunkLengthFirst()
    {
        /* 实现按块长度先比较，因此 "007" 与 "7" 长度不同，不视为相等 */
        assertTrue(comparator.compare("a7", "a07") < 0);
    }

    @Test
    void nullsCompareAsEqual()
    {
        assertEquals(0, comparator.compare(null, "abc"));
        assertEquals(0, comparator.compare("abc", null));
        assertEquals(0, comparator.compare(null, null));
    }

    @Test
    void sortingReproducesExplorerOrder()
    {
        List<String> names = new ArrayList<>(List.of("shot10", "shot2", "shot1", "shot20", "shot3"));

        Collections.sort(names, comparator);

        assertEquals(List.of("shot1", "shot2", "shot3", "shot10", "shot20"), names);
    }
}
