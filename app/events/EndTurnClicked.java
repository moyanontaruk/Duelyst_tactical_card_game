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

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import utils.SimpleAI;

public class EndTurnClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		if (gameState.gameOver) return;


		HighlightUtils.clearSelectionAndHighlights(out, gameState);

		String current = gameState.activePlayer; // "HUMAN" or "AI"

		// Story #29: when a player's stunned turn ends, clear those stuns
		StunRules.clearStunsForEndingPlayer(gameState, current);

		// ----------------------------------------------------
		// Story #1 + #2 :
		// Draw 1 card at end of HUMAN turn (max to hand position 6)
		// ----------------------------------------------------
		if ("HUMAN".equals(current)) {
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
		} else {
			gameState.activePlayer = "HUMAN";
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

    	// let AI play immediately
    	SimpleAI.takeTurn(out, gameState);
		}
	}

	/**
	 * Decide next human hand position to draw into (4..6).
	 * In this simplified model, we assume:
	 * - Initialize draws positions 1..3.
	 * - Each time HUMAN ends a turn, we draw one more card.
	 * The draw count equals (turnNumber - 1) for completed full rounds,
	 * but HUMAN can end turn multiple times; we keep it simple:
	 * position = 3 + number_of_human_end_turns_done_so_far.
	 *
	 * Without adding new fields, we approximate human_end_turns_done = turnNumber - 1,
	 * and cap to 6.
	 */
	private int getNextHumanHandPos(GameState gameState) {
		// After round 1 (turnNumber=1), first human end-turn draw goes to pos 4.
		int pos = 3 + gameState.turnNumber;
		if (pos < 4) pos = 4;
		if (pos > 6) return -1;
		return pos;
	}

	/**
	 * Get the human card config by sorted index in conf/gameconfs/cards/ (1_*.json).
	 */
	private String getHumanCardConfigByIndex(int idx) {
		File dir = new File("conf/gameconfs/cards/");
		String[] p1 = dir.list((d, name) -> name.startsWith("1_") && name.endsWith(".json"));
		if (p1 == null || p1.length == 0) return null;

		Arrays.sort(p1);
		if (idx < 0 || idx >= p1.length) return null;

		return "conf/gameconfs/cards/" + p1[idx];
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
