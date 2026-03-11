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
    // ------------------------------------------------------------
    // SUMMONING
    // ------------------------------------------------------------

    private static boolean trySummonBestAffordableUnit(ActorRef out, GameState gameState) {
        List<String> aiCards = getAiCardConfigs();
        if (aiCards.isEmpty()) return false;

        List<String> affordableUnits = new ArrayList<>();
        for (String cfg : aiCards) {
            Card c = BasicObjectBuilders.loadCard(cfg, 9000, Card.class);
            if (c == null) continue;
            if (!c.isCreature()) continue;
            if (c.getManacost() > gameState.aiMana) continue;
            affordableUnits.add(cfg);
        }

        if (affordableUnits.isEmpty()) return false;

        // prefer highest mana cost first
        affordableUnits.sort((a, b) -> {
            Card ca = BasicObjectBuilders.loadCard(a, 9001, Card.class);
            Card cb = BasicObjectBuilders.loadCard(b, 9002, Card.class);
            int ma = (ca == null) ? -1 : ca.getManacost();
            int mb = (cb == null) ? -1 : cb.getManacost();
            return Integer.compare(mb, ma);
        });

        for (String cfg : affordableUnits) {
            Card card = BasicObjectBuilders.loadCard(cfg, 9003, Card.class);
            if (card == null) continue;

            int[] tile = chooseBestSummonTile(gameState, card);
            if (tile == null) continue;

            Unit unit = SummonUtils.spawnUnit(
                    out,
                    gameState,
                    card.getUnitConfig(),
                    tile[0], tile[1],
                    card.getBigCard().getAttack(),
                    card.getBigCard().getHealth(),
                    "AI"
            );

            if (unit == null) continue;

            gameState.aiMana -= card.getManacost();
            BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));

            // newly summoned units should not act immediately in this baseline AI
            gameState.unitHasMoved.put(unit.getId(), true);
            gameState.unitHadAttacked.put(unit.getId(), true);

            OpeningGambitResolver.onSummoned(out, gameState, unit, card.getCardname());
            return true;
        }

        return false;
    }

    private static int[] chooseBestSummonTile(GameState gameState, Card card) {
        List<int[]> candidates = new ArrayList<>();

        // Special case: Ironcliff Guardian can be summoned anywhere
        String name = normalize(card.getCardname());
        boolean summonAnywhere = name.equals("ironcliff guardian");

        if (summonAnywhere) {
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 5; y++) {
                    if (!gameState.boardUnits.containsKey(gameState.key(x, y))) {
                        candidates.add(new int[]{x, y});
                    }
                }
            }
        } else {
            for (String key : gameState.boardUnits.keySet()) {
                Unit u = gameState.boardUnits.get(key);
                if (u == null) continue;

                String owner = gameState.unitOwner.get(u.getId());
                if (!"AI".equals(owner)) continue;

                int ux = u.getPosition().getTilex();
                int uy = u.getPosition().getTiley();

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;

                        int tx = ux + dx;
                        int ty = uy + dy;
                        if (!isOnBoard(tx, ty)) continue;
                        if (gameState.boardUnits.containsKey(gameState.key(tx, ty))) continue;

                        candidates.add(new int[]{tx, ty});
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;

        // prefer tiles closer to human avatar
        candidates.sort(Comparator.comparingInt(t ->
                manhattan(t[0], t[1], 1, 2)
        ));

        return candidates.get(0);
    }

    private static List<String> getAiCardConfigs() {
        File dir = new File("conf/gameconfs/cards/");
        String[] p2 = dir.list((d, name) -> name.startsWith("2_") && name.endsWith(".json"));
        if (p2 == null) return new ArrayList<>();

        Arrays.sort(p2);
        List<String> res = new ArrayList<>();
        for (String s : p2) {
            res.add("conf/gameconfs/cards/" + s);
        }
        return res;
    }

}