/*
 * Copyright NEC Europe Ltd. 2006-2007
 * 
 * This file is part of the context simulator called Siafu.
 * 
 * Siafu is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * Siafu is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.nec.nle.siafu.copacabana;

import static de.nec.nle.siafu.copacabana.Constants.*;
import static de.nec.nle.siafu.copacabana.Constants.Fields.*;

import java.util.*;

import de.nec.nle.siafu.behaviormodels.BaseAgentModel;
import de.nec.nle.siafu.exceptions.InfoUndefinedException;
import de.nec.nle.siafu.exceptions.PlaceNotFoundException;
import de.nec.nle.siafu.copacabana.Constants.Activity;
import de.nec.nle.siafu.model.Agent;
import de.nec.nle.siafu.model.Place;
import de.nec.nle.siafu.model.World;
import de.nec.nle.siafu.types.*;
import kafka.core.ProducerCreator;
import kafka.model.COVID19_Symptoms;
import kafka.model.Person;

/**
 * Defines the behavior of the agent population. Essentially, they wake up in
 * the morning at home (red spots), and go to work (blue spots) and then
 * either go home and stay, or go to an entertainment place (yellow spots) or
 * one and then the other. Eventually (hopefully before they have to go work
 * again) they go to sleep home, and the cycle resumes.
 * 
 * @author Miquel Martin
 * 
 */
public class AgentModel extends BaseAgentModel {
	long counter = 0;

	/**
	 * Cars move in between 1 and 1+SPEED_RANGE speed.
	 */
	private static final int SPEED_RANGE = 3;

	/**
	 * An agent that owns a car will only use it to cover distances of this
	 * amount.
	 */
	private static final int MIN_DIST_4_CAR = 100;

	/** A random number generator. */
	private static final Random RAND = new Random();

	/**
	 * Noon time.
	 */
	private static final EasyTime NOON = new EasyTime(12, 0);

	/** Set of encounters duration */
	private static final LinkedHashMap<String,Long> encountersDuration = new LinkedHashMap<String,Long>();

	/** Set of active encounters */
	private static final LinkedHashSet<String> activeEncounters = new LinkedHashSet<String>();

	/** Set of previous iteration encounters* */
	private static final LinkedHashSet<String> lastIterationEncounters = new LinkedHashSet<String>();

	/**Infected someone today*/
	private boolean infectedSomeoneToday = false;

	/**
	 * A constructor that simply calls the super constructor (BaseAgentModel).
	 * 
	 * @param world the simulated world
	 */
	public AgentModel(final World world) {
		super(world);
	}

	/**
	 * Create POPULATION agents randomly, using the PersonGenerator class.
	 * 
	 * @return the list of agents.
	 */
	public ArrayList<Agent> createAgents() {
		System.out.println("Creating " + POPULATION + " people");

		ArrayList<Agent> people =
				AgentGenerator.createRandomPopulation(POPULATION, world);

		// We need a value for the first party. It will, however, be ignored
		final int firstPartyStart = 22;
		final int firstPartyEnd = 23;

		for (Agent a : people) {
			a.set(WILL_GO_PARTY, new BooleanType(false));
			a.set(PARTY_START, new EasyTime(firstPartyStart, 0));
			a.set(PARTY_END, new EasyTime(firstPartyEnd, 0));
			try {
				a.set(PARTY_PLACE, world.getRandomPlaceOfType("Entertainment"));
			} catch (PlaceNotFoundException e) {
				throw new RuntimeException("You need to have places of type Entertainment for this simulation.");
			}
		}

		return people;
	}

