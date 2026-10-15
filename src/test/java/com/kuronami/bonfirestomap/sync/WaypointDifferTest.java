package com.kuronami.bonfirestomap.sync;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 差分同期エンジンの単体テスト。MC クラスパス無しで完結する
 * （PointSnapshot が純粋レコードであることの回帰ガードも兼ねる）。
 */
class WaypointDifferTest {

    private static PointSnapshot point(String idSeed, String name, int x, int y, int z) {
        UUID id = UUID.nameUUIDFromBytes(idSeed.getBytes());
        return new PointSnapshot(id, name, x, y, z, "minecraft:overworld", 0xFF6A00);
    }

    private static Map<UUID, PointSnapshot> mapOf(PointSnapshot... points) {
        Map<UUID, PointSnapshot> map = new HashMap<>();
        for (PointSnapshot p : points) {
            map.put(p.id(), p);
        }
        return map;
    }

    @Test
    void emptyToEmptyProducesEmptyPlan() {
        SyncPlan plan = WaypointDiffer.plan(Map.of(), Map.of());
        assertTrue(plan.isEmpty());
    }

    @Test
    void allNewPointsAreUpserts() {
        PointSnapshot a = point("a", "Base", 0, 64, 0);
        PointSnapshot b = point("b", "Mine", 100, 32, -50);

        SyncPlan plan = WaypointDiffer.plan(mapOf(a, b), Map.of());

        // プランは id 昇順で決定論的に並ぶ
        List<PointSnapshot> expected = java.util.stream.Stream.of(a, b)
                .sorted(java.util.Comparator.comparing(PointSnapshot::id))
                .toList();
        assertEquals(expected, plan.upserts());
        assertTrue(plan.removals().isEmpty());
    }

    @Test
    void removedPointsAreRemovals() {
        PointSnapshot a = point("a", "Base", 0, 64, 0);
        PointSnapshot b = point("b", "Mine", 100, 32, -50);
        Map<UUID, PointSnapshot> tracked = mapOf(a, b);

        SyncPlan plan = WaypointDiffer.plan(mapOf(a), tracked);

        assertTrue(plan.upserts().isEmpty());
        assertEquals(List.of(b.id()), plan.removals());
    }

    @Test
    void changedContentIsUpdate() {
        PointSnapshot original = point("a", "Base", 0, 64, 0);
        // 同一 UUID で内容だけ変わったもの（名前変更）
        PointSnapshot renamed = new PointSnapshot(original.id(), "Home Base", 0, 64, 0,
                original.dimensionId(), original.rgb());
        Map<UUID, PointSnapshot> tracked = mapOf(original);

        SyncPlan plan = WaypointDiffer.plan(mapOf(renamed), tracked);

        assertEquals(List.of(renamed), plan.upserts());
        assertTrue(plan.removals().isEmpty());
    }

    @Test
    void movedOrRecoloredIsAlsoUpdate() {
        PointSnapshot original = point("a", "Base", 0, 64, 0);
        PointSnapshot moved = new PointSnapshot(original.id(), "Base", 10, 64, 20,
                original.dimensionId(), original.rgb());
        PointSnapshot recolored = new PointSnapshot(original.id(), "Base", 0, 64, 0,
                original.dimensionId(), 0x00FF00);

        assertEquals(List.of(moved), WaypointDiffer.plan(mapOf(moved), mapOf(original)).upserts());
        assertEquals(List.of(recolored), WaypointDiffer.plan(mapOf(recolored), mapOf(original)).upserts());
    }

    @Test
    void identicalStateYieldsEmptyPlan() {
        PointSnapshot a = point("a", "Base", 0, 64, 0);
        SyncPlan plan = WaypointDiffer.plan(mapOf(a), mapOf(a));
        assertTrue(plan.isEmpty());
    }

    @Test
    void mixedAddUpdateRemoveInOnePlan() {
        PointSnapshot kept = point("kept", "Keep", 1, 2, 3);
        PointSnapshot updatedOld = point("upd", "Old", 4, 5, 6);
        PointSnapshot updatedNew = new PointSnapshot(updatedOld.id(), "New", 4, 5, 6,
                updatedOld.dimensionId(), updatedOld.rgb());
        PointSnapshot added = point("add", "Add", 7, 8, 9);
        PointSnapshot dropped = point("drop", "Drop", 10, 11, 12);

        SyncPlan plan = WaypointDiffer.plan(
                mapOf(kept, updatedNew, added),
                mapOf(kept, updatedOld, dropped));

        assertEquals(List.of(added, updatedNew), plan.upserts()); // id 昇順
        assertEquals(List.of(dropped.id()), plan.removals());
    }

    @Test
    void inputsAreNotMutated() {
        PointSnapshot a = point("a", "Base", 0, 64, 0);
        PointSnapshot b = point("b", "Mine", 100, 32, -50);
        Map<UUID, PointSnapshot> desired = mapOf(a);
        Map<UUID, PointSnapshot> tracked = mapOf(b);

        WaypointDiffer.plan(desired, tracked);

        assertEquals(1, desired.size());
        assertEquals(1, tracked.size());
    }
}
