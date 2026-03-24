package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Tile;
import structures.basic.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Story #31: highlight valid spell target tiles in red when a spell card is selected.
 *  Mainly used when:
 * - selecting cards
 * - showing valid targets (spells, moves, attacks)
 * - clearing UI state after actions
 */
public final class HighlightUtils {

    private HighlightUtils() {}

    /** Clears any previously highlighted target tiles (mode=0). */
    public static void clearHighlightedTiles(ActorRef out, GameState gameState) {
    if (gameState == null) return;

    if (out != null && !gameState.highlightedTargetTiles.isEmpty()) {
        for (String key : gameState.highlightedTargetTiles) {
            String[] parts = key.split(",");
            if (parts.length != 2) continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                Tile tile = BasicObjectBuilders.loadTile(x, y);
                BasicCommands.drawTile(out, tile, 0);
                } catch (NumberFormatException ignored) {
            }
        }
    }

        gameState.highlightedTargetTiles.clear();
    }

    /**
     * Highlights a list of tiles in red (mode=2) and tracks them in gameState.
     */
    public static void highlightTilesRed(ActorRef out, GameState gameState, List<int[]> tiles0Based) {
        if (out == null || gameState == null) return;
        if (tiles0Based == null || tiles0Based.isEmpty()) return;

        for (int[] xy : tiles0Based) {
            if (xy == null || xy.length < 2) continue;
            int x = xy[0];
            int y = xy[1];

            Tile tile = BasicObjectBuilders.loadTile(x, y);
            BasicCommands.drawTile(out, tile, 2);

            gameState.highlightedTargetTiles.add(x + "," + y);
        }
    }

    public static void highlightTilesWhite(ActorRef out, GameState gameState, List<int[]> tiles0Based){
        if (out == null || gameState == null) return;
        if (tiles0Based == null || tiles0Based.isEmpty()) return;

        for (int[] xy : tiles0Based){
            if ( xy == null || xy.length < 2) continue;
            int x = xy[0];
            int y = xy[1];
            Tile tile = BasicObjectBuilders.loadTile(x,y);
            BasicCommands.drawTile(out, tile, 1);
            gameState.highlightedTargetTiles.add(x + "," + y);
        }
    }

    /** Unhighlights any currently selected card (if still present) and clears selection state. */
    public static void clearCardSelection(ActorRef out, GameState gameState) {
        if (gameState == null) return;

        if (out != null && gameState.selectedHandPos != null) {
            int pos = gameState.selectedHandPos;

            if (pos >= 1 && pos <= gameState.humanHand.size()) {
                String cfg = gameState.humanHand.get(pos - 1);
                Card card = BasicObjectBuilders.loadCard(cfg, 1000 + pos, Card.class);
                if (card != null) {
                    BasicCommands.drawCard(out, card, pos, 0);
                }
            }
        }

        gameState.selectedHandPos = null;
        gameState.selectedCardConfig = null;
        gameState.selectedCardIsUnit = false;
    }


    /** Convenience: clear both selection + target highlights */
    public static void clearSelectionAndHighlights(ActorRef out, GameState gameState) {
        if (gameState == null) return;

        clearCardSelection(out, gameState);
        clearHighlightedTiles(out, gameState);

        gameState.selectUnitId = null;

        if (out != null && !gameState.highlightedMovedTiles.isEmpty()) {
            for (String key : gameState.highlightedMovedTiles) {
                String[] parts = key.split(",");
                if (parts.length != 2) continue;

                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    Tile tile = BasicObjectBuilders.loadTile(x, y);
                    BasicCommands.drawTile(out, tile, 0);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        gameState.highlightedMovedTiles.clear();
    }


    // Returns enemy-occupied tiles surrounding the given coordinates based on player type
    public static List<int[]> getEnemyTiles(int x, int y,boolean isHuman,GameState gameState) {
        List<int[]> surroundingTiles = getSurroundingTiles(x,y);
        List<int[]> result =new ArrayList<>();

        //decide which owner string counts as enemy
        String enemyOwner = isHuman ? "AI" : "HUMAN";

        for (int[] tile : surroundingTiles) {
            Unit unit = gameState.boardUnits.get(gameState.key(tile[0],tile[1]));
            if (unit!=null) {

                String owner = gameState.unitOwner.get(unit.getId());
                if(enemyOwner.equals(owner)) {
                    result.add(tile);
                }
            }
        }
        return result;
    }
    // Generates all valid adjacent tiles within 9x5 board boundaries (8 directions)
    private static List<int[]> getSurroundingTiles(int x, int y) {
        List<int[]> surroundingTiles = new ArrayList<>();

        // Define the 8 possible directions (including diagonals)
        int[][] directions = {
                {0, 1},
                {0, -1},
                {-1, 0},
                {1, 0},
                {-1, 1},
                {1, 1},
                {-1, -1},
                {1, -1}
        };

        for (int[] dir : directions) {
            int newX = x + dir[0];
            int newY = y + dir[1];

            // Check if the new coordinates are within the board boundaries
            if (newX >= 0 && newX < 9 && newY >= 0 && newY < 5) {
                // Add the surrounding tile to the list
                surroundingTiles.add(new int[]{newX,newY});
            }
        }
        return surroundingTiles;
    }

    /**
     * Returns adjacent enemy units that have "provoke".
     * Used to restrict movement/attacks when provoked.
     */
    public static List<Unit> getAdjacentEnemyProvokers(GameState gameState, Unit unit) {
        List<Unit> result = new ArrayList<>();
        if (gameState == null || unit == null) return result;

        String myOwner = gameState.unitOwner.get(unit.getId());
        String enemyOwner = "HUMAN".equals(myOwner) ? "AI" : "HUMAN";

        int x = unit.getPosition().getTilex();
        int y = unit.getPosition().getTiley();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int tx = x + dx;
                int ty = y + dy;
                if (tx < 0 || tx >= 9 || ty < 0 || ty >= 5) continue;

                Unit other = gameState.boardUnits.get(gameState.key(tx, ty));
                if (other == null) continue;

                int otherId = other.getId();
                if (!gameState.provokeUnitIds.contains(otherId)) continue;

                String owner = gameState.unitOwner.get(otherId);
                if (enemyOwner.equals(owner)) {
                    result.add(other);
                }
            }
        }

        return result;
    }

    //Returns true if the unit is currently affected by provoke
    public static boolean isProvoked(GameState gameState, Unit unit) {
        return !getAdjacentEnemyProvokers(gameState, unit).isEmpty();
    }

}