	/**
	 * Perform an iteration by going through each of the agents. The exact
	 * behaviour is explained in this class' description. Note that agents who
	 * are being controlled by the GUI will not be affected.
	 * 
	 * @param agents the agents in the world (including those controlled
	 *            through the GUI
	 */
	public void doIteration(final Collection<Agent> agents) {
		long timeinit = System.currentTimeMillis();
		Collection<Agent> agentsCopy = new LinkedHashSet<Agent>(agents);
		for (Agent a : agents) {
			if (!a.isOnAuto()) {
				continue; // This guy's being managed by the user interface
			}
			checkEncounter(a,agentsCopy); // Check if this agent has encounters
			agentsCopy.remove(a); // Remove from temporary copy

			Calendar time = world.getTime();
			EasyTime now =
					new EasyTime(time.get(Calendar.HOUR_OF_DAY), time
							.get(Calendar.MINUTE));

			TimePeriod sleepPeriod = (TimePeriod) a.get(SLEEP_PERIOD);
			EasyTime workStart = (EasyTime) a.get(WORK_START);
			EasyTime workEnd = (EasyTime) a.get(WORK_END);
			EasyTime partyEnd = (EasyTime) a.get(PARTY_END);
			EasyTime sleepEnd =
					((TimePeriod) a.get(SLEEP_PERIOD)).getEnd();
			switch ((Activity) a.get(ACTIVITY)) {
			case ASLEEP:
				if (!now.isIn(sleepPeriod)) {
					a.set(ACTIVITY, Activity.AT_HOME);
				}

				break;

			case AT_HOME:

				if (now.isAfter(workStart) && now.isBefore(workEnd) && ((BooleanType) a.get(IS_DISOBEY)).getValue()) {
					goWork(a);
				} else if (isTimeForParty(now, a) && ((BooleanType) a.get(IS_DISOBEY)).getValue()) {
					goParty(a);
				} else if (now.isIn(sleepPeriod)) {
					a.set(ACTIVITY, Activity.ASLEEP);
				} else {
					beIdleAtHome(a);
				}

				break;

			case GOING_TO_WORK:

				if (a.isAtDestination()) {
					a.set(WILL_GO_PARTY, new BooleanType(false));
					a.set(ACTIVITY, Activity.WORKING);
					carify(a, false, "HumanBlue", 1);
					setWorkEnd(a, workStart);
				}

				break;

			case WORKING:
				if (!now.isBefore((EasyTime) a.get(WORK_END))) {
					decideAboutGoingParty(a);
					goHome(a);
				} else {
					beAtWork(a);
				}

				break;

			case GOING_HOME:

				if (a.isAtDestination()) {
					a.set(ACTIVITY, Activity.AT_HOME);
				} else if (isTimeForParty(now, a)) {
					goParty(a);
				}

				break;

			case GOING_TO_PARTY:

				if (a.isAtDestination()) {
					a.set(ACTIVITY, Activity.AT_PARTY);
					a.set(WILL_GO_PARTY, new BooleanType(false));
					setPartyEnd(a, now);
				}

				break;

			case AT_PARTY:
				if (now.isAfter(partyEnd)) {
					goHome(a);
				} else if (now.isAfter(sleepEnd) && now.isBefore(NOON)) {
					goWork(a);
				} else {
					beAtParty(a);
				}

				break;
			default:
				throw new RuntimeException("Unable to handle activity "
						+ (Activity) a.get(ACTIVITY));
			}

			//printToConsole(a);
		}
		//System.out.println(counter);
		//counter++;
		checkIteration();
		Calendar time = world.getTime();
		EasyTime now =
				new EasyTime(time.get(Calendar.HOUR_OF_DAY), time
						.get(Calendar.MINUTE));
		if (now.isAfter(NOON) && now.isBefore(new EasyTime(12,01)) && infectedSomeoneToday) {
			infectedSomeoneToday = false;
		}
		if (now.isAfter(new EasyTime(16,30)) && infectedSomeoneToday) {
			infectedSomeoneToday = false;
		}
		if (now.isBefore(NOON) && now.isAfter(new EasyTime(11,00)) && !infectedSomeoneToday) {
			System.out.println("Infecting Someone");
			infectSomeone();
			System.out.println("Changing Symptoms");
			changeSymptoms();
		}
		if (now.isAfter(new EasyTime(16,00)) && now.isBefore(new EasyTime(16,01)) && !infectedSomeoneToday) {
			System.out.println("Changing Symptoms");
			changeSymptoms();
		}
		long timefinish = System.currentTimeMillis();
		System.out.println("Tempo: "+(timefinish-timeinit));
	}

	private void changeSymptoms() {
		Iterator<Agent> peopleIt = world.getPeople().iterator();
		while (peopleIt.hasNext()) {
			Agent a = peopleIt.next();
			Person p = AGENTSMAP.get(a.getName());
			Iterator<Map.Entry<COVID19_Symptoms, String>> it = p.get_symptoms().entrySet().iterator();
			while(it.hasNext()) {
				Map.Entry<COVID19_Symptoms, String> entry = it.next();
				if (RAND.nextFloat() < 0.01f) {
					if (entry.getKey().getName().equalsIgnoreCase(COVID19_Symptoms.SYMPTOM10.getName())) {

					} else {
						if (entry.getValue().equals("Yes")) {
							p.add_symptom(entry.getKey(),"No");
						} else {
							p.add_symptom(entry.getKey(),"Yes");
						}
					}
				}
			}
			AGENTSMAP.put(a.getName(),p);
			//ProducerCreator.getInstance().send(p);
		}
		infectedSomeoneToday = true;
	}

