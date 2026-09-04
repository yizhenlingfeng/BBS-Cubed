package gbeic.bbsplusplus.settings;

import mchorse.bbs_mod.settings.values.core.ValueString;

/**
 * 设置面板分区标题：只渲染居中文字，不显示输入框与 tooltip。
 * UI 工厂在客户端 {@code BBSFSloveCMLClient} 注册。
 */
public class ValueSectionHeader extends ValueString
{
    public ValueSectionHeader(String id, String defaultValue)
    {
        super(id, defaultValue);
    }
}
