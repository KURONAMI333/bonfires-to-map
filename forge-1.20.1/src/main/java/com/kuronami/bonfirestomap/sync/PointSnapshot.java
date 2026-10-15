package com.kuronami.bonfirestomap.sync;

import java.util.UUID;

/**
 * 地点 1 件の不変スナップショット。JourneyMap / Bonfires のどちらの型にも依存しない
 * 純粋なデータ保持用レコード。差分判定エンジン（{@link WaypointDiffer}）と単体テストは
 * Minecraft クラスパス無しで動く。
 *
 * @param id          Bonfires 側の篝火 UUID（追跡キー）
 * @param name        表示名（篝火名そのまま）
 * @param x           ブロック座標 X
 * @param y           ブロック座標 Y
 * @param z           ブロック座標 Z
 * @param dimensionId 次元の ResourceLocation 文字列（例: {@code minecraft:overworld}）
 * @param rgb         ウェイポイント色（0xRRGGBB・アルファ落とし済み）
 */
public record PointSnapshot(UUID id, String name, int x, int y, int z, String dimensionId, int rgb) {

    public PointSnapshot {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(dimensionId, "dimensionId");
    }
}
