package com.miniciv.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * A Nation's state inside the engine.
 */
class Nation {

	/**
	 * How many Turns after a Lasting Event ends no other can begin; the
	 * Player is never told.
	 */
	static final int LASTING_COOLDOWN_TURNS = 3;

	private final NationIdentity identity;

	private int gold;

	private int population;

	private int research;

	/**
	 * The Technologies the Nation has unlocked, in the order unlocked.
	 */
	private final Set<String> technologies = new LinkedHashSet<>();

	private final List<Construction> construction = new ArrayList<>();

	/**
	 * How many of each Building the Nation has completed, in the order first
	 * completed.
	 */
	private final Map<String, Integer> buildings = new LinkedHashMap<>();

	/**
	 * How much Defense each defensive Building type has lost; see
	 * {@link BuildingDamage}.
	 */
	private final Map<String, Integer> defenseLost = new LinkedHashMap<>();

	/**
	 * How many of each non-defensive Building type are Damaged, counting those
	 * being repaired; see {@link BuildingDamage}.
	 */
	private final Map<String, Integer> damaged = new LinkedHashMap<>();

	/**
	 * The Buildings being repaired or rebuilt, in the order started.
	 */
	private final List<Repair> repairs = new ArrayList<>();

	/**
	 * The Units in training, in the order started.
	 */
	private final List<Training> training = new ArrayList<>();

	/**
	 * How many of each Unit type the Nation has at home, in the order first
	 * trained.
	 */
	private final Map<String, Integer> army = new LinkedHashMap<>();

	/**
	 * How many of each Scout type the Nation has roaming the wilds, in the
	 * order first trained; they are never part of the Army.
	 */
	private final Map<String, Integer> scouts = new LinkedHashMap<>();

	/**
	 * The Tasks the Nation's Scouts are set to, in the order set; the Scouts
	 * on none are Idle.
	 */
	private final List<Task> tasks = new ArrayList<>();

	/**
	 * The number the next Task set will be told apart by.
	 */
	private int nextTaskId = 1;

	/**
	 * The Nations this one has Located, in the order Located.
	 */
	private final Set<String> located = new LinkedHashSet<>();

	/**
	 * The Nation's Warparties abroad, in the order sent.
	 */
	private final List<Warparty> warparties = new ArrayList<>();

	/**
	 * The number the next Warparty sent will be told apart by.
	 */
	private int nextWarpartyId = 1;

	/**
	 * The Nation's Spies abroad, in the order sent.
	 */
	private final List<Spy> spies = new ArrayList<>();

	/**
	 * The number the next Spy sent will be told apart by.
	 */
	private int nextSpyId = 1;

	/**
	 * What the Nation has learned of each other Nation's hidden state, by
	 * Nation.
	 */
	private final Map<String, Facts> learned = new LinkedHashMap<>();

	private TurnReport turnReport;

	/**
	 * The Dilemmas to settle as this Turn ends, in the order they happened.
	 */
	private final List<FacedDilemma> dilemmas = new ArrayList<>();

	/**
	 * The Hauls seized from caught Spies to keep or send back as this Turn
	 * ends, in the order seized.
	 */
	private final List<HaulDecision> hauls = new ArrayList<>();

	/**
	 * The number the next Haul decision will be told apart by.
	 */
	private int nextHaulId = 1;

	/**
	 * The names of every Dilemma the Nation has faced this Game.
	 */
	private final Set<String> everFaced = new LinkedHashSet<>();

	/**
	 * The Nation or Nations that conquered this one, such as "Rome and Egypt",
	 * or null while it is still in the Game.
	 */
	private String conqueredBy;

	/**
	 * Whether the Nation was removed from the Game, its Player having been
	 * disconnected for too long.
	 */
	private boolean removed;

	/**
	 * The Lasting Event active for the Nation, or null if none is.
	 */
	private LastingEvent lastingEvent;

	/**
	 * The Turn the active Lasting Event began on, as which it does not yet
	 * apply.
	 */
	private int lastingBegunOn;

	/**
	 * The Turn the Nation's last Lasting Event ended on, or 0 if none has.
	 */
	private int lastingEndedOn;

	private int version = 1;

	Nation(NationIdentity identity, int gold, int population, int research) {
		this.identity = identity;
		this.gold = gold;
		this.population = population;
		this.research = research;
	}

	/**
	 * A copy of the Nation's resources, Buildings, work in progress and Units
	 * that can be changed without changing the Nation, such as to forecast
	 * what resolving the Turn would do to it.
	 */
	Nation copy() {
		Nation copy = new Nation(identity, gold, population, research);
		copy.construction.addAll(construction);
		copy.buildings.putAll(buildings);
		copy.defenseLost.putAll(defenseLost);
		copy.damaged.putAll(damaged);
		copy.repairs.addAll(repairs);
		copy.training.addAll(training);
		copy.army.putAll(army);
		copy.scouts.putAll(scouts);
		copy.tasks.addAll(tasks);
		copy.warparties.addAll(warparties);
		copy.spies.addAll(spies);
		copy.lastingEvent = lastingEvent;
		copy.lastingBegunOn = lastingBegunOn;
		copy.lastingEndedOn = lastingEndedOn;
		return copy;
	}

	String name() {
		return identity.name();
	}

	/**
	 * The Nation named by its Ruler, such as "Caesar of Rome", for what it
	 * directly did to another Nation.
	 */
	String ruled() {
		return identity.ruled();
	}

	/**
	 * Gives the Nation the report of the Turn that just resolved.
	 */
	void turnReport(TurnReport turnReport) {
		this.turnReport = turnReport;
		version++;
	}

	NationView view(int turn, List<MapNation> map, List<ArrivedWarparty> arrivals,
			List<Tribute> tributes, List<Encounter> encounters, Ruleset ruleset, GoldForecast goldForecast,
			String winner) {
		List<BuildingType> availableBuildings = ruleset.buildings()
			.stream()
			.filter(building -> hasAccessTo(building.name(), ruleset))
			.toList();
		List<UnitType> availableUnits = ruleset.units().stream().filter(unit -> hasAccessTo(unit.name(), ruleset)).toList();
		return new NationView(version, turn, name(), gold, population, research, map, availableBuildings,
				Collections.unmodifiableMap(new LinkedHashMap<>(buildings)), construction, repairs, defense(ruleset), damage(ruleset),
				sellValues(ruleset),
				availableUnits,
				Collections.unmodifiableMap(new LinkedHashMap<>(army)), scouts(), idleScouts(), tasks, List.copyOf(located), training, warparties, spies, ruleset.missions(), TravelBracket.ALL, Spy.TRAVEL_TURNS, upkeep(ruleset),
				goldForecast, lastingEvent,
				arrivals, tributes, encounters, techTree(ruleset), turnReport, awaitingChoice(winner),
				haulsAwaiting(winner), conqueredBy, winner);
	}

	/**
	 * Marks a change the Nation's Player should see that is not to the Nation
	 * itself, such as the Game being won.
	 */
	void changed() {
		version++;
	}

	boolean conquered() {
		return conqueredBy != null;
	}

	boolean removed() {
		return removed;
	}

	/**
	 * Takes the Nation out of the Game without it being conquered; its
	 * Warparties, Spies abroad, Scouts and their Tasks, and the Hauls it was
	 * to decide on are gone.
	 */
	void remove() {
		removed = true;
		warparties.clear();
		spies.clear();
		scouts.clear();
		tasks.clear();
		hauls.clear();
	}

	/**
	 * Whether the Nation is still in the Game, neither conquered nor removed.
	 */
	boolean inGame() {
		return !conquered() && !removed;
	}

	/**
	 * This Nation as another Nation sees it on the Map.
	 *
	 * @param learned what that other Nation has learned of this one
	 */
	MapNation onMap(Facts learned) {
		MapNation.Standing standing = conquered() ? MapNation.Standing.CONQUERED
				: removed ? MapNation.Standing.REMOVED : MapNation.Standing.IN_GAME;
		return new MapNation(identity, standing, learned);
	}

