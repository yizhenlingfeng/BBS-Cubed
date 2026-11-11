package gbeic.bbsplusplus;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * BBSPlusPlusBlocks
 *
 * <p>定义了 BBS++ 模组中的方块和对应的物品。主要包括 8 个发光的色彩方块（红、绿、蓝、青、品红、黄、黑、白），
 * 这些方块具有特殊的属性，如无法被破坏、不会掉落物品，并且始终发光。所有方块和物品都注册在 bbs 命名空间下，
 * 以便与 BBS 的资源系统兼容。</p>
 *
 * <p>1.21.11 适配（B6-06）：Fabric 删除了 {@code FabricBlockSettings}（object-builder-api 只剩
 * {@code FabricBlockEntityType} 与 {@code BlockSetTypeBuilder}），改用原版 {@link AbstractBlock.Settings#create()}；
 * 并且 1.21.11 起方块与物品的 Settings 都要求带 {@code registryKey(...)}，否则注册时拿不到 id 直接抛
 * {@code IllegalStateException}。{@code emissiveLighting} 的谓词签名也从
 * {@code (BlockState, BlockView, BlockPos)} 保持不变，无需改调用形状。</p>
 */
public class BBSPlusPlusBlocks
{
    private static final String NS = "bbs";

    private static Block createEmissiveChromaBlock(String name)
    {
        Identifier id = Identifier.of(NS, "emissive_chroma_" + name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);

        return new Block(AbstractBlock.Settings.create()
            .registryKey(blockKey)
            .noBlockBreakParticles()
            .dropsNothing()
            .requiresTool()
            .strength(-1F, 3600000F)
            .emissiveLighting((state, world, pos) -> true));
    }

    private static BlockItem createItem(String name, Block block)
    {
        Identifier id = Identifier.of(NS, "emissive_chroma_" + name);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

        return new BlockItem(block, new Item.Settings().registryKey(itemKey));
    }

    public static final Block EMISSIVE_CHROMA_RED_BLOCK = createEmissiveChromaBlock("red");
    public static final Block EMISSIVE_CHROMA_GREEN_BLOCK = createEmissiveChromaBlock("green");
    public static final Block EMISSIVE_CHROMA_BLUE_BLOCK = createEmissiveChromaBlock("blue");
    public static final Block EMISSIVE_CHROMA_CYAN_BLOCK = createEmissiveChromaBlock("cyan");
    public static final Block EMISSIVE_CHROMA_MAGENTA_BLOCK = createEmissiveChromaBlock("magenta");
    public static final Block EMISSIVE_CHROMA_YELLOW_BLOCK = createEmissiveChromaBlock("yellow");
    public static final Block EMISSIVE_CHROMA_BLACK_BLOCK = createEmissiveChromaBlock("black");
    public static final Block EMISSIVE_CHROMA_WHITE_BLOCK = createEmissiveChromaBlock("white");

    public static final BlockItem EMISSIVE_CHROMA_RED_BLOCK_ITEM = createItem("red", EMISSIVE_CHROMA_RED_BLOCK);
    public static final BlockItem EMISSIVE_CHROMA_GREEN_BLOCK_ITEM = createItem("green", EMISSIVE_CHROMA_GREEN_BLOCK);
    public static final BlockItem EMISSIVE_CHROMA_BLUE_BLOCK_ITEM = createItem("blue", EMISSIVE_CHROMA_BLUE_BLOCK);
    public static final BlockItem EMISSIVE_CHROMA_CYAN_BLOCK_ITEM = createItem("cyan", EMISSIVE_CHROMA_CYAN_BLOCK);
    public static final BlockItem EMISSIVE_CHROMA_MAGENTA_BLOCK_ITEM = createItem("magenta", EMISSIVE_CHROMA_MAGENTA_BLOCK);
    public static final BlockItem EMISSIVE_CHROMA_YELLOW_BLOCK_ITEM = createItem("yellow", EMISSIVE_CHROMA_YELLOW_BLOCK);
    public static final BlockItem EMISSIVE_CHROMA_BLACK_BLOCK_ITEM = createItem("black", EMISSIVE_CHROMA_BLACK_BLOCK);
    public static final BlockItem EMISSIVE_CHROMA_WHITE_BLOCK_ITEM = createItem("white", EMISSIVE_CHROMA_WHITE_BLOCK);

    public static void register()
    {
        List<BlockItem> registered = new ArrayList<>();

        registerChroma(registered, "red", EMISSIVE_CHROMA_RED_BLOCK, EMISSIVE_CHROMA_RED_BLOCK_ITEM);
        registerChroma(registered, "green", EMISSIVE_CHROMA_GREEN_BLOCK, EMISSIVE_CHROMA_GREEN_BLOCK_ITEM);
        registerChroma(registered, "blue", EMISSIVE_CHROMA_BLUE_BLOCK, EMISSIVE_CHROMA_BLUE_BLOCK_ITEM);
        registerChroma(registered, "cyan", EMISSIVE_CHROMA_CYAN_BLOCK, EMISSIVE_CHROMA_CYAN_BLOCK_ITEM);
        registerChroma(registered, "magenta", EMISSIVE_CHROMA_MAGENTA_BLOCK, EMISSIVE_CHROMA_MAGENTA_BLOCK_ITEM);
        registerChroma(registered, "yellow", EMISSIVE_CHROMA_YELLOW_BLOCK, EMISSIVE_CHROMA_YELLOW_BLOCK_ITEM);
        registerChroma(registered, "black", EMISSIVE_CHROMA_BLACK_BLOCK, EMISSIVE_CHROMA_BLACK_BLOCK_ITEM);
        registerChroma(registered, "white", EMISSIVE_CHROMA_WHITE_BLOCK, EMISSIVE_CHROMA_WHITE_BLOCK_ITEM);

        /* 添加到 bbs 的创造模式物品栏中 */
        ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of("bbs", "main"))).register(content -> registered.forEach(content::add));
    }

    /**
     * 注册一组发光方块与物品，并在注册前检查 ID 是否已被 BBS 或其它模组占用。
     * 若冲突则整组跳过，避免 Registry 重复键崩溃或物品栏引用未注册物品。
     */
    private static void registerChroma(List<BlockItem> registered, String name, Block block, BlockItem item)
    {
        Identifier id = Identifier.of(NS, "emissive_chroma_" + name);

        if (Registries.BLOCK.containsId(id) || Registries.ITEM.containsId(id))
        {
            BBSPlusPlusMod.LOGGER.warn("[BBS Cubed] 方块/物品 {} 已存在，跳过发光色块注册", id);

            return;
        }

        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, item);
        registered.add(item);
    }
}
