package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;
import utils.SimpleAI;

/**
 * In the user’s browser, the game is running in an infinite loop, where there is around a 1 second delay 
 * between each loop. Its during each loop that the UI acts on the commands that have been sent to it. A 
 * heartbeat event is fired at the end of each loop iteration. As with all events this is received by the Game 
 * Actor, which you can use to trigger game logic.
 * 
 * { 
 *   String messageType = “heartbeat”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class Heartbeat implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		if (gameState == null || gameState.gameOver) return;
		if (!gameState.aiTurnPending) return;
		if (!"AI".equals(gameState.activePlayer)) {
			gameState.aiTurnPending = false;
			return;
		}
		if (gameState.aiTurnRunning) return;

		gameState.aiTurnPending = false;
		gameState.aiTurnRunning = true;
		try {
			SimpleAI.takeTurn(out, gameState);
		} finally {
			gameState.aiTurnRunning = false;
		}
	}

}
