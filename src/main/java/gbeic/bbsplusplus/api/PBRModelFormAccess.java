package gbeic.bbsplusplus.api;

import java.util.Map;

public interface PBRModelFormAccess
{
    Map<String, Map<String, Integer>> bbspp_snow$getPbrOverrides();

    Map<String, Map<String, Integer>> bbspp_snow$getBonePbrOverrides();
}
