package utils;

import structures.GameState;
import structures.basic.Card;
import structures.basic.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

        int humanAx = humanAvatarPos[0], humanAy = humanAvatarPos[1];



        if (n.equals("truestrike")) {
            return tilesWithNonAvatarUnitsByOwner(gameState, "AI");
        }

        if (n.equals("beam shock") || n.equals("beamshock")) {
            return tilesWithNonAvatarUnitsByOwner(gameState, "AI");
        }

        if (n.equals("dark terminus")) {
            //replacing with same generic method for this and sundrop elixir
            return tilesWithNonAvatarUnitsByOwner(gameState, "AI");
        }

        // Sundrop Elixir -> any unit tile
        if (n.equals("sundrop elixir")) {
            // --- only with AI ---
            return tilesWithNonAvatarUnitsByOwner(gameState, "AI");
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
    Set<String> seen = new HashSet<>();

    for (Unit u : gameState.boardUnits.values()) {
        if (u == null) continue;

        String owner = gameState.unitOwner.get(u.getId());
        if (!"HUMAN".equals(owner)) continue;

        int x = u.getPosition().getTilex();
        int y = u.getPosition().getTiley();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int tx = x + dx;
                int ty = y + dy;

                if (!isOnBoard(tx, ty)) continue;
                if (!isTileEmpty(gameState, tx, ty)) continue;

                String key = gameState.key(tx, ty);
                if (seen.contains(key)) continue;

                seen.add(key);
                res.add(new int[]{tx, ty});
            }
        }
    }
    return res;
}

        return Collections.emptyList();
    }


    private static boolean isTileEmpty(GameState gameState, int x, int y) {
        return !gameState.boardUnits.containsKey(gameState.key(x, y));
    }

    private static boolean isOnBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }


    //made into generic method to apply to sundrop elixir, truestrike, beam shock, dark terminus
    private static List<int[]> 
    tilesWithNonAvatarUnitsByOwner(GameState gameState, String owner) {
    List<int[]> res = new ArrayList<>();

    for (String k : gameState.boardUnits.keySet()) {
        Unit u = gameState.boardUnits.get(k);
        if (u == null) continue;

        int id = u.getId();

        // exclude avatars
        if (id == gameState.humanAvatarId || id == gameState.aiAvatarId) continue;

        // filter by owner
        String unitOwner = gameState.unitOwner.get(id);
        if (unitOwner == null || !owner.equals(unitOwner)) continue;

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