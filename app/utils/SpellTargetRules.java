package utils;

import structures.GameState;
import structures.basic.Card;
import structures.basic.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Story #31 helper (single GameState architecture):
 * decide which board tiles are valid targets for a spell.
 * Returns list of int[]{x,y} using the SAME coordinate system you store in GameState.key(x,y).
 */
public final class SpellTargetRules {

    private SpellTargetRules() {}

    public static List<int[]> getValidTargetTiles(GameState gameState, Card spell) {
        if (gameState == null || spell == null) return Collections.emptyList();

        String name = spell.getCardname();
        if (name == null) return Collections.emptyList();
        String n = name.trim().toLowerCase();

        // - boardUnits contains ALL units (including avatars) as UI Units
        // - avatars are at fixed positions (1,2) and (7,2)
        int[] humanAvatarPos = gameState.getAvatarPosition("HUMAN");
        int[] aiAvatarPos = gameState.getAvatarPosition("AI");

        int humanAx = humanAvatarPos[0], humanAy = humanAvatarPos[1];
        int aiAx = aiAvatarPos[0], aiAy = aiAvatarPos[1];



        if (n.equals("truestrike")) {
            return tilesWithEnemyNonAvatarUnits(gameState, "AI");
        }

        if (n.equals("beamshock")) {
            return tilesWithEnemyNonAvatarUnits(gameState, "AI");
        }

        if (n.equals("dark terminus")) {
            return tilesWithEnemyNonAvatarUnits(gameState, "AI");
        }

        // Sundrop Elixir -> any unit tile
        if (n.equals("sundrop elixir")) {
            return tilesWithAnyUnit(gameState);
        }

        // Horn of the Forsaken -> target avatar tile (human)
        if (n.equals("horn of the forsaken")) {
            List<int[]> res = new ArrayList<>();
            res.add(new int[]{humanAx, humanAy});
            return res;
        }

        // Wraithling Swarm -> empty adjacent tiles around avatar (human)
        if (n.equals("wraithling swarm")) {
            List<int[]> res = new ArrayList<>();
            for (int[] xy : adjacentTiles(humanAx, humanAy)) {
                if (xy != null && isOnBoard(xy[0], xy[1]) && isTileEmpty(gameState, xy[0], xy[1])) {
                    res.add(new int[]{xy[0], xy[1]});
                }
            }
            return res;
        }

        return Collections.emptyList();
    }

    private static List<int[]> tilesWithAnyUnit(GameState gameState) {
        List<int[]> res = new ArrayList<>();
        for (String k : gameState.boardUnits.keySet()) {
            String[] parts = k.split(",");
            if (parts.length != 2) continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                res.add(new int[]{x, y});
            } catch (NumberFormatException ignored) {}
        }
        return res;
    }

    private static boolean isTileEmpty(GameState gameState, int x, int y) {
        return !gameState.boardUnits.containsKey(gameState.key(x, y));
    }

    private static boolean isOnBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }

    private static List<int[]> adjacentTiles(int x, int y) {
        List<int[]> res = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                res.add(new int[]{x + dx, y + dy});
            }
        }
        return res;
    }

    private static List<int[]> tilesWithEnemyNonAvatarUnits(GameState gameState, String enemyOwner) {
    List<int[]> res = new ArrayList<>();

    for (String k : gameState.boardUnits.keySet()) {
        Unit u = gameState.boardUnits.get(k);
        if (u == null) continue;

        int id = u.getId();

        // exclude avatars
        if (id == gameState.humanAvatarId || id == gameState.aiAvatarId) continue;

        // only enemy units
        String owner = gameState.unitOwner.get(id);
        if (owner == null || !owner.equals(enemyOwner)) continue;

        String[] parts = k.split(",");
        if (parts.length != 2) continue;

        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            res.add(new int[]{x, y});
        } catch (NumberFormatException ignored) {
        }
    }

    return res;
}

}