	private void infectSomeone() {
		Iterator<Agent> peopleIt = world.getPeople().iterator();
		while (peopleIt.hasNext()) {
			Agent a = peopleIt.next();
			if (!((BooleanType) a.get(HAS_COVID)).getValue()) {
				a.set(HAS_COVID, new BooleanType(true));
				a.setImage("HumanMagenta");
				//Person p = ProducerCreator.getInstance().generateContaminatedPerson(a.getName(),a.getName());
				//ProducerCreator.getInstance().sendContaminated(p);
				break;
			}
		}
		infectedSomeoneToday = true;
	}

	private void printToConsole(Agent a) {
		StringBuilder line = new StringBuilder("");
		line.append(new Text("" + world.getTime().getTimeInMillis()).flatten()).append(",");
		line.append(new Text(a.getName()).flatten()).append(",");
		line.append(a.getPos().flatten()).append(",");
		line.append(new BooleanType(a.isAtDestination()).flatten()).append(",");
		Iterator ait = Agent.getInfoKeys().iterator();
		for (Publishable info : a.getInfoValues()) {
			line.append(ait.next()).append(": ").append(info.toString()).append(",");
		}
		System.out.println(line.toString());

	}

	private void checkEncounter(Agent thisOne, Collection<Agent> allAgents) {
		for (Agent agent : allAgents) {
			if (!thisOne.getName().equalsIgnoreCase(agent.getName())
			&& thisOne.getPos().isNear(agent.getPos(), 30)
			) {
				if (!encountersDuration.containsKey(thisOne.getName()+" "+agent.getName()) && !encountersDuration.containsKey(agent.getName()+" "+thisOne.getName())) {
					encountersDuration.put(thisOne.getName()+" "+agent.getName(),10L);
					activeEncounters.add(thisOne.getName()+" "+agent.getName());
					//System.out.println("First "+thisOne.getName()+ " " + agent.getName()+ " initial "+10);
				} else if (encountersDuration.containsKey(thisOne.getName()+" "+agent.getName())) {
					//System.out.println(thisOne.getName()+ " " + agent.getName()+ " add "+10);
					activeEncounters.add(thisOne.getName()+" "+agent.getName());
					encountersDuration.computeIfPresent(thisOne.getName()+" "+agent.getName(),(k,v) -> v + 10L);
				} else if (encountersDuration.containsKey(agent.getName()+" "+thisOne.getName())) {
					//System.out.println("Reverse "+agent.getName()+" "+thisOne.getName()+ " add "+10);
					activeEncounters.add(agent.getName()+" "+thisOne.getName());
					encountersDuration.computeIfPresent(agent.getName()+" "+thisOne.getName(),(k,v) -> v + 10L);
				}
			}
		}
	}

	private void checkIteration() {
		/*if (lastIterationEncounters.isEmpty()) {
			lastIterationEncounters.addAll(activeEncounters);
			return;
		}*/
		for (Iterator<String> iterator = lastIterationEncounters.iterator(); iterator.hasNext();) {
			String encounter = iterator.next();
			if (!activeEncounters.contains(encounter)) {

				Long num =  encountersDuration.get(encounter);
				String[] personsInEncounter = encounter.split(" ");
				Person p = null; // Build Object
				if (num != null && num>=300) {
					p = ProducerCreator.getInstance()
							.generateEncounterData(personsInEncounter[0],
									personsInEncounter[0],
									personsInEncounter[1],
									personsInEncounter[1],
									String.valueOf(num*1000L));

					System.out.println("ENCONTRO ENTRE "+personsInEncounter[0]+" e "+personsInEncounter[1]+" Duração: "+num+" s");
					//send to kafka
					//ProducerCreator.getInstance().sendEncounter(p);
					iterator.remove();
					if (encountersDuration.remove(encounter)==null) {
						String[] s = encounter.split(" ");
						encountersDuration.remove(s[1]+" "+s[0]);
					}
				} else if (num>=300){
					Long num1 = encountersDuration.get(personsInEncounter[1]+" "+personsInEncounter[0]);
					p = ProducerCreator.getInstance()
							.generateEncounterData(personsInEncounter[1],
									personsInEncounter[1],
									personsInEncounter[0],
									personsInEncounter[0],
									String.valueOf(num1*1000L));
					System.out.println("ENCONTRO ENTRE "+personsInEncounter[1]+" e "+personsInEncounter[0]+" Duração: "+num+" s");
					//send to kafka
					//ProducerCreator.getInstance().sendEncounter(p);
					iterator.remove();
					if (encountersDuration.remove(encounter)==null) {
						String[] s = encounter.split(" ");
						encountersDuration.remove(s[1]+" "+s[0]);
					}
				}

			}
		}
		/*Iterator<Map.Entry<String, Long>> it = encountersDuration.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String,Long> encounter = it.next();
			if (encounter.getValue() >= 14400 ) {
				//send to kafka
				it.remove();
			}
		}*/
		lastIterationEncounters.addAll(activeEncounters);
		activeEncounters.clear();
	}

