package com.miniciv.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * A Barbarian Camp's state inside the engine: a hostile band no Player leads,
 * not a Nation (see ADR 0002). It has only an Army, which grows by itself, and
 * the barbarian defensive Buildings it appeared with, and sends Warparties at
 * its Targets.
 */
class Camp implements Sender {

	/**
	 * The colour every Camp shows in, which no Player can pick.
	 */
	static final String COLOUR = "#8b1e1e";

	/**
	 * The emblem every Camp shows, which no Player can pick.
	 */
	static final String EMBLEM = "skull";

	/**
	 * What every Camp's name ends with, after its place.
	 */
	static final String NAMED = " Barbarian Camp";

	/**
	 * The fewest Units a Camp appears with.
	 */
	static final int FEWEST_UNITS = 3;

	/**
	 * The most Units a Camp appears with.
	 */
	static final int MOST_UNITS = 5;

	/**
	 * The most barbarian Buildings a Camp appears with.
	 */
	static final int MOST_BUILDINGS = 2;

	/**
	 * The most Units a Camp has, at home and abroad, beyond which its Army no
	 * longer grows.
	 */
	static final int MOST_UNITS_GROWN = 12;

	/**
	 * The chance, each Turn, of a Camp's Army growing by one Unit, on the
	 * first Turn.
	 */
	static final double GROWTH_CHANCE = 0.25;

	/**
	 * How much the chance of a Camp's Army growing rises each Turn of the
	 * Game.
	 */
	static final double GROWTH_CHANCE_PER_TURN = 0.01;

	/**
	 * How many Units a Camp's Army must have at home before it sends half of
	 * them in a Warparty.
	 */
	static final int RAID_SIZE = 6;

	private final NationIdentity identity;

	/**
	 * How many of each Unit type it has at home, in the order first gained.
	 */
	private final Map<String, Integer> army = new LinkedHashMap<>();

	/**
	 * How many of each barbarian Building it has, in the order first listed.
	 */
	private final Map<String, Integer> buildings = new LinkedHashMap<>();

	/**
	 * How much Defense each barbarian Building type has lost.
	 */
	private final Map<String, Integer> defenseLost = new LinkedHashMap<>();

	/**
	 * The Nations it may send Warparties at, in the order they became Targets.
	 */
	private final Set<String> targets = new LinkedHashSet<>();

	/**
	 * For each Target it lost a Warparty at, that Warparty's Strength when
	 * sent, which its Army must outgrow before it attacks there again.
	 */
	private final Map<String, Double> lostAt = new LinkedHashMap<>();

	/**
	 * Its Warparties abroad, in the order sent.
	 */
	private final List<Warparty> warparties = new ArrayList<>();

	private int nextWarpartyId = 1;

	private boolean destroyed;

	/**
	 * @param place what the Camp is named for, such as "Mountain Passage"
	 * @param ruler the name of its Ruler
	 * @param targets the Nations it may send Warparties at
	 * @param army how many of each Unit type it has at home
	 * @param buildings how many of each barbarian Building it has
	 */
	Camp(String place, String ruler, List<String> targets, Map<String, Integer> army,
			Map<String, Integer> buildings) {
		this.identity = new NationIdentity(place + NAMED, ruler, COLOUR, EMBLEM);
		this.targets.addAll(targets);
		this.army.putAll(army);
		this.buildings.putAll(buildings);
	}

	/**
	 * Whether Camps have Units of the given type: those with Strength that are
	 * neither Scouts nor Spies.
	 */
	static boolean fights(UnitType unit) {
		return unit.strength() > 0 && !unit.scout() && !unit.spy();
	}

	/**
	 * The Unit types Camps have, in the ruleset's order.
	 */
	static List<UnitType> fighting(Ruleset ruleset) {
		return ruleset.units().stream().filter(Camp::fights).toList();
	}

	@Override
	public String name() {
		return identity.name();
	}

	@Override
	public String ruled() {
		return identity.ruled();
	}

	@Override
	public boolean camp() {
		return true;
	}

	NationIdentity identity() {
		return identity;
	}

	boolean destroyed() {
		return destroyed;
	}

