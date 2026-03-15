package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.HighlightUtils;

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
}