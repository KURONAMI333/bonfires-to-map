package com.kuronami.bonfirestomap.compat.jm;

import com.kuronami.bonfirestomap.BonfiresToMap;
import com.kuronami.bonfirestomap.sync.PointSnapshot;
import com.kuronami.bonfirestomap.sync.SyncPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;

import net.neoforged.fml.ModList;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JourneyMap への差分プラン適用。JM 不在時に NoClassDefFoundError を起こさないよう、
 * JM API への参照はすべてこのクラス（と Inner）に閉じ込める。
 *
 * <p>更新は「同一キーの remove + add」。篝火 UUID を追跡キーとして
 * 自前の live マップで Waypoint 実体を保持する（removeWaypoint は実体参照を取るため）。</p>
 */
public final class JourneyMapBridge {

    /** キー = 篝火 UUID。JM へ渡した Waypoint 実体の保持用。 */
    private static final Map<UUID, Waypoint> LIVE = new HashMap<>();

    private JourneyMapBridge() {
    }

    public static boolean isJourneyMapLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("journeymap");
    }

    /**
     * 差分プランを JM へ適用する。
     *
     * @return 適用が完了したら true（JM 不在・API 未初期化・例外時は false）
     */
    public static boolean apply(SyncPlan plan) {
        if (!isJourneyMapLoaded()) {
            return false;
        }
        try {
            return Inner.apply(plan);
        } catch (Throwable t) {
            BonfiresToMap.LOGGER.warn("JourneyMap waypoint apply failed: {}", t.toString());
            return false;
        }
    }

    /**
     * セッション開始時の全再構築。前セッションで永続化された自 MOD ウェイポイントを
     * 一度すべて消してから現在の地点集合を積み直す（重複蓄積の防止）。
     *
     * @param desired 現在の地点集合（空でもよい＝全部消えるのが正）
     * @return 完了したら true
     */
    public static boolean replaceAll(Map<UUID, PointSnapshot> desired) {
        if (!isJourneyMapLoaded()) {
            return false;
        }
        try {
            return Inner.replaceAll(desired);
        } catch (Throwable t) {
            BonfiresToMap.LOGGER.warn("JourneyMap waypoint rebuild failed: {}", t.toString());
            return false;
        }
    }

    /**
     * ワールド切断時に呼ぶ。JM 上の永続ウェイポイントには触れず、
     * 自分の保持している実体参照だけを捨てる。
     */
    public static void forgetSession() {
        LIVE.clear();
    }

    private static final class Inner {

        static boolean replaceAll(Map<UUID, PointSnapshot> desired) {
            IClientAPI api = BtmJourneyMapPlugin.api;
            if (api == null) {
                BonfiresToMap.LOGGER.debug("JourneyMap API not yet initialized, skipping session rebuild");
                return false;
            }
            api.removeAllWaypoints(BonfiresToMap.MODID);
            LIVE.clear();
            List<PointSnapshot> ordered = desired.values().stream()
                    .sorted(Comparator.comparing(PointSnapshot::id))
                    .toList();
            for (PointSnapshot snapshot : ordered) {
                Waypoint wp = createWaypoint(snapshot);
                LIVE.put(snapshot.id(), wp);
                api.addWaypoint(BonfiresToMap.MODID, wp);
                BonfiresToMap.LOGGER.debug("Waypoint created: {} @ ({}, {}, {}) in {} color=#{}",
                        snapshot.name(), snapshot.x(), snapshot.y(), snapshot.z(), snapshot.dimensionId(),
                        String.format("%06x", snapshot.rgb()));
            }
            BonfiresToMap.LOGGER.info("Rebuilt {} Bonfires waypoint(s) on JourneyMap", ordered.size());
            return true;
        }

        static boolean apply(SyncPlan plan) {
            IClientAPI api = BtmJourneyMapPlugin.api;
            if (api == null) {
                // JM は居るがまだプラグイン初期化前 → 静かに skip（次サイクルで再試行）
                BonfiresToMap.LOGGER.debug("JourneyMap API not yet initialized, skipping sync cycle");
                return false;
            }

            for (UUID id : plan.removals()) {
                Waypoint wp = LIVE.remove(id);
                if (wp != null) {
                    api.removeWaypoint(BonfiresToMap.MODID, wp);
                }
            }

            for (PointSnapshot snapshot : plan.upserts()) {
                UUID id = snapshot.id();
                Waypoint previous = LIVE.remove(id);
                if (previous != null) {
                    api.removeWaypoint(BonfiresToMap.MODID, previous);
                }
                Waypoint wp = createWaypoint(snapshot);
                LIVE.put(id, wp);
                api.addWaypoint(BonfiresToMap.MODID, wp);
                BonfiresToMap.LOGGER.debug("Waypoint upserted: {} @ ({}, {}, {}) in {} color=#{}",
                        snapshot.name(), snapshot.x(), snapshot.y(), snapshot.z(), snapshot.dimensionId(),
                        String.format("%06x", snapshot.rgb()));
            }
            return true;
        }

        static Waypoint createWaypoint(PointSnapshot s) {
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(s.dimensionId()));
            // 永続 waypoint 固定（v1.0 の製品挙動）
            Waypoint wp = WaypointFactory.createClientWaypoint(
                    BonfiresToMap.MODID,
                    new BlockPos(s.x(), s.y(), s.z()),
                    s.name(),
                    dimension,
                    true);
            wp.setColor(s.rgb());
            return wp;
        }
    }
}