	/**
	 * What the Nation has learned of another Nation's hidden state.
	 */
	Facts learnedOf(String nation) {
		return learned.getOrDefault(nation, Facts.NONE);
	}

	/**
	 * Records what the Nation has now learned of another Nation's hidden
	 * state.
	 */
	void learn(String nation, Facts facts) {
		learned.put(nation, facts);
		version++;
	}

	/**
	 * Whether the Nation is left with neither Population nor Army, and so is
	 * conquered: its Scouts, never at home, cannot hold it.
	 */
	boolean hasNeitherPopulationNorArmy() {
		return population == 0 && army.isEmpty();
	}

	/**
	 * Takes the Nation out of the Game: its Warparties, Spies abroad, Scouts
	 * and their Tasks, and the Hauls it was to decide on disappear, and its conquerors seize a share of its Gold and Research,
	 * rounded down.
	 *
	 * @param conquerors the Nation or Nations that conquered it, such as "Rome
	 * and Egypt"
	 * @return the Gold and Research seized
	 */
	Plunder conquer(String conquerors, double share) {
		conqueredBy = conquerors;
		warparties.clear();
		spies.clear();
		scouts.clear();
		tasks.clear();
		hauls.clear();
		return plundered((int) Math.floor(gold * share), (int) Math.floor(research * share));
	}

	/**
	 * Adds Gold and Research seized from a conquered Nation.
	 */
	void seize(Plunder seized) {
		gold += seized.gold();
		research += seized.research();
	}

