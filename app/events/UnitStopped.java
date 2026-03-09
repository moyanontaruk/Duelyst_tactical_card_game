package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.HighlightUtils;

/**
 * Indicates that a unit instance has stopped moving. 
 * The event reports the unique id of the unit.
 * 
 * { 
 *   messageType = “unitStopped”
 *   id = <unit id>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class UnitStopped implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		
		int unitid = message.get("id").asInt();
		Unit moveUnit = gameState.uiUnitById.get(unitid);
		clearMoveHighlights(out,gameState);
		HighlightUtils.clearHighlightedTiles(out,gameState);
	}
	private void clearMoveHighlights(ActorRef out, GameState gameState) {
		if (gameState.highlightedMovedTiles.isEmpty()) return;
		for (String key : gameState.highlightedMovedTiles) {
			String[] parts = key.split(",");
			int x = Integer.parseInt(parts[0]);
			int y = Integer.parseInt(parts[1]);

			Tile tile = BasicObjectBuilders.loadTile(x, y);
			BasicCommands.drawTile(out, tile, 0); //won't highlight if =0
		}

		gameState.highlightedMovedTiles.clear();
	}

}
