# miniciv

A turn-based, player-vs-player strategy game in the browser where each player leads a nation, spending resources and research to outbuild and conquer their opponents.

## Game structure

**Game**:
One match of miniciv for 2–4 Players, from start until one Nation is left standing; Barbarian Camps do not count.
_Avoid_: Match, session, round

**Room**:
The place Players gather before a Game starts, joined by a room code of three words or an invite link; the Game starts once 2–4 Players are all ready, and no one can join after that. Once a Game ends, each Player who chooses to play again returns to the Room, keeping their Nation's name, Ruler, colour and emblem, and others may join again for the next Game. A Room none of whose Players has been connected for 60 seconds is removed, and its room code no longer resolves.
_Avoid_: Lobby, server

**Player**:
A person taking part in a Game. A Player who stays disconnected for more than five minutes in total over a Game has their Nation Removed. A Player may also leave the Game from its header, after confirming, which Removes their Nation at once without Pausing the Game and returns them to the menu.
_Avoid_: User, account

**Paused**:
The state of a Game while any Player whose Nation is still in it is disconnected: the Turn timer stops and no Player can act until they reconnect or are Removed.
_Avoid_: Frozen, suspended, on hold

**Removed**:
A Nation taken out of the Game because its Player left the Game or was disconnected for more than five minutes in total; unlike Conquest, nobody seizes anything from it, and Warparties heading to it turn back home.
_Avoid_: Kicked, dropped, forfeited

**Turn**:
One step of the Game, at most two minutes long not counting time Paused, in which all Players make their decisions at the same time, after which everything resolves together.
_Avoid_: Round, tick

**Turn Report**:
The short text description each Player receives at the start of a Turn of what happened as the previous Turn resolved, headed by how its Nation's Gold, Population and Research moved and grouped into sections by kind (alerts, warfare, completions, Events, Spies and Scouts, resources).
_Avoid_: Log, summary, news

**Message**:
Private text one Player sends to one other Player during a Game, seen only by the two of them and kept until the Game's Room is gone; Players knocked out can still send and receive them, Players Removed cannot. A Player may send at most 500 Messages in a Game.
_Avoid_: Chat, DM, whisper

**Map**:
A read-only graphic of every Nation's state; Players do not act through it.
_Avoid_: Board

## Nations

**Nation**:
The country a Player leads in a Game; what owns Gold, Population, Research, Buildings and Units. Every Nation starts the same; other Players initially know only its name, Ruler, colour and emblem, each unique among the Nations in the Game. A Nation's name is at most 20 characters.
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
A Nation's or Barbarian Camp's protection against attack, raised by defensive Buildings, which must be worn down to 0 before an attack can reduce a Nation's Population or destroy a Camp.
_Avoid_: Walls, fortification, armour

**Damaged**:
A Building harmed but still standing. A Damaged defensive Building has lost some of the Defense it raises and raises only what it has left; any other Damaged Building adds none of its effects until Repaired, and harming it further does nothing more.
_Avoid_: Hurt, worn, weakened

**Destroyed**:
A defensive Building that has lost all of its Defense; it raises no Defense and adds none of its other effects until Rebuilt. Only defensive Buildings can be Destroyed.
_Avoid_: Ruined, razed, lost

**Repair**:
Restoring one Damaged Building, paying Gold upfront and taking some Turns: for a defensive Building, half of what constructing it would in proportion to the Defense lost; for any other, half of what constructing it would, rounded up.
_Avoid_: Fix, mend

**Rebuild**:
Restoring one Destroyed Building, paying Gold upfront and taking some Turns, both half of what constructing it would, rounded up.
_Avoid_: Reconstruct, restore

**Breached**:
The state of a Nation that has at least one Destroyed defensive Building, or none at all; a Battle at a Breached Nation can also Damage its other Buildings.
_Avoid_: Open, undefended, sacked

**Sell**:
Giving up one standing Building at once in return for Gold: half its cost, or a quarter if it is Damaged. A Destroyed Building cannot be Sold.
_Avoid_: Demolish, scrap, refund

