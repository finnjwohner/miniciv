# miniciv

A turn-based, player-vs-player strategy game in the browser where each player leads a nation, spending resources and research to outbuild and conquer their opponents.

## Game structure

**Game**:
One match of miniciv for 2–4 Players, from start until one Nation is left standing.
_Avoid_: Match, session, round

**Room**:
The place Players gather before a Game starts, joined by a room code or invite link; the Game starts once 2–4 Players are all ready, and no one can join after that.
_Avoid_: Lobby, server

**Player**:
A person taking part in a Game. A Player who stays disconnected for more than five minutes in total over a Game has their Nation Removed.
_Avoid_: User, account

**Paused**:
The state of a Game while any Player whose Nation is still in it is disconnected: the Turn timer stops and no Player can act until they reconnect or are Removed.
_Avoid_: Frozen, suspended, on hold

**Removed**:
A Nation taken out of the Game because its Player was disconnected for more than five minutes in total; unlike Conquest, nobody seizes anything from it, and Warparties heading to it turn back home.
_Avoid_: Kicked, dropped, forfeited

**Turn**:
One step of the Game, at most two minutes long not counting time Paused, in which all Players make their decisions at the same time, after which everything resolves together.
_Avoid_: Round, tick

**Turn Report**:
The short text description each Player receives at the start of a Turn of what happened as the previous Turn resolved, headed by how its Nation's Gold, Population and Research moved and grouped into sections by kind (alerts, warfare, completions, Events, Spies and Scouts, resources).
_Avoid_: Log, summary, news

**Message**:
Private text one Player sends to one other Player during a Game, seen only by the two of them and kept until the Game's Room is gone; Players knocked out can still send and receive them, Players Removed cannot.
_Avoid_: Chat, DM, whisper

**Map**:
A read-only graphic of every Nation's state; Players do not act through it.
_Avoid_: Board

## Nations

**Nation**:
The country a Player leads in a Game; what owns Gold, Population, Research, Buildings and Units. Every Nation starts the same; other Players initially know only its name, Ruler, colour and emblem, each unique among the Nations in the Game.
_Avoid_: Country, civ, empire, faction

**Ruler**:
The in-world leader of a Nation, named by its Player in the Room; Turn Reports name the Ruler alongside the Nation for what one Nation directly does to another, such as its Warparty arriving.
_Avoid_: Leader, king, player

**Gold**:
A resource a Nation gains each Turn, one for every one of its Population plus what Buildings such as markets and banks add, and spends on Buildings and Units.
_Avoid_: Money, coins

**Population**:
A resource representing a Nation's people; it grows each Turn by a flat base amount plus what Buildings such as farms add, and is spent to train Units.
_Avoid_: Citizens, pop, manpower

**Research**:
A resource a Nation accumulates each Turn, a flat base amount plus what Buildings such as libraries add, and spends to unlock Technologies.
_Avoid_: Science, tech points

**Technology**:
An unlockable entry in the Tech Tree that gives access to new Buildings or Units.
_Avoid_: Tech, upgrade, discovery

**Tech Tree**:
The small branching set of Technologies, where some Technologies must be unlocked before others become available. A Nation sees the Technologies it has unlocked or can unlock now, and those whose prerequisites are all among them; every other Technology shows only as unknown.
_Avoid_: Research tree, skill tree

**Building**:
Something a Nation constructs over one or more Turns by paying Gold upfront.
_Avoid_: Structure, improvement

**Defense**:
A Nation's protection against attack, raised by defensive Buildings, which must be worn down to 0 before an attack can reduce Population.
_Avoid_: Walls, fortification, armour

**Damaged**:
A defensive Building that has lost some of the Defense it raises but still stands; it raises only what it has left.
_Avoid_: Hurt, worn, weakened

**Destroyed**:
A defensive Building that has lost all of its Defense; it raises no Defense and adds none of its other effects until Rebuilt.
_Avoid_: Ruined, razed, lost

**Repair**:
Restoring the Defense one Damaged Building lost, paying Gold upfront and taking some Turns, both half of what constructing it would, in proportion to the Defense lost.
_Avoid_: Fix, mend

**Rebuild**:
Restoring one Destroyed Building, paying Gold upfront and taking some Turns, both half of what constructing it would, rounded up.
_Avoid_: Reconstruct, restore

**Sell**:
Giving up one standing Building at once in return for Gold: half its cost, or a quarter if it is Damaged. A Destroyed Building cannot be Sold.
_Avoid_: Demolish, scrap, refund

