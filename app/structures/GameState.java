package structures;

import java.util.*;

import structures.basic.Unit;

public class GameState {

	// ---- Template flags ----
	public boolean gameInitialized = false;
	public boolean something = false;

	// ---- Game end state ----
	public boolean gameOver = false;
	public String winner = null; // "HUMAN" or "AI"

	// ---- Players ----
	public int humanHealth = 20;
	public int aiHealth = 20;

	public int humanMana = 0;
	public int aiMana = 0;

	// ---- Turn ----
	public int turnNumber = 1;
	public String activePlayer = "HUMAN";

	// ---- Units on board ----
	// key = "x,y"
	public final Map<String, Unit> boardUnits = new HashMap<>();

	// ---- Unit stats tracking (server-side) ----
	// Note: the provided Unit class is primarily a UI representation.

	public final Map<Integer, Integer> unitHealth = new HashMap<>();
	public final Map<Integer, Integer> unitAttack = new HashMap<>();
	// unitId -> board key ("x,y") to allow fast removal from boardUnits when a unit dies
	public final Map<Integer, String> unitPositionKey = new HashMap<>();

	// ---- Unit id generator ----
	public int nextUnitId = 1000;
	public int allocateUnitId() {
		return nextUnitId++;
	}

	// ---- Card selection ----
	public Integer selectedHandPos = null;
	public String selectedCardConfig = null;
	public boolean selectedCardIsUnit = false;

	// ---- UI Unit mapping ----
	public final Map<Integer, Unit> uiUnitById = new HashMap<>();

	// ---- Highlighted tiles ----
	public final Set<String> highlightedTargetTiles = new HashSet<>();

	// ---- Helpers ----
	public String key(int x, int y) {
		return x + "," + y;
	}

	public final Map<Integer, Integer> unitMaxHealth = new HashMap<>();
	public final Map<Integer, String> unitOwner = new HashMap<>();
}