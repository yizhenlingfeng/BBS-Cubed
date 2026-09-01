package gbeic.bbsppp;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BBSPPP 客户端入口。
 * <p>
 * BBSPPP 是 BBS++ 的私有扩展包，公开版 BBS++ 不包含这里的功能逻辑。
 * 当前入口只负责提供日志标识，具体功能由 Mixin 注入到 BBS 与 BBS++ 的现有流程中。
 * </p>
 */
public class BBSPPPModClient implements ClientModInitializer
{
    public static final String MOD_ID = "bbsppp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient()
    {
        LOGGER.info("BBSPPP initialized!");
    }
}
