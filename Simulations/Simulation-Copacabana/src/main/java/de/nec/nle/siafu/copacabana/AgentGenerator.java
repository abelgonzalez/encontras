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

import java.util.ArrayList;
import java.util.Random;

import de.nec.nle.siafu.exceptions.PlaceNotFoundException;
import de.nec.nle.siafu.copacabana.Constants.Activity;
import de.nec.nle.siafu.model.Agent;
import de.nec.nle.siafu.model.Place;
import de.nec.nle.siafu.model.Position;
import de.nec.nle.siafu.model.World;
import de.nec.nle.siafu.types.BooleanType;
import de.nec.nle.siafu.types.EasyTime;
import de.nec.nle.siafu.types.IntegerNumber;
import de.nec.nle.siafu.types.Text;
import de.nec.nle.siafu.types.TimePeriod;
import kafka.core.ProducerCreator;
import kafka.model.Person;

/**
 * This class creates an agent that's suitable for the Copacabana simulation, by
 * setting randomized values for the whole population.
 * 
 * @author Felipe Carvalho
 * 
 */
final class AgentGenerator {

	/**
	 * A random number generator.
	 */
	private static Random rand = new Random();

	/**
	 * Keep this class from being instantiated.
	 */
	private AgentGenerator() {
	}

	/**
	 * Create a population made up of <code>size</code> random agents.
	 * 
	 * @param size the population size
	 * @param world the world object of the whole simulation
	 * @return an ArrayList with the collection of agents
	 */
	public static ArrayList<Agent> createRandomPopulation(final int size,
			final World world) {
		ArrayList<Agent> population = new ArrayList<Agent>(size);

		for (int i = 0; i < size; i++) {
			population.add(createRandomAgent(world));
		}

		return population;
	}

	/**
	 * Create a random agent that fits the Copacabana simulation.
	 * @param world the world of the simulation
	 * @return the new agent
	 */
	public static Agent createRandomAgent(final World world) {
		Position anywhere = world.getPlaces().get(0).getPos();

		Agent a = new Agent(anywhere, "HumanGreen", world);

		//Person p = ProducerCreator.getInstance().generatePersonData(a.getName(),a.getName());
		//AGENTSMAP.put(a.getName(),p);
		//ProducerCreator.getInstance().send(p);

		boolean hasCar = false;
		if (rand.nextFloat() < PROB_HAS_CAR) {
			hasCar = true;
		}
		boolean isDisobey = false;
		if (rand.nextFloat() < PROB_DISOBEY_SOCIAL_ISOLATION) {
			isDisobey = true;
		}
		boolean hasCovid = false;
		if (rand.nextFloat() < PROB_HAS_COVID) {
			hasCovid = true;
			a.setImage("HumanMagenta");
			//Person contaminatedPerson = ProducerCreator.getInstance().generateContaminatedPerson(a.getName(),a.getName());
			//ProducerCreator.getInstance().sendContaminated(contaminatedPerson);
		}
		int age = MIN_AGE + rand.nextInt(MAX_AGE - MIN_AGE);
		EasyTime workStart = new EasyTime(AVG_WORK_START, 0);
		EasyTime sleepEnd = new EasyTime(workStart).shift(-1, 0);
		EasyTime sleepStart =
				new EasyTime(sleepEnd).shift(-AVG_SLEEP_TIME, 0);

		workStart.blur(TWO_HOUR_BLUR);
		sleepEnd.blur(HALF_HOUR_BLUR);
		sleepStart.blur(TWO_HOUR_BLUR);

		a.set(AGE, new IntegerNumber(age));
		a.set(CUISINE, getRandomType(CUISINE_TYPES));
		a.set(LANGUAGE, getRandomType(LANGUAGE_TYPES));
		a.set(GENDER, getRandomType(GENDER_TYPES));
		a.set(PARTY_ANIMAL, getRandomType(PARTY_ANIMAL_TYPES));
		a.set(WORKAHOLIC, getRandomType(WORKAHOLIC_TYPES));
		a.set(HAS_CAR, new BooleanType(hasCar));
		a.set(IS_DISOBEY, new BooleanType((isDisobey)));
		a.set(ACTIVITY, Activity.ASLEEP);
		a.set(WORK_START, workStart);
		a.set(WORK_END, new EasyTime(workStart).shift(AVG_WORK_TIME, 0));
		a.set(SLEEP_PERIOD, new TimePeriod(sleepStart, sleepEnd));

		a.set(HAS_COVID, new BooleanType(hasCovid));
		a.set(HAD_COVID, new BooleanType(false));
		a.set(SUSPECT_COVID, new BooleanType(false));
		a.set(ITER_CONTACT_COVID, new IntegerNumber(0));

		try {
			a.set(HOME, world.getRandomPlaceOfType("Homes"));
		} catch (PlaceNotFoundException e) {
			throw new RuntimeException(
					"Can't find any homes Places. Did u create them?");
		}

		try {
			a.set(OFFICE, world.getRandomPlaceOfType("Offices"));
		} catch (PlaceNotFoundException e) {
			throw new RuntimeException(
					"Can't find any offices. Did u create them?");
		}

		a.setPos(((Place) a.get(HOME)).getPos());

		return a;
	}

	/**
	 * Return a random element from the given array list.
	 * 
	 * @param types the ArrayList containing the types
	 * @return the randomly chosen type
	 */
	private static Text getRandomType(final ArrayList<Text> types) {
		return types.get(rand.nextInt(types.size()));
	}

}