	/**
	 * Destroys the Camp: it no longer grows, sends Warparties or can be
	 * attacked; its Warparties abroad finish their trip, then disband.
	 */
	void destroy() {
		destroyed = true;
		army.clear();
	}

	/**
	 * Whether the Camp is destroyed and none of its Warparties is still abroad.
	 */
	boolean gone() {
		return destroyed && warparties.isEmpty();
	}

	Map<String, Integer> army() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(army));
	}

	/**
	 * How many of each barbarian Building it has, standing or destroyed.
	 */
	Map<String, Integer> buildings() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(buildings));
	}

	/**
	 * The Nations it may send Warparties at, in the order they became Targets.
	 */
	List<String> targets() {
		return List.copyOf(targets);
	}

	/**
	 * Makes a Nation one of its Targets, if it is not already.
	 */
	void target(String nation) {
		targets.add(nation);
	}

	/**
	 * Forgets a Nation that has left the Game: it is no longer a Target, and
	 * every Warparty heading to it turns back home.
	 */
	void forget(String nation) {
		targets.remove(nation);
		lostAt.remove(nation);
		turnBackFrom(nation);
	}

	/**
	 * Turns every Warparty heading to a Nation, and not yet having fought
	 * there, back home.
	 */
	void turnBackFrom(String target) {
		warparties.replaceAll(current -> !current.homeward() && current.target().equals(target) ? current.turnedBack()
				: current);
	}

	/**
	 * The Camp's Defense: what its barbarian Buildings raise it by, less what
	 * they have lost.
	 */
	int defense(Barbarians barbarians) {
		int raised = buildings.entrySet()
			.stream()
			.mapToInt(owned -> barbarians.building(owned.getKey()).defense() * owned.getValue())
			.sum();
		return raised - defenseLost.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Wears the Camp's Defense down by up to the given amount, from its
	 * barbarian Buildings by type in the order listed, as a Nation's is.
	 */
	Nation.DefenseWorn wearDefense(int amount, Barbarians barbarians) {
		int before = defense(barbarians);
		int left = amount;
		Map<String, Integer> destroyedBuildings = new LinkedHashMap<>();
		List<String> damaged = new ArrayList<>();
		for (Map.Entry<String, Integer> owned : buildings.entrySet()) {
			String building = owned.getKey();
			int defense = barbarians.building(building).defense();
			int lost = defenseLost.getOrDefault(building, 0);
			int worn = Math.min(left, defense * owned.getValue() - lost);
			if (worn == 0) {
				continue;
			}
			left -= worn;
			defenseLost.put(building, lost + worn);
			int newlyDestroyed = (lost + worn) / defense - lost / defense;
			if (newlyDestroyed > 0) {
				destroyedBuildings.put(building, newlyDestroyed);
			}
			if ((lost + worn) % defense != 0) {
				damaged.add(building);
			}
		}
		return new Nation.DefenseWorn(before, before - (amount - left), destroyedBuildings, damaged);
	}

	/**
	 * Removes Units its Army lost.
	 */
	void lose(Map<String, Integer> units) {
		units.forEach((unit, count) -> army.computeIfPresent(unit, (u, owned) -> owned <= count ? null : owned - count));
	}

	/**
	 * How many Units the Camp has, at home and abroad.
	 */
	private int unitCount() {
		return count(army) + warparties.stream().mapToInt(Warparty::unitCount).sum();
	}

	private static int count(Map<String, Integer> units) {
		return units.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Has the Camp's Army grow by one Unit, by chance, unless it has the most
	 * Units it can: one draw from the random source for whether it grows,
	 * {@value #GROWTH_CHANCE} likely plus {@value #GROWTH_CHANCE_PER_TURN} more
	 * for each Turn of the Game, and one more for which fighting Unit type
	 * joins, each as likely as the others.
	 *
	 * @return the Unit type that joined, if one did
	 */
	Optional<String> grow(int turn, Ruleset ruleset, RandomGenerator random) {
		if (destroyed || unitCount() >= MOST_UNITS_GROWN) {
			return Optional.empty();
		}
		if (random.nextDouble() >= Math.min(1, GROWTH_CHANCE + GROWTH_CHANCE_PER_TURN * turn)) {
			return Optional.empty();
		}
		List<UnitType> fighting = fighting(ruleset);
		String unit = fighting.get((int) (random.nextDouble() * fighting.size())).name();
		army.merge(unit, 1, Integer::sum);
		return Optional.of(unit);
	}

	/**
	 * Sends a Warparty of half of the Army's Units, rounded down, at one of
	 * its Targets still in the Game, once its Army has at least
	 * {@value #RAID_SIZE} Units at home: one draw from the random source picks
	 * the Target, each as likely as the others. A Target it lost a Warparty at
	 * is passed over until its Army's Strength is greater than that
	 * Warparty's was when sent. It takes half of each Unit type, rounded
	 * down, then one more of each type left with an odd number, in the order
	 * first gained, until it has half; the Army at home is never emptied.
	 *
	 * @param inGame the Nations still in the Game
	 * @return the Warparty sent, if one was
	 */
	Optional<Warparty> raid(List<String> inGame, Ruleset ruleset, RandomGenerator random) {
		if (destroyed || count(army) < RAID_SIZE) {
			return Optional.empty();
		}
		double strength = ruleset.strengthOf(army);
		lostAt.values().removeIf(lost -> strength > lost);
		List<String> open = targets.stream()
			.filter(inGame::contains)
			.filter(target -> !lostAt.containsKey(target))
			.toList();
		if (open.isEmpty()) {
			return Optional.empty();
		}
		String target = open.get((int) (random.nextDouble() * open.size()));
		int sending = count(army) / 2;
		Map<String, Integer> sent = new LinkedHashMap<>();
		army.forEach((unit, owned) -> sent.put(unit, owned / 2));
		for (Map.Entry<String, Integer> owned : army.entrySet()) {
			if (count(sent) < sending && owned.getValue() % 2 == 1) {
				sent.merge(owned.getKey(), 1, Integer::sum);
			}
		}
		sent.values().removeIf(count -> count == 0);
		sent.forEach((unit, count) -> army.computeIfPresent(unit, (u, owned) -> owned == count ? null : owned - count));
		Warparty warparty = Warparty.sent(nextWarpartyId++, target, sent, ruleset);
		warparties.add(warparty);
		return Optional.of(warparty);
	}

	@Override
	public List<Warparty> warparties() {
		return List.copyOf(warparties);
	}

	@Override
	public Optional<Warparty> warparty(int id) {
		return warparties.stream().filter(warparty -> warparty.id() == id).findFirst();
	}

	/**
	 * Sends a Warparty home from its Battle, as a Nation's is. One wiped out
	 * means the Camp will not attack that Target again until its Army
	 * outgrows it.
	 */
	@Override
	public void foughtBattle(Warparty warparty, Map<String, Integer> survivors, Plunder plunder, Plunder haul,
			Ruleset ruleset) {
		int at = warparties.indexOf(warparty);
		if (survivors.isEmpty()) {
			warparties.remove(at);
			lostAt.merge(warparty.target(), warparty.strengthSent(), Math::max);
		}
		else {
			warparties.set(at, warparty.headingHome(survivors, plunder, haul, ruleset));
		}
	}

	@Override
	public void foughtEncounter(Warparty warparty, Map<String, Integer> survivors, Ruleset ruleset) {
		int at = warparties.indexOf(warparty);
		if (survivors.isEmpty()) {
			warparties.remove(at);
		}
		else {
			warparties.set(at, warparty.with(survivors, ruleset));
		}
	}

	/**
	 * Moves every Warparty heading home one Turn on. One reaching the Camp
	 * rejoins its Army, and what it carries is gone for good; if the Camp was
	 * destroyed, it disbands instead.
	 */
	void travelHome() {
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (!current.homeward()) {
				continue;
			}
			if (current.turnsLeft() > 1) {
				it.set(current.travelled());
				continue;
			}
			it.remove();
			if (!destroyed) {
				current.units().forEach((unit, count) -> army.merge(unit, count, Integer::sum));
			}
		}
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
	 * What a Nation learns of the Camp by Locating it, as it is now.
	 */
	CampFacts facts(Barbarians barbarians, int turn) {
		return new CampFacts(army, buildings, defense(barbarians), targets(), turn);
	}

}
