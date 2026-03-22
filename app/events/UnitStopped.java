package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.HighlightUtils;
import utils.SummonUtils;
import java.util.ArrayList;
import java.util.List;

public class UnitStopped implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        int unitid = message.get("id").asInt();

        clearMoveHighlights(out, gameState);
        HighlightUtils.clearHighlightedTiles(out, gameState);

        Integer targetId = gameState.pendingAttackAfterMove.remove(unitid);
        if (targetId == null) return;

        Unit attacker = gameState.uiUnitById.get(unitid);
        Unit defender = gameState.uiUnitById.get(targetId);

        if (attacker == null || defender == null) return;

        int ax = attacker.getPosition().getTilex();
        int ay = attacker.getPosition().getTiley();
        int dx = defender.getPosition().getTilex();
        int dy = defender.getPosition().getTiley();

        boolean adjacent = Math.abs(ax - dx) <= 1
                && Math.abs(ay - dy) <= 1
                && !(ax == dx && ay == dy);

        if (!adjacent) return;

        attacker.attack(gameState, out, defender);
        triggerHornOnHit(out, gameState, attacker);
    }

    private void clearMoveHighlights(ActorRef out, GameState gameState) {
        if (gameState.highlightedMovedTiles.isEmpty()) return;

        for (String key : gameState.highlightedMovedTiles) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);

            Tile tile = BasicObjectBuilders.loadTile(x, y);
            BasicCommands.drawTile(out, tile, 0);
        }

        gameState.highlightedMovedTiles.clear();
    }

    // fixing horn of forsaken for it to match the same local as it does in tileclicked.java
    //before it was only cardinal direction, after fix will check all 8 tiles around the human avatar
    // then randomly choice empty tile
    private void triggerHornOnHit(ActorRef out, GameState gameState, Unit attacker) {
        // Safety -- if there is no attacker, do nothing
        if (attacker == null)
            return;

        // Horn only triggers if the human is attacking
        if (attacker.getId() != gameState.humanAvatarId)
            return;
        // If Horn is not currently equipped, do nothing
        if (!gameState.hornOfForsaken)
            return;
        // extra safety --
        // if robustness is already 0 or less, Horn should not trigger,, matches tileclicked version

        if (gameState.hornRobustness <= 0)
            return;

        // get current board position of human avatar
        int[] avatarPos = gameState.getAvatarPosition("HUMAN");
        int px = avatarPos[0];
        int py = avatarPos[1];

        // collect ALL empty adjacent tiles here
        List<int[]> emptyAdjacent = new ArrayList<>();

        // loop through all 8 surrounding tiles, not just 4
        // skip (0,0) because that is the avatar's own tile
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0)
                    continue;

                int tx = px + dx;
                int ty = py + dy;
                // skip tiles that are off the board
                if (tx < 0 || tx >= 9 || ty < 0 || ty >= 5)
                    continue;

                String key = gameState.key(tx, ty);

                // consider only tiles that are empty
                if (!gameState.boardUnits.containsKey(key)) {
                    emptyAdjacent.add(new int[]{tx, ty});
                }
            }
        }

        // if no empty adjacent tiles, horn cannot spawn anything
        if (emptyAdjacent.isEmpty())
            return;

        // Randomly choose ONE empty adjacent tile, before it was just picking the first one

        int idx = (int) (Math.random() * emptyAdjacent.size());
        int[] chosen = emptyAdjacent.get(idx);

        // Spawn the wraithling on the chosen tile
        SummonUtils.spawnWraithling(out, gameState, chosen[0], chosen[1], "HUMAN");
    }
}