## Warfare

**Unit**:
A single soldier of a given type (e.g. archer, horseman, warrior, scout) that a Nation trains over one or more Turns by paying Gold and Population upfront, and then pays a small Gold upkeep for each Turn.
_Avoid_: Troop

**Strength**:
How much a Unit contributes in a Battle, multiplied when it fights a Unit type it counters.
_Avoid_: Power, attack, damage

**Army**:
The Units a Nation has at home in its own land; Units away in a Warparty, Spies abroad and Scouts are not part of it.
_Avoid_: Garrison, defenders

**Warparty**:
A group of Units a Player sends from their Army toward another Nation; it travels for some Turns before arriving, more the greater its Strength, and on the way home more still the more Plunder it carries.
_Avoid_: Attack, expedition, army

**Scout**:
A Unit that roams the wilds from the moment it is trained, giving its Nation a chance each Turn to discover Warparties abroad and to catch other Nations' Spies still travelling, better the more Scouts it has. Scouts are never at home or part of the Army, have no Strength and never fight, and no Warparty or Scout can meet or catch one; only Events can lose them.
_Avoid_: Spy, lookout

**Spy**:
A Unit that travels to another Nation and stays there until caught, now and then reporting a snapshot of its hidden state (Gold, Population, Research, Army or Buildings). Spies kept at home run Counter-intelligence, and Scouts and travelling Warparties can catch Spies still travelling. A caught Spy dies, stays silent or confesses, revealing its sender and a snapshot of the sender's Nation; the sender is told only if another of its Spies in the catching Nation reports it.
_Avoid_: Scout, agent

**Counter-intelligence**:
What a Nation's Spies kept at home do: give it a chance each Turn to catch other Nations' Spies in its land.
_Avoid_: Spy-catching, security

**Fact**:
One piece of another Nation's hidden state a Spy reported or a caught Spy confessed, shown on the Map against that Nation with the Turn it was learned; a later report of the same kind replaces it.
_Avoid_: Intel, knowledge, info

**Recall**:
Ordering a Warparty to turn back home; the trip home takes as long as it has travelled so far, less in proportion if it has since lost Strength.
_Avoid_: Retreat, withdraw

**Tribute**:
Gold or Population a defending Nation offers an attacker, mid-Turn, in return for Recalling its Warparty; if accepted, no Battle takes place. An offer, and the answer to it, cannot be taken back.
_Avoid_: Bribe, ransom, payment

**Encounter**:
Two Warparties from different Nations meeting by chance while abroad, each seeing the other's Units; if either Player chooses to fight, they fight each other. Each Player may change their choice until the Turn ends.
_Avoid_: Skirmish, ambush

**Battle**:
The fight that happens when Warparties arrive at a Nation; Warparties arriving on the same Turn fight as one. Its outcome is reported the following Turn, and surviving attackers head home.
_Avoid_: Fight, combat, clash

**Plunder**:
The Gold and Research a Warparty's surviving Units take from the Nation they fought, scaled by their remaining Strength and carried home to their own Nation. Warparties fighting a Battle as one split it in proportion to their remaining Strength.
_Avoid_: Loot, spoils, booty

**Conquest**:
Taking a Nation by bringing both its Population and its Army to 0 in a Battle that some of the attacking Units survive; a Nation is only conquered when attacked, and a Warparty wiped out in the Battle conquers nothing. The conquering Nation seizes a large share of its Gold and Research, split like Plunder when several Warparties conquer it together, and the conquered Player is knocked out.
_Avoid_: Domination, elimination

## Events

**Event**:
Something that happens to a Nation, a Warparty or a Nation's Scouts outside the Players' direct actions, announced through the Turn Report (e.g. a plague, a good harvest, a dust storm delaying a Warparty, rumours of an approaching army). How much it changes varies a little by luck.
_Avoid_: Incident, occurrence

**Lasting Event**:
An Event whose change applies again each Turn for some Turns. A Nation has at most one at a time, and none can begin for a few Turns after one ends.
_Avoid_: Modifier, buff, debuff, status

**Dilemma**:
An Event that offers its Player two or three choices; the Player may change their choice until the Turn ends, and if they have not chosen by then, the worst choice applies. What a choice leads to is partly luck, so even the best choice can turn out badly and the worst well; the Player is not told the odds.
_Avoid_: Choice, decision, prompt
