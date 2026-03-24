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
import structures.basic.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.HashSet;
import java.util.Set;
import structures.basic.Unit;


public class CardClicked implements EventProcessor {

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        // only human turn can select
        if (gameState.gameOver) return;
        if (!"HUMAN".equals(gameState.activePlayer)) return;

        // Hand positions are shown as 1..6 in the UI
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

        // mana check; Stop here if the player cannot afford the card
        if (gameState.humanMana < card.getManacost()) {
            BasicCommands.addPlayer1Notification(out, "Not enough mana", 2);
            return;
        }

        // Clear anything that was selected or highlighted before choosing this card
        HighlightUtils.clearSelectionAndHighlights(out, gameState);


        // Store the new selection so later click handlers know what the player chose
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
            BasicObjectBuilders.sleep(150);
        return;
        }

        // Story #31: spell target highlight
        List<int[]> targets = SpellTargetRules.getValidTargetTiles(gameState, card);

        // lower case card name
        String cardName = card.getCardname() == null ? "" : card.getCardname().trim().toLowerCase();
        // friendlies are highlighted white 
        if (cardName.equals("horn of the forsaken")){
            HighlightUtils.highlightTilesWhite(out, gameState, targets);
        } else {
            //enemy/attack is highlighted red
            HighlightUtils.highlightTilesRed(out, gameState, targets);
        }
    }

    
    
private List<int[]> getHumanSummonTiles(GameState gameState) {
    List<int[]> result = new ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();

    // A unit can be summoned onto any empty adjacent tile around a human-owned unit.
    // The seen set prevents duplicate tiles when multiple friendly units share neighbours
    for (Unit unit : gameState.boardUnits.values()) {
        if (unit == null) continue;

        String owner = gameState.unitOwner.get(unit.getId());
        if (!"HUMAN".equals(owner)) continue;

        int unitX = unit.getPosition().getTilex();
        int unitY = unit.getPosition().getTiley();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int x = unitX + dx;
                int y = unitY + dy;

                if (x < 0 || x >= 9 || y < 0 || y >= 5) continue;
                if (gameState.boardUnits.containsKey(gameState.key(x, y))) continue;

                String key = gameState.key(x, y);
                if (seen.contains(key)) continue;

                seen.add(key);
                result.add(new int[]{x, y});
            }
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