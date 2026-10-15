package com.kuronami.bonfirestomap.server;

import com.kuronami.bonfirestomap.sync.PointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.fml.ModList;

import wehavecookies56.bonfires.bonfire.Bonfire;
import wehavecookies56.bonfires.bonfire.BonfireRegistry;
import wehavecookies56.bonfires.data.BonfireHandler;
import wehavecookies56.bonfires.data.DiscoveryHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * サーバー側で「このプレイヤーが発見済みの」篝火を読む（Forge 1.20.1 capability 方式）。
 *
 * <p>読み取り経路: {@code DiscoveryHandler.getHandler(player)} →
 * {@code IDiscoveryHandler.getDiscovered()}（プレイヤーごとの発見済み UUID 集合）を引き、
 * 各 UUID を {@code BonfireHandler.getServerHandler(server).getRegistry().getBonfire(id)}
 * で実体へ解決する。全登録をそのまま送らないのは、Bonfires 本体が篝火の点火時に
 * 「未発見を含む全登録」をクライアントへ送る分岐を持ち、それを地図に出すと他人の篝火を
 * 暴くため（REPORT §4）。他者の篝火は送らない。</p>
 *
 * <p>注意: Bonfires の API は公開保証外のため、ホスト更新でシグネチャが変わった場合は
 * ここで静かに無効化される（Throwable を全部吸収して null を返す＝同期停止・クラッシュ無し）。</p>
 */
public final class BonfireCollector {

    /**
     * 篝火のウェイポイント色（0xRRGGBB）。Bonfires の篝火は色を持たないため固定値。
     * 炎の橙を採用する（供給源に色が無いので、ここで決める）。
     */
    private static final int FIXED_RGB = 0xFF8C42;

    private BonfireCollector() {
    }

    public static boolean isBonfiresLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("bonfires");
    }

    /**
     * @return プレイヤーが発見済みの篝火集合。Bonfires 未ロード・読み取り失敗は
     *         null（＝このプレイヤーには何も送らない）。空マップは正当な状態
     *         （発見ゼロ＝クライアント側の全削除が正）。
     */
    public static Map<UUID, PointSnapshot> collect(ServerPlayer player) {
        if (!isBonfiresLoaded()) {
            return null;
        }
        try {
            return Inner.collect(player);
        } catch (Throwable t) {
            com.kuronami.bonfirestomap.BonfiresToMap.LOGGER.warn(
                    "Bonfires registry read failed (sync disabled for this cycle): {}", t.toString());
            return null;
        }
    }

    /** Bonfires クラスへの参照はすべて Inner に閉じ込める（isolation パターン）。 */
    private static final class Inner {

        static Map<UUID, PointSnapshot> collect(ServerPlayer player) {
            Map<UUID, PointSnapshot> result = new LinkedHashMap<>();

            DiscoveryHandler.IDiscoveryHandler discovery = DiscoveryHandler.getHandler(player);
            if (discovery == null) {
                return result;
            }
            Map<UUID, java.time.Instant> discovered = discovery.getDiscovered();
            if (discovered == null || discovered.isEmpty()) {
                return result;
            }

            BonfireHandler.IBonfireHandler handler = BonfireHandler.getServerHandler(player.server);
            if (handler == null || handler.getRegistry() == null) {
                return result;
            }
            BonfireRegistry registry = handler.getRegistry();

            for (UUID id : discovered.keySet()) {
                Bonfire bonfire = registry.getBonfire(id);
                if (bonfire == null) {
                    continue; // 発見記録が残っていても登録から消えている篝火は送らない
                }
                BlockPos pos = bonfire.getPos();
                if (pos == null || bonfire.getDimension() == null) {
                    continue;
                }
                String name = bonfire.getName();
                result.put(id, new PointSnapshot(
                        id,
                        name == null ? "" : name,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        bonfire.getDimension().location().toString(),
                        FIXED_RGB));
            }
            return result;
        }
    }
}
