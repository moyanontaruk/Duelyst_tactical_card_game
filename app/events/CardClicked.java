package events;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import utils.BasicObjectBuilders;
import utils.HighlightUtils;
import utils.SpellTargetRules;

import java.io.File;
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

        // map handPosition -> initial sorted human cards
        String cardConfig = getInitialHumanCardConfig(handPosition);
        if (cardConfig == null) return;

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
        if (!card.isCreature()) {

            List<int[]> targets =
                    SpellTargetRules.getValidTargetTiles(gameState, card);

            HighlightUtils.highlightTilesRed(out, gameState, targets);
        }
    }

    /**
     * Returns config path for initial hand mapping.
     * handPosition 1..3 -> first 3 sorted "1_*.json" cards.
     * Others -> null.
     */
    
    private String getInitialHumanCardConfig(int handPosition) {

        if (handPosition < 1 || handPosition > 3) return null;

        File dir = new File("conf/gameconfs/cards/");
        String[] p1 = dir.list((d, name) ->
                name.startsWith("1_") && name.endsWith(".json"));

        if (p1 == null || p1.length == 0) return null;

        Arrays.sort(p1);

        int idx = handPosition - 1;
        if (idx >= p1.length) return null;

        return "conf/gameconfs/cards/" + p1[idx];
    }
    
    /** 
    //test #26
    private String getInitialHumanCardConfig(int handPosition) {

    if (handPosition < 1 || handPosition > 6) return null;

    // temporary test: put Sundrop Elixir in slot 1
    if (handPosition == 1) {
        return "conf/gameconfs/cards/2_9_c_s_sundrop_elixir.json";
    }

    File dir = new File("conf/gameconfs/cards/");
    String[] p1 = dir.list((d, name) ->
            name.startsWith("1_") && name.endsWith(".json"));

    if (p1 == null || p1.length == 0) return null;

    Arrays.sort(p1);

    int idx = handPosition - 2; // because slot 1 is occupied by Sundrop now
    if (idx < 0 || idx >= p1.length) return null;

    return "conf/gameconfs/cards/" + p1[idx];
    }
    */
}