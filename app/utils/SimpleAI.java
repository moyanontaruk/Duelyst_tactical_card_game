package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Card;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class SimpleAI {

    private SimpleAI() {}

    public static void takeTurn(ActorRef out, GameState gameState) {
        if (gameState == null || gameState.gameOver) return;
        if (!"AI".equals(gameState.activePlayer)) return;

        resetUnitsForTurn(gameState, "AI");
        sleep(300);

        // 1) summon as many affordable units as possible
        boolean summonedSomething = true;
        while (summonedSomething && !gameState.gameOver) {
            summonedSomething = trySummonBestAffordableUnit(out, gameState);
            if (summonedSomething) sleep(350);
        }

        // 2) attack first if already adjacent
        attackAllPossible(out, gameState);

        // 3) move each AI unit toward nearest human target, then try attacking again
        moveAllUnitsTowardEnemy(out, gameState);
        attackAllPossible(out, gameState);

        // 4) finish AI turn
        endAiTurn(out, gameState);
    }
     // ------------------------------------------------------------
    // TURN END
    // ------------------------------------------------------------

    private static void endAiTurn(ActorRef out, GameState gameState) {
        if (gameState.gameOver) return;

        // drain AI mana
        gameState.aiMana = 0;
        BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));

        // switch back to human
        gameState.activePlayer = "HUMAN";
        gameState.turnNumber += 1;

        int manaForHuman = gameState.turnNumber + 1;
        gameState.humanMana = manaForHuman;
        BasicCommands.setPlayer1Mana(out, new Player(gameState.humanHealth, gameState.humanMana));

        resetUnitsForTurn(gameState, "HUMAN");
    }
        private static void resetUnitsForTurn(GameState gameState, String owner) {
        for (Integer unitId : new ArrayList<>(gameState.unitOwner.keySet())) {
            String uOwner = gameState.unitOwner.get(unitId);
            if (owner.equals(uOwner)) {
                gameState.unitHasMoved.put(unitId, false);
                gameState.unitHadAttacked.put(unitId, false);
            }
        }
    }
}