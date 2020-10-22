package kafka.core;

public interface IKafkaConstants {
    public static String KAFKA_BROKERS = "139.82.100.102:9092";
    public static Integer MESSAGE_COUNT=2;
    public static String CLIENT_ID="client-covid-finder";

    /**TÓPICOS*/
    public static String TOPIC_NAME="test";
    public static String TOPIC_PEOPLE_DATA = "peopleNewData";
    public static String TOPIC_GLOBAL_TABLE = "peopleStaticData";
    public static String TOPIC_OUT = "peopleUnitedOutput";
    public static String TOPIC_PEOPLE_ENCOUNTERS = "peopleEncounters";
    public static String topicPeopleContaminated = "";
    public static String topicOut2 = "";

    public static String GROUP_ID_CONFIG="consumerGroup1";
    public static Integer MAX_NO_MESSAGE_FOUND_COUNT=100;
    public static String OFFSET_RESET_LATEST="latest";
    public static String OFFSET_RESET_EARLIER="earliest";
    public static Integer MAX_POLL_RECORDS=1;
}