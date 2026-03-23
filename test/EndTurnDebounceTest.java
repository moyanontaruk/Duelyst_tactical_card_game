import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import events.EndTurnClicked;
import events.Heartbeat;
import play.libs.Json;
import structures.GameState;

public class EndTurnDebounceTest {
    private GameState gameState;

    @Before
    public void setup() {
        BasicCommands.altTell = new CheckMessageIsNotNullOnTell();
        gameState = new GameState();
        gameState.activePlayer = "HUMAN";
        gameState.turnNumber = 1;
    }

    @Test
    public void rapidEndTurnClicksShouldNotSkipMultipleTurns() {
        EndTurnClicked endTurn = new EndTurnClicked();
        ObjectNode click = Json.newObject();
        click.put("messagetype", "endturnclicked");

        endTurn.processEvent(null, gameState, click);
        endTurn.processEvent(null, gameState, click);
        endTurn.processEvent(null, gameState, click);

        assertEquals("AI", gameState.activePlayer);
        assertEquals(1, gameState.turnNumber);
        assertTrue(gameState.aiTurnPending);

        new Heartbeat().processEvent(null, gameState, Json.newObject());

        assertEquals("HUMAN", gameState.activePlayer);
        assertEquals(2, gameState.turnNumber);
        assertFalse(gameState.aiTurnPending);
        assertFalse(gameState.aiTurnRunning);
    }
}
