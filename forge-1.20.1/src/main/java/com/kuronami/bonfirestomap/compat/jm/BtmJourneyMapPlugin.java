package com.kuronami.bonfirestomap.compat.jm;

import com.kuronami.bonfirestomap.BonfiresToMap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * JourneyMap が起動時に検出して呼び出すプラグインエントリ。
 * {@link IClientAPI} ハンドルを保持し、後で waypoint 差分適用に使う。
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class BtmJourneyMapPlugin implements IClientPlugin {

    public static volatile IClientAPI api;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        BonfiresToMap.LOGGER.info("JourneyMap API initialized for Bonfires to Map");
    }

    @Override
    public String getModId() {
        return BonfiresToMap.MODID;
    }
}