	private void checkIfNextToSomeoneWithCovid(Agent thisOne, Collection<Agent> agents) {

		for (Agent agent : agents) {
			if (!thisOne.getName().equalsIgnoreCase(agent.getName())
					&& !((BooleanType) thisOne.get(HAS_COVID)).getValue()
					&& !((BooleanType) thisOne.get(HAD_COVID)).getValue()
					&& ((BooleanType) agent.get(HAS_COVID)).getValue()
					&& thisOne.getPos().isNear(agent.getPos(), 10)
					) {

				System.out.println("Agent "+ thisOne.getName()+" next to "+agent.getName());
				int iter = ((IntegerNumber) thisOne.get(ITER_CONTACT_COVID)).getNumber();
				thisOne.set(ITER_CONTACT_COVID, new IntegerNumber(++iter));
				/** Each iteration step represents 10 seconds. */
				if (iter > 11) {
					encountersDuration.put(thisOne.getName()+agent.getName(),120L);
					thisOne.set(ITER_CONTACT_COVID, new IntegerNumber(0));
					thisOne.set(SUSPECT_COVID, new BooleanType(true));
				}
				//break;
			}
		}
	}

	/**
	 * Modifies the agent to behave like a car by setting the right speed and
	 * appearance).
	 * 
	 * @param a the agent to turn into a car
	 * @param turnToCar true if the agent should become a car, false if it
	 *            should look back like a person when looking like a car.
	 * @param appearance the name of the sprite that represents the car we
	 *            need
	 * @param speed the speed of a car. The speed of a person is 1 by default.
	 */
	protected void carify(final Agent a, final boolean turnToCar,
			final String appearance, final int speed) {
		if (turnToCar) {
			//a.setImage(appearance);
			a.setSpeed(speed);
		} else {
			a.setSpeed(1);
			//a.setImage(appearance);
		}
	}

	/**
	 * Give a numeric value to a string from a sorted list of strings. In this
	 * case, the workaholic and party animal levels. For instance, a hermit or
	 * a slacker have a level of 0, while "Just say where" or terminal
	 * workaholics are whoping top level 4.
	 * 
	 * @param value the value to rate
	 * @param types the list of possible types to rate against
	 * @return the party animal index, between 0 and 4, both included
	 */
	protected int getIndex(final Text value, final ArrayList<Text> types) {
		for (int i = 0; i < types.size(); i++) {
			if (value.equals(types.get(i))) {
				return i;
			}
		}
		throw new RuntimeException("Uknown value: " + value);
	}

	/**
	 * Set the time at which the party ends, which is the given start time
	 * plus a number of hours depending on the party animal factor, and
	 * blurred over one hour.
	 * 
	 * @param a the agent whose party end we want to set
	 * @param start the time at which the party starts
	 */
	protected void setPartyEnd(final Agent a, final EasyTime start) {
		int paIndex =
				getIndex((Text) a.get(PARTY_ANIMAL), PARTY_ANIMAL_TYPES);
		EasyTime partyEnd =
				new EasyTime(start)
						.shift(paIndex * PARTY_ANIMAL_FACTOR, 1);
		partyEnd.blur(ONE_HOUR_BLUR);
		a.set(PARTY_END, partyEnd);
		return;
	}

