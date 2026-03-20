package utils;

import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;

public final class MovementUtils {

    private static final int CLIENT_MOVE_VELOCITY_PX_PER_FRAME = 2;
    private static final int CLIENT_FPS = 60;
    private static final int SAFETY_BUFFER_MS = 150;

    private MovementUtils() {}

    public static int estimateMoveDurationMs(int fromX, int fromY, int toX, int toY) {
        Tile from = BasicObjectBuilders.loadTile(fromX, fromY);
        Tile to = BasicObjectBuilders.loadTile(toX, toY);
        return estimateMoveDurationMs(from, to);
    }

    public static int estimateMoveDurationMs(Tile from, Tile to) {
        if (from == null || to == null) return 0;

        int pixelDistance = Math.abs(to.getXpos() - from.getXpos())
                + Math.abs(to.getYpos() - from.getYpos());

        int frames = (int) Math.ceil(pixelDistance / (double) CLIENT_MOVE_VELOCITY_PX_PER_FRAME);
        return (int) Math.ceil((frames * 1000.0) / CLIENT_FPS) + SAFETY_BUFFER_MS;
    }

    // Returns true when the client should move on Y first, then X.
    public static boolean decideMoveOrder(GameState gameState, int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;

        if (dx == 0 || dy == 0) {
            return true;
        }

        Unit xFirstBlocker = gameState.boardUnits.get(gameState.key(toX, fromY));
        Unit yFirstBlocker = gameState.boardUnits.get(gameState.key(fromX, toY));

        boolean xFirstFree = (xFirstBlocker == null);
        boolean yFirstFree = (yFirstBlocker == null);

        if (yFirstFree && !xFirstFree) return true;
        if (xFirstFree && !yFirstFree) return false;

        return true;
    }

    public static void syncUnitBoardPosition(GameState gameState, Unit unit, int tilex, int tiley) {
        if (gameState == null || unit == null) return;

        int unitId = unit.getId();
        String newKey = gameState.key(tilex, tiley);

        gameState.boardUnits.entrySet().removeIf(entry -> {
            Unit placedUnit = entry.getValue();
            return placedUnit != null && placedUnit.getId() == unitId;
        });

        gameState.boardUnits.put(newKey, unit);
        gameState.unitPositionKey.put(unitId, newKey);
    }
}