## Warfare

**Located**:
Another Nation or a Barbarian Camp a Nation has found: a Nation through a Scout Task, a Battle that Nation's Warparty fought against it, or a caught Spy of that Nation confessing; a Camp only through a Scout Task, which can search for a Camp the Nation has heard the name of or for any in the wilds. Only a Located Nation can be sent Spies or Warparties, and only a Located Camp Warparties; Locating a Camp shows its Army, Buildings and Targets as they were at the time. Locating is one-way and lasts the whole Game.
_Avoid_: Found, discovered, known

**Unit**:
A single soldier of a given type (e.g. archer, horseman, warrior, scout) that a Nation trains over one or more Turns by paying Gold and Population upfront, and then pays a small Gold upkeep for each Turn.
_Avoid_: Troop

**Strength**:
How much a Unit contributes in a Battle, multiplied when it fights a Unit type it counters.
_Avoid_: Power, attack, damage

**Army**:
The Units a Nation or Barbarian Camp has at home; Units away in a Warparty, Spies abroad and Scouts are not part of it.
_Avoid_: Garrison, defenders

**Warparty**:
A group of Units a Player sends from their Army toward another Nation or a Barbarian Camp, or a Camp sends toward one of its Targets; it travels for some Turns before arriving, more the greater its Strength, and on the way home more still the more Plunder it carries.
_Avoid_: Attack, expedition, army

**Scout**:
A Unit that roams the wilds from the moment it is trained, doing whatever Task it is set: Locating a Nation, watching for Warparties abroad or hunting other Nations' travelling Spies. Scouts are never at home or part of the Army, have no Strength and never fight, and no Warparty or Scout can meet or catch one; only Events, or upkeep its Nation cannot pay, can lose them.
_Avoid_: Spy, lookout

**Spy**:
A Unit that travels to another Located Nation and stays there carrying out whatever Mission it is set, until caught, Unmasked, or heading home with a Haul. Spies kept at home run Counter-intelligence, and Scouts hunting travelling Spies and travelling Warparties can catch Spies still travelling, to or from their target. A caught Spy dies, stays silent or confesses, revealing its sender and a snapshot of the sender's Nation; the sender is told only if another of its Spies in the catching Nation reports it.
_Avoid_: Scout, agent

**Mission**:
What one Spy in place at another Nation is set to do for a fixed number of Turns: report one kind of Fact, or sabotage, poison, incite or steal. The Player may switch it at any time, losing its progress; when it ends the Spy is Idle.
_Avoid_: Task, activity, operation, order

**Task**:
What some of a Nation's Scouts are set to do, such as Locating a Nation or watching for Warparties; some take any number of Scouts, others exactly one.
_Avoid_: Mission, activity, job

**Idle**:
A Spy with no Mission or a Scout with no Task; it does nothing at all, though an Idle Spy abroad can still be caught.
_Avoid_: Waiting, unassigned, free

**Unmasked**:
A Spy whose sender the Nation it is at learns, by chance, as one of its Missions ends, more likely when the Mission failed. It is caught on the spot or flees home carrying any Haul; its sender learns of it only if it fled, and the Nation that unmasked it does not thereby Locate the sender.
_Avoid_: Exposed, blown, discovered

**Haul**:
The Gold or Research a Spy steals, taken from what its target gains the Turn after the Mission succeeds rather than from what the target holds, and only reaching the Spy's Nation once the Spy has carried it home. A Nation that catches the Spy on the way chooses to keep the Haul or send it back to the Nation it was stolen from; either way it takes some Turns to arrive.
_Avoid_: Loot, spoils, stolen goods, Plunder

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
Two Warparties from different Nations or Barbarian Camps meeting by chance while abroad, each seeing the other's Units; if either side chooses to fight, they fight each other, and a Warparty that wipes out the other takes any Plunder it carried. Plunder taken from a third Nation this way may be kept or sent back to that Nation, as with a Haul. Each Player may change their choice until the Turn ends; a Barbarian Camp's Warparty chooses to fight only when its Strength is at least the other's, and never meets another Camp's.
_Avoid_: Skirmish, ambush

