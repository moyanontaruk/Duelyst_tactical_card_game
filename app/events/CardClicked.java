package events;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import utils.BasicObjectBuilders;
import utils.HighlightUtils;
import utils.SpellTargetRules;

import structures.basic.Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class CardClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        // only human turn can select
        if (gameState.gameOver) return;
        if (!"HUMAN".equals(gameState.activePlayer)) return;

        int handPosition = message.get("position").asInt(); // 1..6
        if (handPosition < 1 || handPosition > 6) return;

        // read from current runtime hand, not initial fixed files
        if (handPosition > gameState.humanHand.size()) return;

        String cardConfig = gameState.humanHand.get(handPosition - 1);
        if (cardConfig == null) return;

        // click same selected card again -> cancel selection
        if (gameState.selectedHandPos != null
            && gameState.selectedHandPos == handPosition
            && cardConfig.equals(gameState.selectedCardConfig)) {
        HighlightUtils.clearSelectionAndHighlights(out, gameState);
        return;
        }

        Card card = BasicObjectBuilders.loadCard(cardConfig, 1000 + handPosition, Card.class);
        if (card == null) return;

        // mana check
        if (gameState.humanMana < card.getManacost()) {
            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            return;
        }

        // clear previous selection & highlights
        HighlightUtils.clearSelectionAndHighlights(out, gameState);


        // store selection state
        gameState.selectedHandPos = handPosition;
        gameState.selectedCardConfig = cardConfig;
        gameState.selectedCardIsUnit = card.isCreature();

        // highlight selected card (mode = 1)
        BasicCommands.drawCard(out, card, handPosition, 1);

        // ------------------------------
        // Story #31: spell target highlight
        // ------------------------------
        // Story #32: unit summon highlight
        if (card.isCreature()) {
            List<int[]> summonTiles = getHumanSummonTiles(gameState);
            highlightTilesWhite(out, gameState, summonTiles);
        return;
        }

        // Story #31: spell target highlight
        List<int[]> targets = SpellTargetRules.getValidTargetTiles(gameState, card);
        HighlightUtils.highlightTilesRed(out, gameState, targets);

    }

    
    
    private List<int[]> getHumanSummonTiles(GameState gameState) {
        List<int[]> result = new ArrayList<>();

        int[] avatarPos = gameState.getAvatarPosition("HUMAN");
        int avatarX = avatarPos[0];
        int avatarY = avatarPos[1];

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int x = avatarX + dx;
                int y = avatarY + dy;

                if (x < 0 || x >= 9 || y < 0 || y >= 5) continue;
                if (gameState.boardUnits.containsKey(gameState.key(x, y))) continue;

                result.add(new int[]{x, y});
            }
        }

        return result;
    }

    private void highlightTilesWhite(ActorRef out, GameState gameState, List<int[]> tiles) {
        if (tiles == null || tiles.isEmpty()) return;

        for (int[] xy : tiles) {
            int x = xy[0];
            int y = xy[1];

            Tile tile = BasicObjectBuilders.loadTile(x, y);
            BasicCommands.drawTile(out, tile, 1);
            gameState.highlightedMovedTiles.add(gameState.key(x, y));
        }
    }

}