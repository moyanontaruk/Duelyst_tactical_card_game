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

    // ------------------------------------------------------------
    // MOVEMENT
    // ------------------------------------------------------------

       private static void moveAllUnitsTowardEnemy(ActorRef out, GameState gameState) {
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, "AI");

        for (Unit unit : aiUnits) {
            if (unit == null) continue;
            if (gameState.gameOver) return;

            int id = unit.getId();

            if (id == gameState.aiAvatarId) {
                // allow avatar to move too if you want; keep it simple for now
            }

            if (gameState.unitHasMoved.getOrDefault(id, false)) continue;
            if (gameState.unitHadAttacked.getOrDefault(id, false)) continue;

            // if already adjacent to enemy, don't move
            if (findAdjacentEnemy(gameState, unit, "HUMAN") != null) continue;

            int[] step = chooseBestMoveTile(gameState, unit);
            if (step == null) continue;

            moveUnit(out, gameState, unit, step[0], step[1]);
            gameState.unitHasMoved.put(id, true);

            sleep(300);
        }
    }

    private static int[] chooseBestMoveTile(GameState gameState, Unit unit) {
        int ux = unit.getPosition().getTilex();
        int uy = unit.getPosition().getTiley();

        List<int[]> moves = new ArrayList<>();

        // cardinal up to 2
        int[][] dirs = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };

        for (int[] d : dirs) {
            int x1 = ux + d[0];
            int y1 = uy + d[1];
            if (isOnBoard(x1, y1) && !occupied(gameState, x1, y1)) {
                moves.add(new int[]{x1, y1});

                int x2 = ux + 2 * d[0];
                int y2 = uy + 2 * d[1];
                if (isOnBoard(x2, y2) && !occupied(gameState, x2, y2)) {
                    moves.add(new int[]{x2, y2});
                }
            }
        }

        // diagonals by 1
        int[][] diag = {
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] d : diag) {
            int tx = ux + d[0];
            int ty = uy + d[1];
            if (isOnBoard(tx, ty) && !occupied(gameState, tx, ty)) {
                moves.add(new int[]{tx, ty});
            }
        }

        if (moves.isEmpty()) return null;

        Unit nearestHuman = nearestEnemyUnit(gameState, ux, uy, "HUMAN");
        if (nearestHuman == null) return null;

        int hx = nearestHuman.getPosition().getTilex();
        int hy = nearestHuman.getPosition().getTiley();

        moves.sort(Comparator.comparingInt(m -> manhattan(m[0], m[1], hx, hy)));
        return moves.get(0);
    }

    private static void moveUnit(ActorRef out, GameState gameState, Unit unit, int toX, int toY) {
        int id = unit.getId();
        String oldKey = gameState.unitPositionKey.get(id);
        if (oldKey != null) {
            gameState.boardUnits.remove(oldKey);
        }

        Tile dest = BasicObjectBuilders.loadTile(toX, toY);
        BasicCommands.moveUnitToTile(out, unit, dest);
        unit.setPositionByTile(dest);

        String newKey = gameState.key(toX, toY);
        gameState.boardUnits.put(newKey, unit);
        gameState.unitPositionKey.put(id, newKey);
    }
    // ------------------------------------------------------------
    // ATTACKING
    // ------------------------------------------------------------

    private static void attackAllPossible(ActorRef out, GameState gameState) {
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, "AI");

        for (Unit attacker : aiUnits) {
            if (attacker == null) continue;
            if (gameState.gameOver) return;

            int attackerId = attacker.getId();
            if (gameState.unitHadAttacked.getOrDefault(attackerId, false)) continue;

            Unit target = findAdjacentEnemy(gameState, attacker, "HUMAN");
            if (target == null) continue;

            performAttack(out, gameState, attacker, target);
            gameState.unitHadAttacked.put(attackerId, true);

            sleep(250);
        }
    }
    private static void performAttack(ActorRef out, GameState gameState, Unit attacker, Unit defender) {
        if (attacker == null || defender == null) return;

        int attackerId = attacker.getId();
        int defenderId = defender.getId();

        int atk = gameState.unitAttack.getOrDefault(attackerId, 0);
        int defAtk = gameState.unitAttack.getOrDefault(defenderId, 0);

        int defHp = gameState.unitHealth.getOrDefault(defenderId, 0);

        BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);

        int defenderAfter = defHp - atk;
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, defender, defenderAfter);

        checkGameOver(out, gameState);
        if (gameState.gameOver) return;

        // if defender survived and is still adjacent -> counterattack
        boolean defenderAlive = gameState.uiUnitById.containsKey(defenderId);
        if (!defenderAlive) return;

        if (!isAdjacent(attacker, defender)) return;

        int attHp = gameState.unitHealth.getOrDefault(attackerId, 0);

        BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.attack);

        int attackerAfter = attHp - defAtk;
        UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, attacker, attackerAfter);

        checkGameOver(out, gameState);
    }


}

