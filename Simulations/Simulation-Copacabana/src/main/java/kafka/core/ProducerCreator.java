package kafka.core;

import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import com.google.gson.Gson;
import kafka.model.COVID19_Symptoms;
import kafka.model.Person;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class ProducerCreator {
    private static Random r;
    private static ProducerCreator instance;
    private static Producer<String,String> producer;
    private static Gson g;

    private ProducerCreator() {
        r = new Random();
        g = new Gson();
        producer = createProducer();
    }

    public static synchronized ProducerCreator getInstance() {
        if (instance == null) {
            instance = new ProducerCreator();
        }
            return instance;
    }

    public static Producer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IKafkaConstants.KAFKA_BROKERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, IKafkaConstants.CLIENT_ID);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        //props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, CustomPartitioner.class.getName());
        return new KafkaProducer<String, String>(props);
    }

    public static Person generatePersonData(String personName, String personID)
    {
        Person person = new Person();
        person.set_id(personID);
        person.set_name(personName);
        COVID19_Symptoms[] symptoms = COVID19_Symptoms.values();
        /*Arrays.sort(symptoms);*/
        for(COVID19_Symptoms symptom: symptoms)
        {
            if(symptom.getName().equalsIgnoreCase(COVID19_Symptoms.SYMPTOM10.getName())) {
                person.add_symptom(symptom, "No"); // No inicio nao tem info de encontro.
                continue;
            }
            if (r.nextFloat() < 0.1f)
                person.add_symptom(symptom,"Yes");
            else
                person.add_symptom(symptom,"No");
        }
        return person;
    }

    public static Person generateContaminatedPerson(String personName, String personID)
    {
        Person person = new Person();
        person.set_id(personID);
        person.set_name(personName);
        person.set_isContaminated(true);

        return person;
    }

    public static Person generateEncounterData(String personID, String personName, String personMeetID, String personMeetName, String timeDuration)
    {
        Person person = new Person();
        person.set_id(personID);
        person.set_name(personName);

        Person personMeet = new Person();
        personMeet.set_id(personMeetID);
        personMeet.set_name(personMeetName);

        person.add_encounter(personMeet, timeDuration);

        return person;
    }

    private static ProducerRecord<String, String> peopleDataStream(String key, String value){
        return new ProducerRecord<>("peopleNewData2", key, value);
    }
    private static ProducerRecord<String, String> peopleGlobalTable(String key, String value){
        return new ProducerRecord<>("peopleStaticData2", key, value);
    }
    private static ProducerRecord<String, String> peopleEncountersStream(String key, String value){
        return new ProducerRecord<>("peopleEncounters2", key, value);
    }
    private static ProducerRecord<String, String> peopleContaminatedStream(String key, String value){
        return new ProducerRecord<>("peopleContaminated2", key, value);
    }

    public static void send(Person p) {
        try {
            producer.send(peopleDataStream(p.get_id(), g.toJson(p))).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static void sendContaminated(Person p) {
        try {
            producer.send(peopleContaminatedStream(p.get_id(),g.toJson(p))).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static void sendEncounter(Person p) {
        try {
            producer.send(peopleEncountersStream(p.get_id(), g.toJson(p))).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}