**Battle**:
The fight that happens when Warparties arrive at a Nation or Barbarian Camp; Warparties arriving on the same Turn fight as one. Its outcome is reported the following Turn, and surviving attackers head home.
_Avoid_: Fight, combat, clash

**Plunder**:
The Gold and Research a Warparty's surviving Units take from the Nation they fought, or the Gold and Captives for a Barbarian Camp's Warparty, scaled by their remaining Strength and carried home. Warparties fighting a Battle as one split it in proportion to their remaining Strength.
_Avoid_: Loot, spoils, booty

**Captives**:
Population a Barbarian Camp's Warparty carries off from the Nation it fought, as part of its Plunder; lost to that Nation as Population killed in the Battle would be.
_Avoid_: Prisoners, slaves, hostages

**Conquest**:
Taking a Nation by bringing both its Population and its Army to 0 in a Battle that some of the attacking Units survive; a Nation is only conquered when attacked, and a Warparty wiped out in the Battle conquers nothing. The conquering Nation seizes a large share of its Gold and Research, split like Plunder when several Warparties conquer it together, and the conquered Player is knocked out.
_Avoid_: Domination, elimination

## Barbarians

**Barbarian Camp**:
A hostile band no Player leads, not a Nation, which appears in the wilds partway through a Game, named for its place (e.g. "Mountain Passage Barbarian Camp"), with a Ruler of its own and the same dark red colour and barbarian emblem as every other Camp. It has no Gold, Population or Research, only an Army, which grows by itself, and barbarian defensive Buildings, which raise Defense only and which it starts with and can neither build nor Repair. A Nation knows only a Camp's name once attacked by or Encountering its Warparties, and nothing at all before that, until it Locates the Camp. It sends Warparties only at its Targets, never Conquers, always leaving a Nation some Population, accepts no Tribute, and cannot be sent Spies; what its Warparties carry home is lost for good once they arrive. A Battle that brings both its Army and its Defense to 0 with some attackers surviving destroys it, and they take a Bounty in place of Plunder; its Warparties still abroad finish their trip and then disband with whatever they carry.
_Avoid_: Barbarians, raiders, NPC, tribe

**Bounty**:
The Gold the surviving attackers take for destroying a Barbarian Camp: a fixed sum give or take a little luck, split like Plunder when several Warparties destroy it together.
_Avoid_: Reward, prize, payout

**Target**:
A Nation a Barbarian Camp may send Warparties at: some or all of the Nations in the Game, chosen when the Camp appears, plus any Nation that fights a Battle at it or chooses to fight one of its Warparties in an Encounter. Locating a Camp reveals its Army and its Targets. A Camp that loses a Battle at a Target does not attack that Target again until its Army has outgrown the Warparty it lost.
_Avoid_: Victim, enemy, prey

## Events

**Event**:
Something that happens to a Nation, a Warparty or a Nation's Scouts outside the Players' direct actions, announced through the Turn Report (e.g. a plague, a good harvest, a dust storm delaying a Warparty, rumours of an approaching army, a Scout lost in a blizzard). How much it changes varies a little by luck, except how many Turns it delays a Warparty by, how many Scouts it loses and how many Turns a Lasting Event lasts.
_Avoid_: Incident, occurrence

**Lasting Event**:
An Event whose change applies again each Turn for some Turns. A Nation has at most one at a time, and none can begin for a few Turns after one ends.
_Avoid_: Modifier, buff, debuff, status

**Dilemma**:
An Event that offers its Player two or three choices; the Player may change their choice until the Turn ends, and if they have not chosen by then, the worst choice applies. What a choice leads to is partly luck, so even the best choice can turn out badly and the worst well; the Player is not told the odds. A Nation faces at most one Dilemma per Turn, preferring one it has never faced.
_Avoid_: Choice, decision, prompt
