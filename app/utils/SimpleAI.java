package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SimpleAI {

    private SimpleAI() {}

    public static void takeTurn(ActorRef out, GameState gameState) {
    if (gameState == null || gameState.gameOver) return;
    if (!"AI".equals(gameState.activePlayer)) return;

    try {
        sleep(100);

        // 1) cast useful spells while affordable, but never forever
        int spellActions = 0;
        while (!gameState.gameOver
                && "AI".equals(gameState.activePlayer)
                && spellActions < 10) {

            boolean playedSpell = tryCastBestAffordableSpell(out, gameState);
            if (!playedSpell) break;

            spellActions++;
            sleep(100);
        }

        // 2) summon useful units while affordable, but never forever
        int summonActions = 0;
        while (!gameState.gameOver
                && "AI".equals(gameState.activePlayer)
                && summonActions < 10) {

            boolean summonedUnit = trySummonBestAffordableUnit(out, gameState);
            if (!summonedUnit) break;

            summonActions++;
            sleep(120);
        }

        // 3) let units act
        playUnits(out, gameState);
        sleep(100);

    } catch (Exception e) {
        e.printStackTrace();
        BasicCommands.addPlayer1Notification(out, "AI error - ending turn", 2);
    } finally {
        // ALWAYS return control to the normal turn flow
        if (gameState != null && !gameState.gameOver && "AI".equals(gameState.activePlayer)) {
            new events.EndTurnClicked().processEvent(out, gameState, null);
        }
    }
}

    // ------------------------------------------------------------
    // MAIN UNIT PLAY LOOP
    // ------------------------------------------------------------

    private static void playUnits(ActorRef out, GameState gameState) {
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, "AI");

        for (Unit unit : aiUnits) {
            if (unit == null) continue;
            if (gameState.gameOver) return;
            if (!"AI".equals(gameState.activePlayer)) return;

            int id = unit.getId();

            if (gameState.unitHadAttacked.getOrDefault(id, false)) continue;

            // If already next to an enemy, attack first
            Unit adjacentEnemy = findAdjacentEnemy(gameState, unit, "HUMAN");
            if (adjacentEnemy != null) {
                doAttack(out, gameState, unit, adjacentEnemy);
                sleep(150);
                continue;
            }

            // If provoked and not adjacent, cannot move away to do something else
            if (HighlightUtils.isProvoked(gameState, unit)) {
                continue;
            }

            boolean movedThisTurn = false;

            if (!gameState.unitHasMoved.getOrDefault(id, false)) {
            int[] bestMove = chooseBestMoveTile(gameState, unit);
            if (bestMove != null) {
            boolean moved = moveUnit(out, gameState, unit, bestMove[0], bestMove[1]);
            if (moved) {
            gameState.unitHasMoved.put(id, true);
            movedThisTurn = true;

            // give the move animation time to fully finish before any attack
            sleep(450);
        }
    }
}

// Only check attack after movement has fully settled
Unit targetAfterMove = findAdjacentEnemy(gameState, unit, "HUMAN");
if (targetAfterMove != null && !gameState.unitHadAttacked.getOrDefault(id, false)) {
    doAttack(out, gameState, unit, targetAfterMove);

    if (movedThisTurn) {
        sleep(200);
    } else {
        sleep(120);
    }
}
        }
    }

    // ------------------------------------------------------------
    // SPELLS
    // ------------------------------------------------------------

    private static boolean tryCastBestAffordableSpell(ActorRef out, GameState gameState) {
        List<String> aiCards = getAiCardConfigs();
        if (aiCards.isEmpty()) return false;

        List<Card> affordableSpells = new ArrayList<>();
        for (String cfg : aiCards) {
            Card c = BasicObjectBuilders.loadCard(cfg, 9100, Card.class);
            if (c == null) continue;
            if (c.isCreature()) continue;
            if (c.getManacost() > gameState.aiMana) continue;
            affordableSpells.add(c);
        }

        if (affordableSpells.isEmpty()) return false;

        for (Card card : affordableSpells) {
            String name = normalize(card.getCardname());

            if (name.equals("beam shock") || name.equals("beamshock")) {
                Unit target = chooseBestAiBeamShockTarget(gameState);
                if (target == null) continue;

                boolean ok = StunRules.applyStunToUnit(out, gameState, target);
                if (!ok) continue;

                gameState.aiMana -= card.getManacost();
                BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));
                return true;
            }

            if (name.equals("true strike")) {
                Unit target = chooseBestAiEnemyUnitTarget(gameState);
                if (target == null) continue;

                int hp = gameState.unitHealth.getOrDefault(target.getId(), 0);
                UnitDeathUtils.setUnitHealthAndCheckDeath(out, gameState, target, hp - 2);

                gameState.aiMana -= card.getManacost();
                BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));
                return true;
            }

            if (name.equals("sundrop elixir")) {
                Unit target = chooseBestAiHealTarget(gameState);
                if (target == null) continue;

                boolean ok = HealSpellUtils.healUnit(
                        out,
                        gameState,
                        target,
                        5,
                        "AI",
                        false,
                        null
                );

                if (!ok) continue;

                gameState.aiMana -= card.getManacost();
                BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));
                return true;
            }
        }

        return false;
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

        // Prefer higher-cost units first
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
            if (card.getBigCard() == null) continue;
            if (card.getUnitConfig() == null) continue;

            int[] tile = chooseBestSummonTile(gameState);
            if (tile == null) continue;

            String summonKey = gameState.key(tile[0], tile[1]);
            if (gameState.boardUnits.containsKey(summonKey)) continue;

            Unit unit = SummonUtils.spawnUnit(
                    out,
                    gameState,
                    card.getUnitConfig(),
                    tile[0],
                    tile[1],
                    card.getBigCard().getAttack(),
                    card.getBigCard().getHealth(),
                    "AI"
            );

            if (unit == null) continue;

            gameState.aiMana -= card.getManacost();
            BasicCommands.setPlayer2Mana(out, new Player(gameState.aiHealth, gameState.aiMana));

            // Newly summoned units should not move/attack this turn
            gameState.unitHasMoved.put(unit.getId(), true);
            gameState.unitHadAttacked.put(unit.getId(), true);

            OpeningGambitResolver.onSummoned(out, gameState, unit, card.getCardname());
            return true;
        }

        return false;
    }

    private static int[] chooseBestSummonTile(GameState gameState) {
        List<int[]> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Unit unit : gameState.boardUnits.values()) {
            if (unit == null) continue;

            String owner = gameState.unitOwner.get(unit.getId());
            if (!"AI".equals(owner)) continue;

            int unitX = unit.getPosition().getTilex();
            int unitY = unit.getPosition().getTiley();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    int x = unitX + dx;
                    int y = unitY + dy;

                    if (!isOnBoard(x, y)) continue;
                    if (occupied(gameState, x, y)) continue;

                    String key = gameState.key(x, y);
                    if (!seen.add(key)) continue;

                    candidates.add(new int[]{x, y});
                }
            }
        }

        if (candidates.isEmpty()) return null;

        int[] best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int[] pos : candidates) {
            int x = pos[0];
            int y = pos[1];

            int score = 0;

            // prefer forward pressure toward human side
            score += (8 - x) * 3;

            // prefer being near enemies
            int dist = distanceToClosestEnemy(gameState, x, y, "HUMAN");
            if (dist != Integer.MAX_VALUE) {
                score += (20 - dist);
            }

            // a little bonus if it can support an attack line
            if (wouldBeAdjacentToEnemy(gameState, x, y, "HUMAN")) {
                score += 6;
            }

            if (score > bestScore) {
                bestScore = score;
                best = pos;
            }
        }

        return best;
    }

    // ------------------------------------------------------------
    // MOVEMENT
    // ------------------------------------------------------------

    private static int[] chooseBestMoveTile(GameState gameState, Unit unit) {
        List<int[]> legalMoves = getValidMoveTilesLikeHuman(gameState, unit);
        if (legalMoves.isEmpty()) return null;

        int[] best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int[] pos : legalMoves) {
            int x = pos[0];
            int y = pos[1];

            int score = evaluateMove(gameState, unit, x, y);

            if (score > bestScore) {
                bestScore = score;
                best = pos;
            }
        }

        return best;
    }

    private static int evaluateMove(GameState gameState, Unit unit, int x, int y) {
        int score = 0;

        int currentX = unit.getPosition().getTilex();
        int currentY = unit.getPosition().getTiley();

        Unit nearest = nearestEnemyUnit(gameState, currentX, currentY, "HUMAN");
        if (nearest != null) {
            int oldDist = manhattan(currentX, currentY, nearest.getPosition().getTilex(), nearest.getPosition().getTiley());
            int newDist = manhattan(x, y, nearest.getPosition().getTilex(), nearest.getPosition().getTiley());

            score += (oldDist - newDist) * 10;
        }

        // Very strong preference for moving to a tile from which it can attack immediately
        if (wouldBeAdjacentToEnemy(gameState, x, y, "HUMAN")) {
            score += 100;
        }

        // mild preference to move leftwards toward human side
        score += (8 - x) * 2;

        // small preference to stay closer to centre rows
        score -= Math.abs(y - 2);

        return score;
    }

    private static boolean moveUnit(ActorRef out, GameState gameState, Unit unit, int toX, int toY) {
        if (unit == null) return false;
        if (!isOnBoard(toX, toY)) return false;

        int id = unit.getId();
        String newKey = gameState.key(toX, toY);

        Unit occupant = gameState.boardUnits.get(newKey);
        if (occupant != null && occupant.getId() != id) return false;

        int fromX = unit.getPosition().getTilex();
        int fromY = unit.getPosition().getTiley();
        String oldKey = gameState.unitPositionKey.get(id);

        if (toX == fromX && toY == fromY) return false;

        // Remove stale entries for this unit id from board map
        List<String> staleKeys = new ArrayList<>();
        for (Map.Entry<String, Unit> e : gameState.boardUnits.entrySet()) {
            Unit u = e.getValue();
            if (u != null && u.getId() == id) {
                staleKeys.add(e.getKey());
            }
        }
        for (String key : staleKeys) {
            gameState.boardUnits.remove(key);
        }

        Tile dest = BasicObjectBuilders.loadTile(toX, toY);
        boolean yFirst = decideMoveOrder(gameState, fromX, fromY, toX, toY);

        BasicCommands.moveUnitToTile(out, unit, dest, yFirst);
        sleep(300);

        unit.setPositionByTile(dest);

        gameState.boardUnits.put(newKey, unit);
        gameState.unitPositionKey.put(id, newKey);

        // clean old key if still around
        if (oldKey != null && !oldKey.equals(newKey)) {
            Unit oldOccupant = gameState.boardUnits.get(oldKey);
            if (oldOccupant != null && oldOccupant.getId() == id) {
                gameState.boardUnits.remove(oldKey);
            }
        }

        return true;
    }

    private static List<int[]> getValidMoveTilesLikeHuman(GameState gameState, Unit unit) {
        List<int[]> validTiles = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int startX = unit.getPosition().getTilex();
        int startY = unit.getPosition().getTiley();

        int[][] cardinalDirs = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };

        for (int[] dir : cardinalDirs) {
            int dx = dir[0];
            int dy = dir[1];

            int step1X = startX + dx;
            int step1Y = startY + dy;

            if (!isOnBoard(step1X, step1Y)) continue;
            if (occupied(gameState, step1X, step1Y)) continue;

            addUniqueTile(validTiles, seen, step1X, step1Y, gameState);

            int step2X = startX + 2 * dx;
            int step2Y = startY + 2 * dy;

            if (!isOnBoard(step2X, step2Y)) continue;
            if (occupied(gameState, step2X, step2Y)) continue;

            addUniqueTile(validTiles, seen, step2X, step2Y, gameState);
        }

        int[][] diagonalDirs = {
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] dir : diagonalDirs) {
            int x = startX + dir[0];
            int y = startY + dir[1];

            if (!isOnBoard(x, y)) continue;
            if (occupied(gameState, x, y)) continue;

            addUniqueTile(validTiles, seen, x, y, gameState);
        }

        return validTiles;
    }

    private static void addUniqueTile(List<int[]> tiles, Set<String> seen, int x, int y, GameState gameState) {
        String key = gameState.key(x, y);
        if (seen.add(key)) {
            tiles.add(new int[]{x, y});
        }
    }

    // ------------------------------------------------------------
    // ATTACKING
    // ------------------------------------------------------------

    private static void doAttack(ActorRef out, GameState gameState, Unit attacker, Unit target) {
        if (attacker == null || target == null) return;
        if (gameState.gameOver) return;

        int attackerId = attacker.getId();
        if (gameState.unitHadAttacked.getOrDefault(attackerId, false)) return;

        if (!isAdjacent(attacker, target)) return;

        attacker.attack(gameState, out, target);
        gameState.unitHadAttacked.put(attackerId, true);
    }

    // ------------------------------------------------------------
    // TARGETING HELPERS
    // ------------------------------------------------------------

    private static Unit findAdjacentEnemy(GameState gameState, Unit unit, String enemyOwner) {
        int x = unit.getPosition().getTilex();
        int y = unit.getPosition().getTiley();

        List<Unit> adjacentEnemies = new ArrayList<>();
        List<Unit> adjacentProvokers = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int tx = x + dx;
                int ty = y + dy;

                if (!isOnBoard(tx, ty)) continue;

                Unit other = gameState.boardUnits.get(gameState.key(tx, ty));
                if (other == null) continue;

                String owner = gameState.unitOwner.get(other.getId());
                if (!enemyOwner.equals(owner)) continue;

                adjacentEnemies.add(other);

                if (gameState.provokeUnitIds.contains(other.getId())) {
                    adjacentProvokers.add(other);
                }
            }
        }

        List<Unit> candidates = adjacentProvokers.isEmpty() ? adjacentEnemies : adjacentProvokers;

        Unit best = null;
        int bestHp = Integer.MAX_VALUE;

        for (Unit other : candidates) {
            int hp = gameState.unitHealth.getOrDefault(other.getId(), 999);
            if (hp < bestHp) {
                bestHp = hp;
                best = other;
            }
        }

        return best;
    }

    private static Unit nearestEnemyUnit(GameState gameState, int x, int y, String enemyOwner) {
        Unit best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Unit u : uniqueBoardUnits(gameState)) {
            String owner = gameState.unitOwner.get(u.getId());
            if (!enemyOwner.equals(owner)) continue;

            int ux = u.getPosition().getTilex();
            int uy = u.getPosition().getTiley();
            int dist = manhattan(x, y, ux, uy);

            if (dist < bestDist) {
                bestDist = dist;
                best = u;
            }
        }

        return best;
    }

    private static Unit chooseBestAiEnemyUnitTarget(GameState gameState) {
        Unit best = null;
        int bestHp = Integer.MAX_VALUE;

        for (Unit u : uniqueBoardUnits(gameState)) {
            int id = u.getId();
            if (id == gameState.humanAvatarId || id == gameState.aiAvatarId) continue;

            String owner = gameState.unitOwner.get(id);
            if (!"HUMAN".equals(owner)) continue;

            int hp = gameState.unitHealth.getOrDefault(id, 999);
            if (hp < bestHp) {
                bestHp = hp;
                best = u;
            }
        }

        return best;
    }

    private static Unit chooseBestAiBeamShockTarget(GameState gameState) {
        Unit best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Unit u : uniqueBoardUnits(gameState)) {
            int id = u.getId();
            if (id == gameState.humanAvatarId || id == gameState.aiAvatarId) continue;

            String owner = gameState.unitOwner.get(id);
            if (!"HUMAN".equals(owner)) continue;

            int score = 0;

            if (!gameState.unitHasMoved.getOrDefault(id, false)) score += 2;
            if (!gameState.unitHadAttacked.getOrDefault(id, false)) score += 2;
            score += gameState.unitAttack.getOrDefault(id, 0);

            if (score > bestScore) {
                bestScore = score;
                best = u;
            }
        }

        return best;
    }

    private static Unit chooseBestAiHealTarget(GameState gameState) {
        Unit best = null;
        int bestMissingHealth = 0;

        for (Unit u : uniqueBoardUnits(gameState)) {
            int id = u.getId();

            if (id == gameState.humanAvatarId || id == gameState.aiAvatarId) continue;

            String owner = gameState.unitOwner.get(id);
            if (!"AI".equals(owner)) continue;

            int hp = gameState.unitHealth.getOrDefault(id, 0);
            int maxHp = gameState.unitMaxHealth.getOrDefault(id, hp);
            int missing = maxHp - hp;

            if (missing > bestMissingHealth) {
                bestMissingHealth = missing;
                best = u;
            }
        }

        return best;
    }

    // ------------------------------------------------------------
    // COLLECTION HELPERS
    // ------------------------------------------------------------

    private static List<Unit> getUnitsOwnedBy(GameState gameState, String owner) {
        List<Unit> res = new ArrayList<>();

        for (Unit u : uniqueBoardUnits(gameState)) {
            String unitOwner = gameState.unitOwner.get(u.getId());
            if (owner.equals(unitOwner)) {
                res.add(u);
            }
        }

        return res;
    }

    private static List<Unit> uniqueBoardUnits(GameState gameState) {
        List<Unit> res = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        for (Unit u : gameState.boardUnits.values()) {
            if (u == null) continue;
            if (seenIds.add(u.getId())) {
                res.add(u);
            }
        }

        return res;
    }

    // ------------------------------------------------------------
    // CARD CONFIGS
    // ------------------------------------------------------------

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
    // GEOMETRY / BOARD HELPERS
    // ------------------------------------------------------------

    private static boolean wouldBeAdjacentToEnemy(GameState gameState, int x, int y, String enemyOwner) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                int tx = x + dx;
                int ty = y + dy;

                if (!isOnBoard(tx, ty)) continue;

                Unit other = gameState.boardUnits.get(gameState.key(tx, ty));
                if (other == null) continue;

                String owner = gameState.unitOwner.get(other.getId());
                if (enemyOwner.equals(owner)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int distanceToClosestEnemy(GameState gameState, int x, int y, String enemyOwner) {
        int best = Integer.MAX_VALUE;

        for (Unit enemy : uniqueBoardUnits(gameState)) {
            String owner = gameState.unitOwner.get(enemy.getId());
            if (!enemyOwner.equals(owner)) continue;

            int ex = enemy.getPosition().getTilex();
            int ey = enemy.getPosition().getTiley();

            int dist = manhattan(x, y, ex, ey);
            if (dist < best) {
                best = dist;
            }
        }

        return best;
    }

    private static boolean decideMoveOrder(GameState gameState, int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;

        if (dx == 0 || dy == 0) {
            return true;
        }

        boolean moveXFirstBlocked = gameState.boardUnits.containsKey(gameState.key(x2, y1));
        return moveXFirstBlocked;
    }

    private static boolean occupied(GameState gameState, int x, int y) {
        return gameState.boardUnits.containsKey(gameState.key(x, y));
    }

    private static boolean isAdjacent(Unit a, Unit b) {
        int ax = a.getPosition().getTilex();
        int ay = a.getPosition().getTiley();
        int bx = b.getPosition().getTilex();
        int by = b.getPosition().getTiley();

        int dx = Math.abs(ax - bx);
        int dy = Math.abs(ay - by);

        return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
    }

    private static boolean isOnBoard(int x, int y) {
        return x >= 0 && x < 9 && y >= 0 && y < 5;
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static String normalize(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}