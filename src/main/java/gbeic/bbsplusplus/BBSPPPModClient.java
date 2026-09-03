package gbeic.bbsplusplus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 纹理补间（TextureTween）模块的日志标识与常量持有类。
 * <p>
 * 原 BBSPPP 独立入口，已合并到 BBS++ 主包。初始化逻辑由
 * {@link gbeic.bbsplusplus.client.BBSPlusPlusModClient} 统一处理，
 * 本类仅保留 MOD_ID 与 LOGGER 供纹理补间相关代码使用。
 * </p>
 */
public class BBSPPPModClient
{
    public static final String MOD_ID = "bbsppp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
}