	/**
	 * Set the time at which the agent leaves work, which is the given start
	 * time plus a AVG_WORK_TIME plus number of hours depending on the
	 * workaholic factor, and blurred over one hour.
	 * 
	 * @param a the agent whose party end we want to set
	 * @param start the time at which the party starts
	 */
	protected void setWorkEnd(final Agent a, final EasyTime start) {
		int paIndex = getIndex((Text) a.get(WORKAHOLIC), WORKAHOLIC_TYPES);
		EasyTime partyEnd =
				new EasyTime(start).shift(AVG_WORK_TIME + paIndex
						* WORKAHOLIC_FACTOR, 1);
		partyEnd.blur(ONE_HOUR_BLUR);
		a.set(WORK_END, partyEnd);
		return;
	}

	/**
	 * Upon leaving work a agent decides if he is gonig party. The more of a
	 * party animal the agent is, the more chances that he will decide to go
	 * party. Also, the more of a party animal, the later he will start the
	 * party
	 * 
	 * @param a the agent for whom we have to decide
	 * @throws InfoUndefinedException
	 */
	protected void decideAboutGoingParty(final Agent a) {
		int paIndex =
				getIndex((Text) a.get(PARTY_ANIMAL), PARTY_ANIMAL_TYPES);

		if (RAND.nextInt(PARTY_ANIMAL_TYPES.size()) < paIndex) {
			// Go party
			EasyTime workEnd = ((EasyTime) a.get(WORK_END));
			int shift = WORK_PARTY_MIN_TIME + paIndex;
			EasyTime partyStart = new EasyTime(workEnd).shift(shift, 0);
			partyStart.blur(TWO_HOUR_BLUR);
			a.set(WILL_GO_PARTY, new BooleanType(true));
			a.set(PARTY_START, partyStart);
			try {
				a.set(PARTY_PLACE, world
						.getRandomPlaceOfType("Entertainment"));
			} catch (PlaceNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Figure out if the agent should be heading for a party right now,
	 * namely, whether he has decided to go party today, and if the time to go
	 * has already past.
	 * 
	 * @param now the current time
	 * @param a the agent being considered
	 * @return true if it is time to go party, false otherwise
	 */
	protected boolean isTimeForParty(final EasyTime now, final Agent a) {
		return ((BooleanType) a.get(WILL_GO_PARTY)).getValue()
				&& now.isAfter((EasyTime) a.get(PARTY_START));
	}

	/**
	 * Go to the assigned office. If the place is far, and the agent has a car
	 * then he turns into a car to go there.
	 * 
	 * @param a the agent to sent to work
	 */
	protected void goWork(final Agent a) {
		a.set(ACTIVITY, Activity.GOING_TO_WORK);

		if ((((Place) a.get(OFFICE)).distanceFrom(a.getPos()) > MIN_DIST_4_CAR)
				&& ((BooleanType) a.get(HAS_CAR)).getValue()) {
			carify(a, true, "CarBlue", RAND.nextInt(SPEED_RANGE) + 1);
		} else {
			//a.setImage("HumanBlue");
		}

		a.setDestination((Place) a.get(OFFICE));
	}

	/**
	 * Send the agent to an entertainment place.
	 * 
	 * @param a the agent to be sent
	 */
	protected void goParty(final Agent a) {
		a.set(ACTIVITY, Activity.GOING_TO_PARTY);
		//a.setImage("HumanYellow");
		try {
			a.setDestination((Place) world
					.getRandomPlaceOfType("Entertainment"));
		} catch (PlaceNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Send the agent home.
	 * 
	 * @param a the agent to be sent home
	 */
	protected void goHome(final Agent a) {
		a.set(ACTIVITY, Activity.GOING_HOME);
		//a.setImage("HumanMagenta");
		a.setDestination((Place) a.get(HOME));
	}

	/**
	 * Keeps the user wandering around home.
	 * 
	 * @param a the agent to keep at home
	 */
	protected void beIdleAtHome(final Agent a) {
		a.wanderAround((Place) a.get(HOME), SMALL_WANDER);
	}

	/**
	 * Do the activities related to being at work. In this case, wander around
	 * the work place with a radius of <code>SMALL_WANDER</code>.
	 * 
	 * @param p the agent that should be at work
	 * @throws InfoUndefinedException if the person doesn't exist
	 */
	protected void beAtWork(final Agent p) throws InfoUndefinedException {
		p.wanderAround((Place) p.get(OFFICE), SMALL_WANDER);
	}

	/**
	 * Keeps the agent int he entertainment place, wandering around, and
	 * potentially getting out of the entertainment area.
	 * 
	 * @param a the agent to keep at the party
	 */
	protected void beAtParty(final Agent a) {
		a.wanderAround((Place) a.get(PARTY_PLACE), BIG_WANDER);
	}
}
