package utils;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.UnitAnimationType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SimpleAI {

    private static final int MAX_MOVE = 2;

    private static final int[][] STEP_DIRS = {
            { 1, 0}, {-1, 0}, {0, 1}, {0, -1},
            { 1, 1}, { 1,-1}, {-1, 1}, {-1,-1}
    };

    private static final class PathNode {
        int x, y, dist;
        PathNode prev;

        PathNode(int x, int y, int dist, PathNode prev) {
            this.x = x;
            this.y = y;
            this.dist = dist;
            this.prev = prev;
        }
    }

    private SimpleAI() {}

    public static void takeTurn(ActorRef out, GameState gameState) {
        if (gameState == null || gameState.gameOver) return;
        if (!"AI".equals(gameState.activePlayer)) return;

        resetUnitsForTurn(gameState, "AI");
        sleep(300);

    // 1) cast as many affordable/useful spells as possible
    boolean playedSomething = true;
    while (playedSomething && !gameState.gameOver) {
    playedSomething = tryCastBestAffordableSpell(out, gameState);
    if (playedSomething) sleep(300);
}

    // 2) summon as many affordable units as possible
    boolean summonedSomething = true;
    while (summonedSomething && !gameState.gameOver) {
    summonedSomething = trySummonBestAffordableUnit(out, gameState);
    if (summonedSomething) sleep(350);
}

    // 3) attack first if already adjacent
    attackAllPossible(out, gameState);

    // 4) move each AI unit toward nearest human target, then try attacking again
    moveAllUnitsTowardEnemy(out, gameState);
    attackAllPossible(out, gameState);

    // 5) finish AI turn
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

    private static boolean tryCastBestAffordableSpell(ActorRef out, GameState gameState) {
    List<String> aiCards = getAiCardConfigs();
    if (aiCards.isEmpty()) return false;

    List<Card> affordableSpells = new ArrayList<>();
    List<String> affordableSpellCfgs = new ArrayList<>();

    for (String cfg : aiCards) {
        Card c = BasicObjectBuilders.loadCard(cfg, 9100, Card.class);
        if (c == null) continue;
        if (c.isCreature()) continue;
        if (c.getManacost() > gameState.aiMana) continue;

        affordableSpells.add(c);
        affordableSpellCfgs.add(cfg);
    }

    if (affordableSpells.isEmpty()) return false;

    // Priority:
    // 1. Beam Shock if a good target exists
    // 2. True Strike if a target exists
    // 3. Sundrop Elixir if an injured allied unit exists
    for (int i = 0; i < affordableSpells.size(); i++) {
        Card card = affordableSpells.get(i);
        String name = normalize(card.getCardname());

        if (name.equals("beam shock") || name.equals("beamshock")) {
            Unit target = chooseBestAiBeamShockTarget(gameState);
            if (target == null) continue;

            if (!StunRules.applyStunToUnit(out, gameState, target)) continue;

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

            int[] tile = chooseBestSummonTile(gameState, card); // keep your existing summon method
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

private static int[] chooseBestSummonTile(GameState gameState, Card card) {
    List<int[]> candidates = new ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();

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
                if (seen.contains(key)) continue;

                seen.add(key);
                candidates.add(new int[]{x, y});
            }
        }
    }

    if (candidates.isEmpty()) return null;

    int[] best = null;
    int bestScore = Integer.MAX_VALUE;

    for (int[] pos : candidates) {
        int x = pos[0];
        int y = pos[1];

        int score = distanceToClosestAttackPosition(gameState, x, y, "HUMAN");

        if (wouldBeAdjacentToEnemy(gameState, x, y, "HUMAN")) {
            score -= 20;
        }

        if (score < bestScore) {
            bestScore = score;
            best = pos;
        }
    }

    return best;
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

            if (gameState.unitHasMoved.getOrDefault(id, false)) continue;
            if (gameState.unitHadAttacked.getOrDefault(id, false)) continue;

            // if provoked, do not move
            if (HighlightUtils.isProvoked(gameState, unit)) continue;

            // if already adjacent to enemy, don't move
            if (findAdjacentEnemy(gameState, unit, "HUMAN") != null) continue;
            int[] step = chooseBestMoveTile(gameState, unit);
            if (step == null) continue;

            moveUnit(out, gameState, unit, step[0], step[1]);
            gameState.unitHasMoved.put(id, true);

            sleep(1000);
        }
    }

    private static int[] chooseBestMoveTile(GameState gameState, Unit unit) {
        List<PathNode> reachable = getReachableTiles(gameState, unit, MAX_MOVE);
        if (reachable.isEmpty()) return null;

        PathNode best = null;
        int bestScore = Integer.MAX_VALUE;

        for (PathNode node : reachable) {
            int score = distanceToClosestAttackPosition(gameState, node.x, node.y, "HUMAN");

            // strong preference for ending next to an enemy
            if (wouldBeAdjacentToEnemy(gameState, node.x, node.y, "HUMAN")) {
                score -= 100;
            }

            // slight preference for shorter path
            score += node.dist;

            if (score < bestScore) {
                bestScore = score;
                best = node;
            }
        }

        if (best == null) return null;
        return new int[]{best.x, best.y};
    }

    private static List<PathNode> getReachableTiles(GameState gameState, Unit unit, int maxMove) {
        List<PathNode> reachable = new ArrayList<>();

        int startX = unit.getPosition().getTilex();
        int startY = unit.getPosition().getTiley();

        boolean[][] visited = new boolean[9][5];
        List<PathNode> queue = new ArrayList<>();

        PathNode start = new PathNode(startX, startY, 0, null);
        queue.add(start);
        visited[startX][startY] = true;

        for (int i = 0; i < queue.size(); i++) {
            PathNode current = queue.get(i);

            if (!(current.x == startX && current.y == startY)) {
                reachable.add(current);
            }

            if (current.dist >= maxMove) continue;

            for (int[] d : STEP_DIRS) {
                int nx = current.x + d[0];
                int ny = current.y + d[1];

                if (!isOnBoard(nx, ny)) continue;
                if (visited[nx][ny]) continue;
                if (!canStep(gameState, current.x, current.y, nx, ny)) continue;

                visited[nx][ny] = true;
                queue.add(new PathNode(nx, ny, current.dist + 1, current));
            }
        }

        return reachable;
    }

    private static boolean canStep(GameState gameState, int fromX, int fromY, int toX, int toY) {
        if (!isOnBoard(toX, toY)) return false;
        if (occupied(gameState, toX, toY)) return false;

        int dx = toX - fromX;
        int dy = toY - fromY;

        // stop diagonal corner-cutting through occupied tiles
        if (Math.abs(dx) == 1 && Math.abs(dy) == 1) {
            if (occupied(gameState, fromX + dx, fromY) || occupied(gameState, fromX, fromY + dy)) {
                return false;
            }
        }

        return true;
    }

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

    private static int distanceToClosestAttackPosition(GameState gameState, int x, int y, String enemyOwner) {
        int best = Integer.MAX_VALUE;

        for (Unit enemy : gameState.boardUnits.values()) {
            if (enemy == null) continue;

            String owner = gameState.unitOwner.get(enemy.getId());
            if (!enemyOwner.equals(owner)) continue;

            int ex = enemy.getPosition().getTilex();
            int ey = enemy.getPosition().getTiley();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    int ax = ex + dx;
                    int ay = ey + dy;

                    if (!isOnBoard(ax, ay)) continue;

                    boolean blocked = occupied(gameState, ax, ay) && !(ax == x && ay == y);
                    if (blocked) continue;

                    int dist = manhattan(x, y, ax, ay);
                    if (dist < best) best = dist;
                }
            }
        }

        return best;
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

            //performAttack(out, gameState, attacker, target);
            attacker.attack(gameState,out,target);
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

    private static void checkGameOver(ActorRef out, GameState gameState) {
        if (gameState.humanHealth <= 0) {
            gameState.gameOver = true;
            gameState.winner = "AI";
            BasicCommands.addPlayer1Notification(out, "AI wins!", 5);
            return;
        }

        if (gameState.aiHealth <= 0) {
            gameState.gameOver = true;
            gameState.winner = "HUMAN";
            BasicCommands.addPlayer1Notification(out, "You win!", 5);
        }
    }

    // ------------------------------------------------------------
    // TARGETING HELPERS
    // ------------------------------------------------------------

    private static Unit findAdjacentEnemy(GameState gameState, Unit unit, String enemyOwner) {
        int x = unit.getPosition().getTilex();
        int y = unit.getPosition().getTiley();

        Unit best = null;
        int bestHp = Integer.MAX_VALUE;

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

                if (gameState.provokeUnitIds.contains(other.getId())) {
                    adjacentProvokers.add(other);
                }
            }
        }

        List<Unit> candidates = new ArrayList<>();

        if (!adjacentProvokers.isEmpty()) {
            candidates = adjacentProvokers;
        } else {
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
                        candidates.add(other);
                    }
                }
            }
        }

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

        for (Unit u : gameState.boardUnits.values()) {
            if (u == null) continue;

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

    private static List<Unit> getUnitsOwnedBy(GameState gameState, String owner) {
        List<Unit> res = new ArrayList<>();
        for (Unit u : gameState.boardUnits.values()) {
            if (u == null) continue;
            String uOwner = gameState.unitOwner.get(u.getId());
            if (owner.equals(uOwner)) {
                res.add(u);
            }
        }
        return res;
    }


    private static Unit chooseBestAiEnemyUnitTarget(GameState gameState) {
    Unit best = null;
    int bestHp = Integer.MAX_VALUE;

    for (Unit u : gameState.boardUnits.values()) {
        if (u == null) continue;

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

    for (Unit u : gameState.boardUnits.values()) {
        if (u == null) continue;

        int id = u.getId();
        if (id == gameState.humanAvatarId || id == gameState.aiAvatarId) continue;

        String owner = gameState.unitOwner.get(id);
        if (!"HUMAN".equals(owner)) continue;

        int score = 0;

        // prefer units that have not yet acted
        if (!gameState.unitHasMoved.getOrDefault(id, false)) score += 2;
        if (!gameState.unitHadAttacked.getOrDefault(id, false)) score += 2;

        // prefer stronger targets a bit
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

    for (Unit u : gameState.boardUnits.values()) {
        if (u == null) continue;

        int id = u.getId();

        // heal allied non-avatar units only
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