	/**
	 * Turns every Warparty heading to a Nation, and not yet having fought
	 * there, back home.
	 *
	 * @return those turned back, as they were before
	 */
	List<Warparty> turnBackFrom(String target) {
		List<Warparty> turnedBack = new ArrayList<>();
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (!current.homeward() && current.target().equals(target)) {
				it.set(current.turnedBack());
				turnedBack.add(current);
			}
		}
		return turnedBack;
	}

	/**
	 * Turns every Spy at a Nation, or heading to it, back home.
	 *
	 * @return those turned back, as they were before
	 */
	List<Spy> turnSpiesBackFrom(String target) {
		List<Spy> turnedBack = new ArrayList<>();
		for (var it = spies.listIterator(); it.hasNext();) {
			Spy current = it.next();
			if (!current.homeward() && current.target().equals(target)) {
				it.set(current.turnedBack());
				turnedBack.add(current);
			}
		}
		if (!turnedBack.isEmpty()) {
			version++;
		}
		return turnedBack;
	}

	int gold() {
		return gold;
	}

	int population() {
		return population;
	}

	int research() {
		return research;
	}

	/**
	 * How many of each Building the Nation has completed, in the order first
	 * completed.
	 */
	Map<String, Integer> buildings() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(buildings));
	}

	/**
	 * The Nation's Spies at their targets, in the order sent.
	 */
	List<Spy> spiesInPlace() {
		return spies.stream().filter(Spy::inPlace).toList();
	}

	/**
	 * The Nation's Spies still travelling, to their targets or home, in the
	 * order sent.
	 */
	List<Spy> spiesTravelling() {
		return spies.stream().filter(spy -> !spy.inPlace()).toList();
	}

	/**
	 * The Nation's Spies abroad, in the order sent.
	 */
	List<Spy> spies() {
		return List.copyOf(spies);
	}

	/**
	 * Loses one of the Nation's Spies abroad, caught by another Nation.
	 *
	 * @param spy the Spy, as it is now or was earlier this Turn
	 */
	void loseSpy(Spy spy) {
		spies.removeIf(current -> current.id() == spy.id());
		version++;
	}

	/**
	 * Turns one of the Nation's Spies in place back home, Unmasked.
	 *
	 * @param spy the Spy, as it is now or was earlier this Turn
	 */
	void fleeHome(Spy spy) {
		changeSpy(spy, Spy::turnedBack);
	}

	/**
	 * Changes one of the Nation's Spies abroad.
	 *
	 * @param spy the Spy, as it is now or was earlier this Turn
	 * @param change what becomes of it, given it as it is now
	 */
	void changeSpy(Spy spy, UnaryOperator<Spy> change) {
		spies.replaceAll(current -> current.id() == spy.id() ? change.apply(current) : current);
		version++;
	}

	/**
	 * The Nation's Spies Escaping, in the order sent.
	 */
	List<Spy> spiesEscaping() {
		return spies.stream().filter(Spy::escaping).toList();
	}

	/**
	 * The Nation's Warparties abroad, in the order sent.
	 */
	List<Warparty> warparties() {
		return List.copyOf(warparties);
	}

	/**
	 * The Nation's Warparty abroad with the given number, if any.
	 */
	Optional<Warparty> warparty(int id) {
		return warparties.stream().filter(warparty -> warparty.id() == id).findFirst();
	}

	/**
	 * Turns one of the Nation's Warparties, not yet heading home, back home;
	 * see {@link Warparty#turnedBack()}.
	 *
	 * @param warparty the Warparty, as {@link #warparty(int)} gave it
	 */
	void recall(Warparty warparty) {
		warparties.set(warparties.indexOf(warparty), warparty.turnedBack());
		version++;
	}

	/**
	 * Changes the Nation's trip for one of its Warparties travelling by the
	 * given number of Turns; see {@link Warparty#delayed(int)}.
	 *
	 * @param warparty the Warparty, as {@link #warparties()} gave it
	 * @return the Warparty as it is now
	 */
	Warparty delay(Warparty warparty, int turns) {
		for (var it = warparties.listIterator(); it.hasNext();) {
			if (it.next() == warparty) {
				Warparty delayed = warparty.delayed(turns);
				it.set(delayed);
				return delayed;
			}
		}
		throw new IllegalArgumentException("Not a Warparty of " + name());
	}

	/**
	 * Units that joined or left the Army or a Warparty.
	 *
	 * @param joined how many of each Unit type joined, in the order given
	 * @param left how many of each Unit type left, in the order given
	 */
	record UnitsChanged(Map<String, Integer> joined, Map<String, Integer> left) {
	}

	/**
	 * Has Units join the Army, or leave it if below 0, no more than it has.
	 *
	 * @param units how many of each Unit type join or leave
	 */
	UnitsChanged changeArmy(Map<String, Integer> units) {
		UnitsChanged changed = changeUnits(army, units);
		version++;
		return changed;
	}

	/**
	 * Has Units join one of the Nation's Warparties abroad, or leave it if
	 * below 0, no more than it holds; its trip takes as long as before. One
	 * left with no Units disappears.
	 *
	 * @param warparty the Warparty, as {@link #warparties()} gave it
	 * @param units how many of each Unit type join or leave
	 */
	UnitsChanged changeWarparty(Warparty warparty, Map<String, Integer> units, Ruleset ruleset) {
		int at = warparties.indexOf(warparty);
		Map<String, Integer> holding = new LinkedHashMap<>(warparty.units());
		UnitsChanged changed = changeUnits(holding, units);
		if (holding.isEmpty()) {
			warparties.remove(at);
		}
		else {
			warparties.set(at, warparty.with(holding, ruleset));
		}
		version++;
		return changed;
	}

	private static UnitsChanged changeUnits(Map<String, Integer> holding, Map<String, Integer> units) {
		Map<String, Integer> joined = new LinkedHashMap<>();
		Map<String, Integer> left = new LinkedHashMap<>();
		units.forEach((unit, count) -> {
			if (count > 0) {
				holding.merge(unit, count, Integer::sum);
				joined.put(unit, count);
			}
			else {
				int leaving = Math.min(-count, holding.getOrDefault(unit, 0));
				if (leaving > 0) {
					holding.computeIfPresent(unit, (u, owned) -> owned == leaving ? null : owned - leaving);
					left.put(unit, leaving);
				}
			}
		});
		return new UnitsChanged(joined, left);
	}

	/**
	 * How many Buildings under construction, Repairs, Rebuilds and Units in
	 * training started together the Nation has.
	 */
	int workInProgress() {
		return construction.size() + repairs.size() + training.size();
	}

	/**
	 * Stalls one piece of work in progress by the given number of Turns, or
	 * speeds it up if below 0; it still finishes no earlier than as the next
	 * Turn resolves.
	 *
	 * @param pick which one, counting Buildings under construction, then
	 *        Repairs and Rebuilds, then Units in training, each in the order
	 *        started, from 0
	 * @param nextTurn the Turn about to start
	 * @return the work as it is now
	 */
	Work changeWork(int pick, int turns, int nextTurn) {
		version++;
		if (pick < construction.size()) {
			Construction current = construction.get(pick);
			Construction changed = new Construction(current.building(), Math.max(1, current.turnsLeft() + turns));
			construction.set(pick, changed);
			return changed;
		}
		pick -= construction.size();
		if (pick < repairs.size()) {
			Repair current = repairs.get(pick);
			Repair changed = new Repair(current.building(), current.rebuild(), current.defense(),
					Math.max(1, current.turnsLeft() + turns));
			repairs.set(pick, changed);
			return changed;
		}
		pick -= repairs.size();
		Training current = training.get(pick);
		Training changed = new Training(current.unit(), current.count(),
				Math.max(nextTurn + 1, current.readyOnTurn() + turns));
		training.set(pick, changed);
		return changed;
	}

	/**
	 * Has a Dilemma await the Player's choice before the next Turn ends.
	 */
	void faceDilemma(Event dilemma) {
		dilemmas.add(new FacedDilemma(dilemma, null));
		everFaced.add(dilemma.name());
		version++;
	}

	/**
	 * Whether the Nation has faced the Dilemma before in this Game.
	 */
	boolean hasFaced(Event dilemma) {
		return everFaced.contains(dilemma.name());
	}

	/**
	 * A Dilemma the Nation faces this Turn, and the choice its Player made.
	 *
	 * @param chosen the choice made, or null while the Player has not chosen
	 */
	record FacedDilemma(Event dilemma, Event.Choice chosen) {

		/**
		 * The choice that applies as the Turn ends: the one made, or else the
		 * worst.
		 */
		Event.Choice applying() {
			return chosen == null ? dilemma.worstChoice() : chosen;
		}

		Dilemma view() {
			return new Dilemma(dilemma.name(), dilemma.text(),
					dilemma.choices().stream().map(choice -> new Dilemma.Choice(choice.name(), choice.worst())).toList(),
					chosen == null ? null : chosen.name());
		}

	}

	/**
	 * The Dilemmas the Player sees: none once the Nation is out of the Game or
	 * the Game is won.
	 */
	private List<Dilemma> awaitingChoice(String winner) {
		return inGame() && winner == null ? dilemmas.stream().map(FacedDilemma::view).toList() : List.of();
	}

	/**
	 * The Haul decisions the Player sees: none once the Nation is out of the
	 * Game or the Game is won.
	 */
	private List<HaulDecision> haulsAwaiting(String winner) {
		return inGame() && winner == null ? List.copyOf(hauls) : List.of();
	}

	/**
	 * The Dilemma of the given name the Nation faces this Turn, if any.
	 */
	Optional<FacedDilemma> dilemma(String name) {
		return dilemmas.stream().filter(faced -> faced.dilemma().name().equals(name)).findFirst();
	}

	/**
	 * Records the Player's choice for a Dilemma the Nation faces, to apply as
	 * the Turn ends.
	 */
	void choose(FacedDilemma faced, Event.Choice choice) {
		dilemmas.set(dilemmas.indexOf(faced), new FacedDilemma(faced.dilemma(), choice));
		version++;
	}

	/**
	 * Stops facing every Dilemma, as the Turn ends.
	 *
	 * @return the Dilemmas the Nation faced, in the order they happened
	 */
	List<FacedDilemma> settleDilemmas() {
		List<FacedDilemma> settled = List.copyOf(dilemmas);
		dilemmas.clear();
		return settled;
	}

	/**
	 * Has the Nation decide, by the end of the Turn, whether to keep a Haul it
	 * seized from a caught Spy or send it back.
	 */
	void faceHaul(String stolenFrom, Haul haul) {
		hauls.add(new HaulDecision(nextHaulId++, stolenFrom, haul, HaulDecision.Choice.NONE));
		version++;
	}

	/**
	 * Records the Player's choice to keep a Haul or send it back, to apply as
	 * the Turn ends.
	 */
	ActionResult answerHaul(int id, boolean keep) {
		for (int at = 0; at < hauls.size(); at++) {
			if (hauls.get(at).id() == id) {
				hauls.set(at, hauls.get(at).chosen(keep ? HaulDecision.Choice.KEEP : HaulDecision.Choice.RETURN));
				version++;
				return ActionResult.ACCEPTED;
			}
		}
		return ActionResult.rejected("You have no such Haul to decide on.");
	}

	/**
	 * Stops deciding on every Haul, as the Turn ends.
	 *
	 * @return the Haul decisions, in the order seized
	 */
	List<HaulDecision> settleHauls() {
		List<HaulDecision> settled = List.copyOf(hauls);
		if (!hauls.isEmpty()) {
			hauls.clear();
			version++;
		}
		return settled;
	}

	/**
	 * How much an Event changed a Nation's Gold, Population and Research by.
	 */
	record Change(int gold, int population, int research) {
	}

	/**
	 * Changes the Nation's Gold, Population and Research by the given amounts,
	 * taking no more than it has.
	 *
	 * @return how much each actually changed by
	 */
	Change change(int gold, int population, int research) {
		Change change = new Change(Math.max(gold, -this.gold), Math.max(population, -this.population),
				Math.max(research, -this.research));
		gain(change.gold(), change.population(), change.research());
		return change;
	}

	/**
	 * Whether a Lasting Event may begin for the Nation as the given Turn
	 * resolves: none is active, and none ended as that Turn or the last
	 * {@value #LASTING_COOLDOWN_TURNS} resolved.
	 */
	boolean canBeginLasting(int turn) {
		return lastingEvent == null && (lastingEndedOn == 0 || turn - lastingEndedOn > LASTING_COOLDOWN_TURNS);
	}

	/**
	 * Begins a Lasting Event for the Nation as the given Turn resolves; it
	 * first applies as the next Turn does.
	 */
	void beginLasting(LastingEvent lasting, int turn) {
		lastingEvent = lasting;
		lastingBegunOn = turn;
		version++;
	}

	/**
	 * Uses up one of the active Lasting Event's Turns as the given Turn
	 * resolves, ending it once it has none left. One that began as this Turn
	 * resolves does not apply yet.
	 *
	 * @return the Lasting Event as it applies this Turn, if one does
	 */
	Optional<LastingEvent> passLastingTurn(int turn) {
		if (lastingEvent == null || lastingBegunOn == turn) {
			return Optional.empty();
		}
		LastingEvent applying = lastingEvent;
		lastingEvent = applying.turnsLeft() > 1 ? applying.withTurnPassed() : null;
		if (lastingEvent == null) {
			lastingEndedOn = turn;
		}
		version++;
		return Optional.of(applying);
	}

	/**
	 * Whether the Nation has at least the given Gold and Population.
	 */
	boolean canAfford(int gold, int population) {
		return gold <= this.gold && population <= this.population;
	}

	/**
	 * Adds a Haul to what the Nation owns.
	 */
	void gain(Haul haul) {
		gain(haul.resource() == Resource.GOLD ? haul.amount() : 0, 0,
				haul.resource() == Resource.RESEARCH ? haul.amount() : 0);
	}

	void gain(int gold, int population, int research) {
		this.gold += gold;
		this.population += population;
		this.research += research;
	}

	/**
	 * Whether the Nation may start a Unit or Building type: either no
	 * Technology is needed for it, or the Nation has unlocked the one that is.
	 */
	private boolean hasAccessTo(String unitOrBuilding, Ruleset ruleset) {
		return lockedBehind(unitOrBuilding, ruleset).isEmpty();
	}

	/**
	 * Why the Nation may not start a Unit or Building type yet, if a
	 * Technology it has not unlocked is needed for it.
	 */
	Optional<ActionResult> locked(String unitOrBuilding, Ruleset ruleset) {
		return lockedBehind(unitOrBuilding, ruleset)
			.map(technology -> sees(technology, ruleset) ? requiredFirst(unitOrBuilding, List.of(technology.name()))
					: ActionResult.rejected(unitOrBuilding + " requires a Technology still unknown to you."));
	}

	private Optional<Technology> lockedBehind(String unitOrBuilding, Ruleset ruleset) {
		return ruleset.unlockedBy(unitOrBuilding).filter(technology -> !technologies.contains(technology.name()));
	}

	private static ActionResult requiredFirst(String what, List<String> technologies) {
		return ActionResult.rejected(what + " requires " + String.join(" and ", technologies) + " to be unlocked first.");
	}

	/**
	 * Every Technology in the Tech Tree as the Nation stands with it, hiding those
	 * more than one step beyond what it can unlock now.
	 */
	private List<TechTreeEntry> techTree(Ruleset ruleset) {
		Set<String> available = available(ruleset);
		return ruleset.technologies().stream().map(technology -> {
			TechTreeEntry.Standing standing = standing(technology, available);
			return standing == TechTreeEntry.Standing.HIDDEN ? TechTreeEntry.hidden()
					: techTreeEntry(technology, standing);
		}).toList();
	}

	/**
	 * Whether the Nation sees a Technology in its Tech Tree, rather than only
	 * that it is there.
	 */
	boolean sees(Technology technology, Ruleset ruleset) {
		return standing(technology, available(ruleset)) != TechTreeEntry.Standing.HIDDEN;
	}

	/**
	 * The names of the Technologies the Nation can unlock now.
	 */
	private Set<String> available(Ruleset ruleset) {
		return ruleset.technologies()
			.stream()
			.filter(technology -> !technologies.contains(technology.name())
					&& technologies.containsAll(technology.prerequisites()))
			.map(Technology::name)
			.collect(Collectors.toSet());
	}

	private TechTreeEntry.Standing standing(Technology technology, Set<String> available) {
		if (technologies.contains(technology.name())) {
			return TechTreeEntry.Standing.UNLOCKED;
		}
		if (available.contains(technology.name())) {
			return TechTreeEntry.Standing.AVAILABLE;
		}
		if (technology.prerequisites()
			.stream()
			.allMatch(prerequisite -> technologies.contains(prerequisite) || available.contains(prerequisite))) {
			return TechTreeEntry.Standing.LOCKED;
		}
		return TechTreeEntry.Standing.HIDDEN;
	}

	private static TechTreeEntry techTreeEntry(Technology technology, TechTreeEntry.Standing standing) {
		return new TechTreeEntry(technology.name(), technology.research(), technology.prerequisites(),
				technology.unlocks(), standing);
	}

	/**
	 * Pays a Technology's Research and unlocks it, once its prerequisites are
	 * unlocked.
	 */
	ActionResult unlock(Technology technology) {
		if (technologies.contains(technology.name())) {
			return ActionResult.rejected("You have already unlocked " + technology.name() + ".");
		}
		List<String> missing = technology.prerequisites()
			.stream()
			.filter(prerequisite -> !technologies.contains(prerequisite))
			.toList();
		if (!missing.isEmpty()) {
			return requiredFirst(technology.name(), missing);
		}
		if (technology.research() > research) {
			return ActionResult.rejected(technology.name() + " costs " + technology.research()
					+ " Research, but you have only " + research + ".");
		}
		research -= technology.research();
		technologies.add(technology.name());
		version++;
		return ActionResult.ACCEPTED;
	}

	ActionResult startBuilding(BuildingType building) {
		if (building.gold() > gold) {
			return ActionResult
				.rejected(building.name() + " costs " + building.gold() + " Gold, but you have only " + gold + ".");
		}
		gold -= building.gold();
		construction.add(new Construction(building.name(), building.turns()));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Pays upfront to repair one of a Building type's Damaged Buildings: for a
	 * defensive one, half the Gold and Turns constructing it costs, in
	 * proportion to the Defense it lost, rounded up; for any other, half of
	 * them, rounded up.
	 */
	ActionResult repair(BuildingType building) {
		int unrepaired = unrepaired(building);
		if (!defensive(building)) {
			if (unrepaired == 0) {
				return ActionResult.rejected("You have no damaged " + building.name() + " to repair.");
			}
			return startRepair(building, false, 0, half(building.gold()), half(building.turns()), "Repairing ");
		}
		int defense = unrepaired % building.defense();
		if (defense == 0) {
			return ActionResult.rejected("You have no damaged " + building.name() + " to repair.");
		}
		return startRepair(building, false, defense, halfInProportion(building.gold(), defense, building),
				halfInProportion(building.turns(), defense, building), "Repairing ");
	}

	/**
	 * Pays upfront to rebuild one of a defensive Building type's destroyed
	 * Buildings, for half the Gold and Turns constructing it costs, rounded up.
	 */
	ActionResult rebuild(BuildingType building) {
		if (!defensive(building) || unrepaired(building) < building.defense()) {
			return ActionResult.rejected("You have no destroyed " + building.name() + " to rebuild.");
		}
		return startRepair(building, true, building.defense(), half(building.gold()), half(building.turns()),
				"Rebuilding ");
	}

	private ActionResult startRepair(BuildingType building, boolean rebuild, int defense, int cost, int turns,
			String doing) {
		if (cost > gold) {
			return ActionResult
				.rejected(doing + building.name() + " costs " + cost + " Gold, but you have only " + gold + ".");
		}
		gold -= cost;
		repairs.add(new Repair(building.name(), rebuild, defense, turns));
		version++;
		return ActionResult.ACCEPTED;
	}

	private static boolean defensive(BuildingType building) {
		return building.defense() > 0;
	}

	/**
	 * A Building's Rebuild Gold or Turns, half its construction's, in
	 * proportion to the share of its Defense given, rounded up.
	 */
	private static int halfInProportion(int whole, int defense, BuildingType building) {
		return (int) Math.ceilDiv((long) half(whole) * defense, building.defense());
	}

	/**
	 * Half a Building's Gold or Turns, rounded up.
	 */
	private static int half(int whole) {
		return Math.ceilDiv(whole, 2);
	}

	/**
	 * For a defensive Building type, the Defense it has lost and is not yet
	 * being repaired or rebuilt for; for any other, how many of its Buildings
	 * are Damaged and not yet being repaired.
	 */
	private int unrepaired(BuildingType building) {
		List<Repair> repairing = repairs.stream().filter(r -> r.building().equals(building.name())).toList();
		if (!defensive(building)) {
			return damaged.getOrDefault(building.name(), 0) - repairing.size();
		}
		return defenseLost.getOrDefault(building.name(), 0) - repairing.stream().mapToInt(Repair::defense).sum();
	}

	/**
	 * Gives up one of a Building type's standing Buildings at once for Gold:
	 * an undamaged one if there is any, for half its Gold, or else a Damaged
	 * one, for a quarter, rounded down. A defensive one takes the Defense it
	 * raised, and any it lost, with it.
	 */
	ActionResult sell(BuildingType building) {
		Optional<String> unsellable = unsellable(building);
		if (unsellable.isPresent()) {
			return ActionResult.rejected(unsellable.get());
		}
		gold += sellValue(building);
		if (undamaged(building) == 0 && !defensive(building)) {
			damaged.computeIfPresent(building.name(), (b, count) -> count == 1 ? null : count - 1);
		}
		else if (undamaged(building) == 0) {
			int lost = defenseLost.get(building.name()) - defenseLostByDamaged(building);
			if (lost == 0) {
				defenseLost.remove(building.name());
			}
			else {
				defenseLost.put(building.name(), lost);
			}
		}
		buildings.computeIfPresent(building.name(), (b, count) -> count == 1 ? null : count - 1);
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Why the Nation may not Sell one of a Building type's Buildings now, if
	 * it may not.
	 */
	private Optional<String> unsellable(BuildingType building) {
		String name = building.name();
		if (!buildings.containsKey(name)) {
			return Optional.of(construction.stream().anyMatch(c -> c.building().equals(name))
					? "Your " + name + " is still under construction, and cannot be sold until it is complete."
					: "You have no " + name + " to sell.");
		}
		Optional<Repair> repair = repairs.stream().filter(r -> r.building().equals(name)).findFirst();
		if (repair.isPresent()) {
			return Optional.of("Your " + name + " is being " + (repair.get().rebuild() ? "rebuilt" : "repaired")
					+ ", and cannot be sold until that is done.");
		}
		if (undamaged(building) == 0 && defensive(building) && defenseLostByDamaged(building) == 0) {
			return Optional.of("Every " + name + " you have is destroyed, and a destroyed Building cannot be sold.");
		}
		return Optional.empty();
	}

	/**
	 * The Gold Selling one of a Building type's Buildings returns, which the
	 * Nation must be able to do.
	 */
	private int sellValue(BuildingType building) {
		return building.gold() / (undamaged(building) > 0 ? 2 : 4);
	}

	/**
	 * The Gold Selling one Building of each type would return now, for the
	 * types the Nation may Sell one of, in the order first completed.
	 */
	private Map<String, Integer> sellValues(Ruleset ruleset) {
		Map<String, Integer> values = new LinkedHashMap<>();
		for (String building : buildings.keySet()) {
			BuildingType type = ruleset.building(building).orElseThrow();
			if (unsellable(type).isEmpty()) {
				values.put(building, sellValue(type));
			}
		}
		return Collections.unmodifiableMap(values);
	}

	/**
	 * How many of a Building type's completed Buildings are neither Damaged
	 * nor destroyed.
	 */
	private int undamaged(BuildingType building) {
		int count = buildings.getOrDefault(building.name(), 0);
		if (!defensive(building)) {
			return count - damaged.getOrDefault(building.name(), 0);
		}
		int lost = defenseLost.getOrDefault(building.name(), 0);
		return count - Math.ceilDiv(lost, building.defense());
	}

	/**
	 * The Defense lost by a defensive Building type's one Damaged but standing
	 * Building, or 0 if none is.
	 */
	private int defenseLostByDamaged(BuildingType building) {
		return defenseLost.getOrDefault(building.name(), 0) % building.defense();
	}

	/**
	 * The Nation's undamaged non-defensive Buildings, one entry for each, by
	 * type in the order first completed.
	 */
	List<String> undamagedNonDefensive(Ruleset ruleset) {
		List<String> undamaged = new ArrayList<>();
		for (String building : buildings.keySet()) {
			BuildingType type = ruleset.building(building).orElseThrow();
			if (!defensive(type)) {
				undamaged.addAll(Collections.nCopies(undamaged(type), building));
			}
		}
		return undamaged;
	}

	/**
	 * Damages one of a non-defensive Building type's undamaged Buildings, which
	 * the Nation must have.
	 */
	void damage(String building) {
		damaged.merge(building, 1, Integer::sum);
		version++;
	}

	/**
	 * How many of each non-defensive Building type are Damaged, in the order
	 * first Damaged.
	 */
	Map<String, Integer> damagedBuildings() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(damaged));
	}

	ActionResult trainUnits(UnitType unit, int count, int turn) {
		long totalGold = (long) unit.gold() * count;
		long totalPopulation = (long) unit.population() * count;
		String units = unit.name() + " ×" + count;
		if (totalGold > gold) {
			return ActionResult.rejected(units + " costs " + totalGold + " Gold, but you have only " + gold + ".");
		}
		if (totalPopulation > population) {
			return ActionResult
				.rejected(units + " costs " + totalPopulation + " Population, but you have only " + population + ".");
		}
		gold -= (int) totalGold;
		population -= (int) totalPopulation;
		training.add(new Training(unit.name(), count, turn + unit.turns()));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Takes Units out of the Army and sets them travelling to another Nation.
	 *
	 * @param units how many of each Unit type to send
	 */
	ActionResult sendWarparty(String target, Map<String, Integer> units, Ruleset ruleset) {
		for (Map.Entry<String, Integer> chosen : units.entrySet()) {
			int owned = army.getOrDefault(chosen.getKey(), 0);
			if (owned == 0) {
				return ActionResult.rejected("Your Army has no " + chosen.getKey() + ".");
			}
			if (chosen.getValue() > owned) {
				return ActionResult.rejected("Your Army has only " + owned + " " + chosen.getKey() + ".");
			}
		}
		Map<String, Integer> sent = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> owned : army.entrySet()) {
			int count = units.getOrDefault(owned.getKey(), 0);
			if (count > 0) {
				sent.put(owned.getKey(), count);
			}
		}
		sent.forEach((unit, count) -> army.computeIfPresent(unit, (u, owned) -> owned == count ? null : owned - count));
		warparties.add(Warparty.sent(nextWarpartyId++, target, sent, ruleset));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Takes a Spy out of the Army and sets it travelling to another Nation.
	 */
	ActionResult sendSpy(String target, Ruleset ruleset) {
		Optional<String> spy = army.keySet().stream().filter(unit -> ruleset.unit(unit).orElseThrow().spy()).findFirst();
		if (spy.isEmpty()) {
			return ActionResult.rejected("Your Army has no Spy.");
		}
		army.computeIfPresent(spy.get(), (unit, owned) -> owned == 1 ? null : owned - 1);
		spies.add(Spy.sent(nextSpyId++, spy.get(), target));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Sets one of the Nation's Spies in place to a Mission, from scratch,
	 * giving up any other it was on.
	 *
	 * @param id the Spy's id
	 */
	ActionResult setMission(int id, Mission mission) {
		for (var it = spies.listIterator(); it.hasNext();) {
			Spy current = it.next();
			if (current.id() != id) {
				continue;
			}
			if (!current.inPlace()) {
				return ActionResult.rejected(current.homeward() ? "Your Spy is heading home."
						: "Your Spy has not yet arrived at " + current.target() + ".");
			}
			if (current.escaping()) {
				return ActionResult.rejected("Your Spy is Escaping from " + current.target() + ".");
			}
			it.set(current.onMission(mission));
			version++;
			return ActionResult.ACCEPTED;
		}
		return ActionResult.rejected("You have no such Spy.");
	}

	/**
	 * Moves every Spy in place on a Mission one Turn further into it; one whose
	 * Mission ends goes Idle.
	 *
	 * @return the Spies whose Mission ended, as they were before, in the order
	 *         sent
	 */
	List<Spy> workOnMissions() {
		List<Spy> ended = new ArrayList<>();
		for (var it = spies.listIterator(); it.hasNext();) {
			Spy current = it.next();
			if (!current.inPlace() || current.mission() == null || current.escaping()) {
				continue;
			}
			it.set(current.workedOnMission());
			if (current.missionTurnsLeft() == 1) {
				ended.add(current);
			}
			version++;
		}
		return ended;
	}

	/**
	 * Moves every Spy heading home one Turn on; one reaching home rejoins the
	 * Army and hands over any Haul it carries.
	 *
	 * @return those that came home
	 */
	List<Spy> spiesTravelHome() {
		List<Spy> returned = new ArrayList<>();
		for (var it = spies.listIterator(); it.hasNext();) {
			Spy current = it.next();
			if (!current.homeward()) {
				continue;
			}
			if (current.turnsLeft() > 1) {
				it.set(current.travelled());
			}
			else {
				it.remove();
				army.merge(current.unit(), 1, Integer::sum);
				if (current.haul() != null) {
					gain(current.haul());
				}
				returned.add(current);
			}
		}
		return returned;
	}

	/**
	 * Moves every Spy travelling to its target one Turn on; one reaching its
	 * target stays there.
	 *
	 * @return those that arrived at their target
	 */
	List<Spy> spiesTravelOut() {
		List<Spy> arrived = new ArrayList<>();
		for (var it = spies.listIterator(); it.hasNext();) {
			Spy current = it.next();
			if (current.homeward() || current.inPlace()) {
				continue;
			}
			it.set(current.travelled());
			if (current.turnsLeft() == 1) {
				arrived.add(current);
			}
		}
		return arrived;
	}

	/**
	 * Moves every Warparty heading home one Turn on; one reaching home rejoins
	 * the Army and hands over its plunder.
	 *
	 * @return those that came home, as they were when they got there
	 */
	List<Warparty> travelHome() {
		List<Warparty> returned = new ArrayList<>();
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (!current.homeward()) {
				continue;
			}
			if (current.turnsLeft() > 1) {
				it.set(current.travelled());
			}
			else {
				it.remove();
				current.units().forEach((unit, count) -> army.merge(unit, count, Integer::sum));
				gold += current.plunder().gold();
				research += current.plunder().research();
				returned.add(current);
			}
		}
		return returned;
	}

	/**
	 * Moves every Warparty travelling to its target one Turn on; one reaching
	 * its target waits there for the Battle.
	 *
	 * @return those that arrived at their target
	 */
	List<Warparty> travelOut() {
		List<Warparty> arrived = new ArrayList<>();
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (current.homeward() || current.awaitingBattle()) {
				continue;
			}
			it.set(current.travelled());
			if (current.turnsLeft() == 1) {
				arrived.add(current);
			}
		}
		return arrived;
	}

	/**
	 * What a Nation's conquerors seized from it, and the other Warparties
	 * heading to it and the Spies at it or heading to it that turned back home.
	 *
	 * @param turnedBack those Warparties as they were before, by the Nation
	 * that sent them
	 * @param spiesTurnedBack those Spies as they were before, by the Nation
	 * that sent them
	 * @param tasksEnded the Tasks about it that ended, by the Nation whose
	 * Scouts were on them
	 */
	record Conquest(Plunder seized, Map<String, List<Warparty>> turnedBack, Map<String, List<Spy>> spiesTurnedBack,
			Map<String, List<Task>> tasksEnded) {
	}

	/**
	 * The Warparties that have arrived at their target and await the Battle,
	 * in the order sent.
	 */
	List<Warparty> awaitingBattle() {
		return warparties.stream().filter(Warparty::awaitingBattle).toList();
	}

	/**
	 * Sends one of the Warparties awaiting the Battle home from it with its
	 * surviving Units and its plunder; one with no survivors disappears.
	 *
	 * @param warparty the Warparty, as {@link #awaitingBattle()} gave it
	 * @param haul what it carries home, whether plundered or seized in a
	 *        Conquest
	 */
	void foughtBattle(Warparty warparty, Map<String, Integer> survivors, Plunder plunder, Plunder haul,
			Ruleset ruleset) {
		for (var it = warparties.listIterator(); it.hasNext();) {
			if (it.next() == warparty) {
				if (survivors.isEmpty()) {
					it.remove();
				}
				else {
					it.set(warparty.headingHome(survivors, plunder, haul, ruleset));
				}
				return;
			}
		}
		throw new IllegalArgumentException("Not a Warparty of " + name());
	}

	/**
	 * Leaves one of the Warparties abroad with the Units that survived
	 * fighting in an Encounter, travelling on as before; one with no survivors
	 * disappears.
	 *
	 * @param warparty the Warparty, as {@link #warparty(int)} gave it
	 */
	void foughtEncounter(Warparty warparty, Map<String, Integer> survivors, Ruleset ruleset) {
		int at = warparties.indexOf(warparty);
		if (survivors.isEmpty()) {
			warparties.remove(at);
		}
		else {
			warparties.set(at, warparty.with(survivors, ruleset));
		}
		version++;
	}

	Map<String, Integer> army() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(army));
	}

	/**
	 * How many Scouts the Nation has roaming the wilds.
	 */
	int scouts() {
		return scouts.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Loses some of the Nation's Scouts, no more than it has, taking its Scout
	 * types in the reverse of the order first trained, and each Scout from
	 * among all of them, Idle or on Tasks, as {@link #fitTasksToScouts} picks.
	 *
	 * @return how many of each Scout type were lost
	 */
	Map<String, Integer> loseScouts(int count, RandomGenerator random) {
		Map<String, Integer> lost = new LinkedHashMap<>();
		int left = count;
		for (String type : List.copyOf(scouts.keySet()).reversed()) {
			if (left == 0) {
				break;
			}
			int losing = Math.min(left, scouts.get(type));
			scouts.computeIfPresent(type, (unit, owned) -> owned == losing ? null : owned - losing);
			lost.put(type, losing);
			left -= losing;
		}
		if (!lost.isEmpty()) {
			fitTasksToScouts(lost.values().stream().mapToInt(Integer::intValue).sum(), random);
			version++;
		}
		return lost;
	}

	/**
	 * How many of the Nation's Scouts are on no Task.
	 */
	int idleScouts() {
		return scouts() - tasks.stream().mapToInt(Task::scouts).sum();
	}

	/**
	 * The Tasks the Nation's Scouts are set to, in the order set.
	 */
	List<Task> tasks() {
		return List.copyOf(tasks);
	}

	/**
	 * Sets some of the Nation's Idle Scouts to a Task, which the Game has
	 * checked makes sense for its target.
	 */
	ActionResult setTask(Task.Kind kind, String target, int count) {
		if (count < 1) {
			return ActionResult.rejected("Set at least one Scout to the Task.");
		}
		int idle = idleScouts();
		if (count > idle) {
			return ActionResult.rejected("You have only " + idle + " Idle " + (idle == 1 ? "Scout" : "Scouts") + ".");
		}
		tasks.add(new Task(nextTaskId++, kind, target, count));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Ends one of the Nation's Tasks, its Scouts going Idle.
	 */
	ActionResult cancelTask(int id) {
		if (!tasks.removeIf(task -> task.id() == id)) {
			return ActionResult.rejected("You have no such Task.");
		}
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Ends the Nation's Tasks about a Nation that has left the Game, their
	 * Scouts going Idle.
	 *
	 * @return the Tasks ended, in the order set
	 */
	List<Task> endTasksFor(String target) {
		List<Task> ended = tasks.stream().filter(task -> target.equals(task.target())).toList();
		if (!ended.isEmpty()) {
			tasks.removeAll(ended);
			version++;
		}
		return ended;
	}

	/**
	 * Takes the given number of Scouts just lost from among all of the
	 * Nation's Scouts as they were, Idle or on Tasks, ending each Task left
	 * with none. While they are on more than one Task, or some are Idle and
	 * some on Tasks, each Scout lost draws once from the seeded random source,
	 * every Scout as likely as any other, taking the Idle Scouts first and then
	 * each Task's in the order set.
	 */
	private void fitTasksToScouts(int lost, RandomGenerator random) {
		int idle = idleScouts() + lost;
		for (int losing = 0; losing < lost; losing++) {
			if (tasks.isEmpty()) {
				return;
			}
			if (idle == 0 && tasks.size() == 1) {
				takeFromTask(0);
				continue;
			}
			int total = idle + tasks.stream().mapToInt(Task::scouts).sum();
			int pick = (int) (random.nextDouble() * total) - idle;
			if (pick < 0) {
				idle--;
				continue;
			}
			for (int at = 0; at < tasks.size(); at++) {
				if (pick < tasks.get(at).scouts()) {
					takeFromTask(at);
					break;
				}
				pick -= tasks.get(at).scouts();
			}
		}
	}

	/**
	 * Takes one Scout from a Task, ending it if left with none.
	 */
	private void takeFromTask(int at) {
		Task task = tasks.get(at);
		if (task.scouts() == 1) {
			tasks.remove(at);
		}
		else {
			tasks.set(at, task.withScouts(task.scouts() - 1));
		}
	}

	/**
	 * How many Tasks the Nation has set to Watch for Warparties.
	 */
	int watching() {
		return (int) tasks.stream().filter(task -> task.kind() == Task.Kind.WATCH).count();
	}

	/**
	 * How many of the Nation's Scouts are set to Hunt Travelling Spies.
	 */
	int hunting() {
		return tasks.stream().filter(task -> task.kind() == Task.Kind.HUNT).mapToInt(Task::scouts).sum();
	}

	/**
	 * Whether the Nation has Located another.
	 */
	boolean hasLocated(String nation) {
		return located.contains(nation);
	}

	/**
	 * Whether the Nation has set Scouts to Locate another.
	 */
	boolean isLocating(String nation) {
		return tasks.stream().anyMatch(task -> task.kind() == Task.Kind.LOCATE && task.target().equals(nation));
	}

	/**
	 * Locates another Nation, for good, ending any Task Locating it, its
	 * Scouts going Idle.
	 *
	 * @return whether it was not Located before
	 */
	boolean locate(String nation) {
		boolean newlyLocated = located.add(nation);
		tasks.removeIf(task -> task.kind() == Task.Kind.LOCATE && task.target().equals(nation));
		if (newlyLocated) {
			version++;
		}
		return newlyLocated;
	}

	/**
	 * How many Spies the Nation has at home.
	 */
	int spiesAtHome(Ruleset ruleset) {
		return army.entrySet()
			.stream()
			.filter(owned -> ruleset.unit(owned.getKey()).orElseThrow().spy())
			.mapToInt(Map.Entry::getValue)
			.sum();
	}

	/**
	 * Removes Units the Army lost.
	 */
	void lose(Map<String, Integer> units) {
		units.forEach((unit, count) -> army.computeIfPresent(unit, (u, owned) -> owned <= count ? null : owned - count));
	}

	/**
	 * The Nation's Defense: what its defensive Buildings raise it by, less the
	 * Defense they have lost.
	 */
	int defense(Ruleset ruleset) {
		int raised = buildings.entrySet()
			.stream()
			.mapToInt(owned -> ruleset.building(owned.getKey()).orElseThrow().defense() * owned.getValue())
			.sum();
		return raised - defenseLost.values().stream().mapToInt(Integer::intValue).sum();
	}

	private List<BuildingDamage> damage(Ruleset ruleset) {
		List<BuildingDamage> damage = new ArrayList<>();
		for (String building : buildings.keySet()) {
			BuildingType type = ruleset.building(building).orElseThrow();
			int unrepaired = unrepaired(type);
			if (unrepaired > 0 && !defensive(type)) {
				damage.add(BuildingDamage.nonDefensive(building, unrepaired, half(type.gold()), half(type.turns())));
			}
			else if (unrepaired > 0) {
				int damaged = unrepaired % type.defense();
				damage.add(BuildingDamage.defensive(building, unrepaired / type.defense(), damaged,
						halfInProportion(type.gold(), damaged, type), halfInProportion(type.turns(), damaged, type),
						half(type.gold()), half(type.turns())));
			}
		}
		return damage;
	}

	/**
	 * How far a Warparty wore a Nation's Defense down.
	 *
	 * @param before the Nation's Defense before
	 * @param after the Nation's Defense after
	 * @param destroyed how many of each defensive Building type it destroyed
	 * @param damaged the defensive Building types it left with a damaged
	 * Building
	 */
	record DefenseWorn(int before, int after, Map<String, Integer> destroyed, List<String> damaged) {

		int worn() {
			return before - after;
		}

	}

	/**
	 * Wears the Nation's Defense down by up to the given amount, from its
	 * defensive Buildings by type in the order first completed.
	 */
	DefenseWorn wearDefense(int amount, Ruleset ruleset) {
		int before = defense(ruleset);
		int left = amount;
		Map<String, Integer> destroyed = new LinkedHashMap<>();
		List<String> damaged = new ArrayList<>();
		for (Map.Entry<String, Integer> owned : buildings.entrySet()) {
			String building = owned.getKey();
			int defense = ruleset.building(building).orElseThrow().defense();
			int lost = defenseLost.getOrDefault(building, 0);
			int worn = Math.min(left, defense * owned.getValue() - lost);
			if (worn == 0) {
				continue;
			}
			left -= worn;
			defenseLost.put(building, lost + worn);
			int newlyDestroyed = (lost + worn) / defense - lost / defense;
			if (newlyDestroyed > 0) {
				destroyed.put(building, newlyDestroyed);
			}
			if ((lost + worn) % defense != 0) {
				damaged.add(building);
			}
		}
		return new DefenseWorn(before, before - (amount - left), destroyed, damaged);
	}

	/**
	 * Whether the Nation is Breached: it has a destroyed defensive Building,
	 * or none at all.
	 */
	boolean breached(Ruleset ruleset) {
		boolean anyDefensive = false;
		for (String building : buildings.keySet()) {
			BuildingType type = ruleset.building(building).orElseThrow();
			if (defensive(type)) {
				anyDefensive = true;
				if (defenseLost.getOrDefault(building, 0) >= type.defense()) {
					return true;
				}
			}
		}
		return !anyDefensive;
	}

	/**
	 * Loses Population to a Warparty, as much as the Nation has.
	 *
	 * @return the Population lost
	 */
	int losePopulation(int amount) {
		int lost = Math.min(amount, population);
		population -= lost;
		return lost;
	}

	/**
	 * Gives up Gold and Research to plunderers, as much as the Nation has.
	 *
	 * @return the Gold and Research taken
	 */
	Plunder plundered(int gold, int research) {
		int goldTaken = Math.min(gold, this.gold);
		int researchTaken = Math.min(research, this.research);
		this.gold -= goldTaken;
		this.research -= researchTaken;
		return new Plunder(goldTaken, researchTaken);
	}

	/**
	 * The Units that finished training as a Turn resolved, by type in the
	 * order started.
	 *
	 * @param joined those that joined the Army
	 * @param setOut the Scouts that set out into the wilds
	 */
	record Trained(Map<String, Integer> joined, Map<String, Integer> setOut) {
	}

	/**
	 * The Buildings, repairs and Units that finished as a Turn resolved.
	 *
	 * @param built the Buildings completed, in the order started
	 * @param repaired the repairs finished, in the order started
	 * @param trained the Units that finished training
	 */
	record Progress(List<String> built, List<Repair> repaired, Trained trained) {
	}

	/**
	 * Moves construction, repairs and training one Turn on, in that order.
	 */
	Progress progressWork(int nextTurn, Ruleset ruleset) {
		List<String> built = progressConstruction();
		List<Repair> repaired = progressRepairs();
		return new Progress(built, repaired, progressTraining(nextTurn, ruleset));
	}

	/**
	 * Moves the Units ready by the start of a Turn out of training: Scouts set
	 * out into the wilds, and every other Unit joins the Army.
	 */
	private Trained progressTraining(int nextTurn, Ruleset ruleset) {
		Map<String, Integer> joined = new LinkedHashMap<>();
		Map<String, Integer> setOut = new LinkedHashMap<>();
		for (var it = training.iterator(); it.hasNext();) {
			Training current = it.next();
			if (current.readyOnTurn() == nextTurn) {
				it.remove();
				boolean scout = ruleset.unit(current.unit()).orElseThrow().scout();
				(scout ? scouts : army).merge(current.unit(), current.count(), Integer::sum);
				(scout ? setOut : joined).merge(current.unit(), current.count(), Integer::sum);
			}
		}
		return new Trained(joined, setOut);
	}

	/**
	 * The Gold the Nation's Units cost each Turn, at home and abroad.
	 */
	int upkeep(Ruleset ruleset) {
		return upkeepOf(army, ruleset) + upkeepOf(scouts, ruleset) + warparties.stream().mapToInt(w -> upkeepOf(w.units(), ruleset)).sum()
				+ spies.stream().mapToInt(spy -> ruleset.unit(spy.unit()).orElseThrow().upkeep()).sum();
	}

	private static int upkeepOf(Map<String, Integer> units, Ruleset ruleset) {
		return units.entrySet()
			.stream()
			.mapToInt(owned -> ruleset.unit(owned.getKey()).orElseThrow().upkeep() * owned.getValue())
			.sum();
	}

	/**
	 * Units that deserted as a Turn resolved, from the Scouts, from the Army,
	 * from one Warparty, or a Spy abroad.
	 *
	 * @param scouts whether they were Scouts roaming the wilds
	 * @param warparty the Warparty they left, as it was before, or null if they
	 * did not leave a Warparty
	 * @param spy the Spy abroad that deserted, or null if they were not one
	 * @param units how many of each Unit type deserted, in the order they
	 * deserted
	 */
	record Desertion(boolean scouts, Warparty warparty, Spy spy, Map<String, Integer> units) {
	}

	/**
	 * Lets Units desert, one at a time, until the Nation's Gold covers its
	 * Units' upkeep. They desert from the Scouts, the Army, Warparties abroad
	 * and Spies abroad alike. The Unit costing the most upkeep deserts first;
	 * between equally costly ones, the one listed last, taking the Scouts'
	 * types and then the Army's in the order first trained, then each
	 * Warparty's in the order sent, then each Spy in the order sent. A
	 * Warparty left with no Units disappears. Scouts that desert are taken
	 * from among all Scouts as {@link #loseScouts} takes them.
	 *
	 * @return the desertions from the Scouts first, then from the Army, then
	 * from each Warparty in the order sent, then of each Spy in the order sent
	 */
	List<Desertion> desertUntilUpkeepIsAffordable(Ruleset ruleset, RandomGenerator random) {
		List<Map<String, Integer>> groups = new ArrayList<>();
		groups.add(scouts);
		groups.add(army);
		warparties.forEach(w -> groups.add(new LinkedHashMap<>(w.units())));
		spies.forEach(spy -> groups.add(new LinkedHashMap<>(Map.of(spy.unit(), 1))));
		List<Map<String, Integer>> deserted = new ArrayList<>();
		groups.forEach(g -> deserted.add(new LinkedHashMap<>()));
		while (groups.stream().mapToInt(g -> upkeepOf(g, ruleset)).sum() > gold) {
			int group = -1;
			String deserter = null;
			int highest = -1;
			for (int i = 0; i < groups.size(); i++) {
				for (String unit : groups.get(i).keySet()) {
					int upkeep = ruleset.unit(unit).orElseThrow().upkeep();
					if (upkeep >= highest) {
						group = i;
						deserter = unit;
						highest = upkeep;
					}
				}
			}
			groups.get(group).computeIfPresent(deserter, (unit, count) -> count == 1 ? null : count - 1);
			deserted.get(group).merge(deserter, 1, Integer::sum);
		}
		List<Desertion> desertions = new ArrayList<>();
		if (!deserted.get(0).isEmpty()) {
			desertions.add(new Desertion(true, null, null, deserted.get(0)));
			fitTasksToScouts(deserted.get(0).values().stream().mapToInt(Integer::intValue).sum(), random);
		}
		if (!deserted.get(1).isEmpty()) {
			desertions.add(new Desertion(false, null, null, deserted.get(1)));
		}
		List<Warparty> before = List.copyOf(warparties);
		warparties.clear();
		for (int i = 0; i < before.size(); i++) {
			Warparty warparty = before.get(i);
			if (!deserted.get(i + 2).isEmpty()) {
				desertions.add(new Desertion(false, warparty, null, deserted.get(i + 2)));
			}
			if (!groups.get(i + 2).isEmpty()) {
				warparties.add(warparty.with(groups.get(i + 2), ruleset));
			}
		}
		List<Spy> spiesBefore = List.copyOf(spies);
		spies.clear();
		for (int i = 0; i < spiesBefore.size(); i++) {
			Spy spy = spiesBefore.get(i);
			int group = before.size() + 2 + i;
			if (groups.get(group).isEmpty()) {
				desertions.add(new Desertion(false, null, spy, deserted.get(group)));
			}
			else {
				spies.add(spy);
			}
		}
		return desertions;
	}

	/**
	 * Pays its Units' upkeep, which the Nation must be able to afford.
	 *
	 * @return the Gold paid
	 */
	int payUpkeep(Ruleset ruleset) {
		int upkeep = upkeep(ruleset);
		gold -= upkeep;
		return upkeep;
	}

	/**
	 * How many of each completed Building add their effects: those neither
	 * destroyed nor, for a non-defensive one, Damaged.
	 */
	private Map<String, Integer> producing(Ruleset ruleset) {
		Map<String, Integer> producing = new LinkedHashMap<>(buildings);
		defenseLost.forEach((building, lost) -> producing.computeIfPresent(building,
				(b, count) -> count - lost / ruleset.building(b).orElseThrow().defense()));
		damaged.forEach((building, count) -> producing.computeIfPresent(building, (b, owned) -> owned - count));
		return producing;
	}

	/**
	 * Population the Nation's producing Buildings add this Turn.
	 */
	int populationFromBuildings(Ruleset ruleset) {
		return fromProducing(ruleset, BuildingType::populationPerTurn);
	}

	/**
	 * Gold the Nation earns this Turn from its current Population: one for
	 * every one of its Population.
	 */
	int goldFromPopulation() {
		return population;
	}

	/**
	 * Gold the Nation's producing Buildings add this Turn: their flat Gold plus
	 * their Gold for its current Population, rounded down.
	 */
	int goldFromBuildings(Ruleset ruleset) {
		int flat = fromProducing(ruleset, BuildingType::goldPerTurn);
		BigDecimal goldPerPopulation = producing(ruleset).entrySet()
			.stream()
			.map(owned -> BigDecimal.valueOf(ruleset.building(owned.getKey()).orElseThrow().goldPerPopulation())
				.multiply(BigDecimal.valueOf(owned.getValue())))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return flat + goldPerPopulation.multiply(BigDecimal.valueOf(population))
			.setScale(0, RoundingMode.FLOOR)
			.intValueExact();
	}

	/**
	 * Research the Nation's producing Buildings add this Turn.
	 */
	int researchFromBuildings(Ruleset ruleset) {
		return fromProducing(ruleset, BuildingType::researchPerTurn);
	}

	/**
	 * Sums one per-Turn effect over the Nation's producing Buildings.
	 */
	private int fromProducing(Ruleset ruleset, ToIntFunction<BuildingType> effect) {
		return producing(ruleset).entrySet()
			.stream()
			.mapToInt(owned -> effect.applyAsInt(ruleset.building(owned.getKey()).orElseThrow()) * owned.getValue())
			.sum();
	}

	/**
	 * Moves every Building under construction one Turn on.
	 *
	 * @return the Buildings completed, in the order started
	 */
	private List<String> progressConstruction() {
		List<String> completed = new ArrayList<>();
		for (var it = construction.listIterator(); it.hasNext();) {
			Construction current = it.next();
			if (current.turnsLeft() == 1) {
				it.remove();
				buildings.merge(current.building(), 1, Integer::sum);
				completed.add(current.building());
			}
			else {
				it.set(new Construction(current.building(), current.turnsLeft() - 1));
			}
		}
		return completed;
	}

	/**
	 * Moves every repair one Turn on. A finished one restores the Defense it
	 * was paid for, or as much as its Building type has lost by then, or, for
	 * a non-defensive Building, makes one of its Damaged ones undamaged.
	 *
	 * @return the repairs finished, in the order started
	 */
	private List<Repair> progressRepairs() {
		List<Repair> finished = new ArrayList<>();
		for (var it = repairs.listIterator(); it.hasNext();) {
			Repair current = it.next();
			if (current.turnsLeft() == 1) {
				it.remove();
				if (current.defense() == 0) {
					damaged.computeIfPresent(current.building(), (building, count) -> count == 1 ? null : count - 1);
				}
				else {
					defenseLost.computeIfPresent(current.building(),
							(building, lost) -> lost <= current.defense() ? null : lost - current.defense());
				}
				finished.add(current);
			}
			else {
				it.set(new Repair(current.building(), current.rebuild(), current.defense(), current.turnsLeft() - 1));
			}
		}
		return finished;
	}

}
