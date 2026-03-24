package events;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Player;
import utils.BasicObjectBuilders;
import utils.HighlightUtils;
import utils.StunRules;

import java.util.Map;

public class EndTurnClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		if (gameState.gameOver) return;

		// Remove any card selection, move highlights, or spell target highlights
        // before the turn changes over
		HighlightUtils.clearSelectionAndHighlights(out, gameState);

		String current = gameState.activePlayer; // "HUMAN" or "AI"
		boolean isUserClick = (message != null);
		
		// A manual end-turn click should only be accepted during the human turn
		if (isUserClick && !"HUMAN".equals(current)) return;

		// Story #29: when a player's stunned turn ends, clear those stuns
		StunRules.clearStunsForEndingPlayer(gameState, current);

		// ----------------------------------------------------
		// Story #1 + #2 :
		// Draw 1 card at end of HUMAN turn (max to hand position 6)
		// ----------------------------------------------------
		/**if ("HUMAN".equals(current)) {
			int nextHandPos = getNextHumanHandPos(gameState); // 4..6
			if (nextHandPos != -1) {
				String cfg = getHumanCardConfigByIndex(nextHandPos - 1); // index 0-based
				if (cfg != null) {
					Card c = BasicObjectBuilders.loadCard(cfg, 1000 + nextHandPos, Card.class);
					if (c != null) {
						BasicCommands.drawCard(out, c, nextHandPos, 0);
					}
				}
			}
		}*/

		// Story #1 + #2: draw 1 card at end of HUMAN turn, discard if hand is full
		if ("HUMAN".equals(current)) {
			drawTopHumanCardIntoHand(out, gameState);
		}


		// ----------------------------------------------------
		// Story #5: Mana Drain -> 0 (and update UI) for current player
		// ----------------------------------------------------
		if ("HUMAN".equals(current)) {
			gameState.humanMana = 0;
			BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));
		} else {
			gameState.aiMana = 0;
			BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));
		}

		// ----------------------------------------------------
		// Switch turn
		// ----------------------------------------------------
		if ("HUMAN".equals(current)) {
			gameState.activePlayer = "AI";
			BasicCommands.addPlayer1Notification(out, "AI Turn", 2);
		} else {
			gameState.activePlayer = "HUMAN";
			BasicCommands.addPlayer1Notification(out, "Your Turn", 2);

			// count full rounds: only increase when AI finishes and goes back to HUMAN
			gameState.turnNumber += 1;
		}

		String next = gameState.activePlayer;



        // debug reset move/attack flags for the player whos new turn is starting - Maggie
		resetActionsForPlayer(gameState, next);



		// ----------------------------------------------------
		// Story #4: Mana Gain = turnNumber + 1 (and update UI) for next player
		// ----------------------------------------------------
		int manaForThisTurn = gameState.turnNumber + 1;

		if ("HUMAN".equals(next)) {
    	gameState.humanMana = manaForThisTurn;
   	 	BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));
		} else {
    	gameState.aiMana = manaForThisTurn;
    	BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));
			if (gameState.aiHand != null && gameState.aiDeck != null) {
				if (gameState.aiHand.size() < 6 && gameState.aiDeckIndex < gameState.aiDeck.size()) {
					String drawn = gameState.aiDeck.get(gameState.aiDeckIndex);
					gameState.aiDeckIndex++;
					gameState.aiHand.add(drawn);
				}
			}
			gameState.aiTurnPending = true;
		}
	}

	private void drawTopHumanCardIntoHand(ActorRef out, GameState gameState) {
		if (gameState.humanDeckIndex >= gameState.humanDeck.size()) return;

		String cfg = gameState.humanDeck.get(gameState.humanDeckIndex);
		gameState.humanDeckIndex++;

		// hand full -> overdraw, card is discarded
		if (gameState.humanHand.size() >= 6) {
			return;
		}

		gameState.humanHand.add(cfg);

		int handPos = gameState.humanHand.size();
		Card c = BasicObjectBuilders.loadCard(cfg, 1000 + handPos, Card.class);
		if (c != null) {
			BasicCommands.drawCard(out, c, handPos, 0);
		}
	}


	// helper method, resets each turn action flags for all units for that player whos
	//turn is starting
	private void resetActionsForPlayer(GameState gameState, String playerOwner){

		// will interates thru every entry in the map
		for (Map.Entry<Integer, String> entry: gameState.unitOwner.entrySet()){
			Integer unitId = entry.getKey();
			String owner = entry.getValue();

			//only resets unit for the player thats starting their turn
			if (!playerOwner.equals(owner)) {
				continue;
			}
			//false = unit is fresh for the new turn
			gameState.unitHasMoved.put(unitId, false);
			gameState.unitHadAttacked.put(unitId,false);
		}
	}
	}
