package com.avispl.dal.communicator.pexip;

import com.avispl.dal.communicator.dto.entities.ReportWrapper;
import com.avispl.dal.communicator.dto.entities.ManagementNodeResponse;
import com.avispl.dal.communicator.dto.conferences.Conference;
import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Device Aggregator communicator for Pexip Management Node
 *
 * Main features (v1):
 * - Conferencing Nodes are provisioned as Aggregated Devices
 * - Conferencing Nodes extended properties keep track of conferencing node status and configuration
 * - Conferencing Nodes keep track of all active conferences at a runtime
 * - Conferencing Nodes have an option to disconnect a specific meeting
 * - Conferencing Nodes have an option of exporting list of active conference's participants as csv over email (requires SMTP setup)
 * - Management Node extended properties contain licensing information and port data
 * - Management Node has extra options of exporting licencing reports, since-date reports and monthly reports over email (requires SMTP setup)
 *
 * @author Maksym.Rossiytsev
 * @since Symphony 5.1
 */
public class PexipManagementNode extends RestCommunicator implements Monitorable, Controller, Aggregator {
    private static final String BASE_URI = "api/admin/";
    private static final String CONFERENCING_NODES_URI = "status/v1/worker_vm/?limit=%s";
  //  private static final String MANAGEMENT_NODES_URI = "status/v1/management_vm/";
    private static final String CONFERENCE_SHARD_URI = "status/v1/conference_shard/?limit=%s";
    private static final String CONFERENCE_PARTICIPANTS_URI = "/status/v1/participant/?limit=%s&conference=%s"; //lookup by conference name
    private static final String PARTICIPANTS_URI = "status/v1/participant/?limit=%s";
    private static final String LICENSING_URI = "status/v1/licensing/";
    private static final String CONFERENCE_URI = "status/v1/conference/?limit=%s";
    private static final String CONFERENCING_NODES_CONFIGURATION_URI = "configuration/v1/worker_vm/?limit=%s"; //address corresponds conference's "node", so it's the way to figure out exact node for a conference
    private static final String HISTORICAL_PARTICIPANTS_URI = "history/v1/participant/?limit=%s&end_time__gte=%s&end_time__lt=%s"; // default limit is 5000
    private static final String HISTORICAL_CONFERENCE_URI = "history/v1/conference/?limit=%s&end_time__gte=%s&end_time__lt=%s"; // default limit is 5000

    private static final String COMMAND_DISCONNECT_PARTICIPANT = "command/v1/participant/disconnect/";
    private static final String COMMAND_DISCONNECT_CONFERENCE = "command/v1/conference/disconnect/";

    private static final String OBJECTS = "objects";

    /*TODO: OTJ for v2:*/
    /*
    private static final String ONE_TOUCH_JOIN_MEETINGS_URI = "status/v1/mjx_meeting/";
    private static final String ONE_TOUCH_JOIN_MEETINGS_PER_DATE_URI = "status/v1/mjx_meeting/?start_time__icontains=%s"; //both for specific date and previous day
    private static final String ONE_TOUCH_JOIN_MEETINGS_PER_ENDPOINT_URI = "status/v1/mjx_meeting/?endpoint_name=%s"; //
    */

    private static int smtpPort = 25;
    private static String smtpHost = "172.31.11.188";
    private static String smtpUsername = "notifications@vnocsymphony.com";
    private static String smtpPassword = "$uPP0rt16";
    private static String smtpSender = "noreply@avispl.com";

    private int daysBackReports = 1;
    private boolean displayConferencesStatistics = false;
    private boolean displayParticipantsStatistics = false;

    private String emailReportsRecipients;
    private JavaMailSender mailSender;
    private final int RESPONSE_LIMIT = 5000;

    /** Device adapter instantiation timestamp. */
    private long adapterInitializationTimestamp;
    /*Name:ID pair to lookup id for specific control actions*/
    private Map<String, String> knownConferences = new HashMap<>();
    private Map<String, String> knownParticipants = new HashMap<>();

    private AggregatedDeviceProcessor aggregatedDeviceProcessor;
    Properties properties = new Properties();

    /*
    * Historical:
    *   $mgr_part = "https://" + $mgr_host + "/api/admin/history/v1/participant/" + "?limit=5000" + "&end_time__gte=" + $start + "&end_time__lt=" + $pexNow
    *   $mgr_conf = "https://" + $mgr_host + "/api/admin/history/v1/conference/" + "?limit=5000" + "&end_time__gte=" + $start + "&end_time__lt=" + $pexNow
    * Otj previous day:
    *   $mgr_meet = "https://" + $mgr_host + "/api/admin/status/v1/mjx_meeting/?start_time__icontains=" + $start
    * Otj per endpoint:
    *   $mgr_meet = "https://" + $mgr_host + "/api/admin/status/v1/mjx_meeting/?endpoint_name=<endpoint_name>"
    * Otj per date:
    *   $mgr_meet = "https://" + $mgr_host + "/api/admin/status/v1/mjx_meeting/?start_time__icontains=2021-01-29"
    * Otj meetings:
    *   $mgr_meet = "https://" + $mgr_host + "/api/admin/status/v1/mjx_meeting/"
    * */

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        List<Statistics> statistics = new ArrayList<>();
        ExtendedStatistics extendedStatistics = new ExtendedStatistics();
        statistics.add(extendedStatistics);
//        Map<String, String> dynamicStatistics = new HashMap<>();
        Map<String, String> staticStatistics = new HashMap<>();

        staticStatistics.put("AdapterVersion", properties.getProperty("pexip.aggregator.version"));
        staticStatistics.put("AdapterBuildDate", properties.getProperty("pexip.aggregator.build.date"));
        staticStatistics.put("AdapterUptime", normalizeUptime((System.currentTimeMillis() - adapterInitializationTimestamp)/1000));
        staticStatistics.put("Export#LicensingReport", "");
        staticStatistics.put("Export#HistoricalReport", "");
        staticStatistics.put("Export#DaysBack", "");
        staticStatistics.put("Export#TotalStatistics", "");

        List<AdvancedControllableProperty> controllableProperties = new ArrayList<>();

        controllableProperties.add(createNumber("Export#DaysBack", daysBackReports));
        controllableProperties.add(createButton("Export#TotalStatistics", "Export", "Exporting", 0L));
        controllableProperties.add(createButton("Export#HistoricalReport", "Export", "Exporting", 0L));
        controllableProperties.add(createButton("Export#LicensingReport", "Export", "Exporting", 0L));

        JsonNode response = doGet(LICENSING_URI, JsonNode.class);
        ArrayNode licensingData = (ArrayNode) response.get(OBJECTS);
        if(licensingData != null && !licensingData.isEmpty()) {
            /* After 5.2 will be changed to Map<String, String> dynamicStatistics = new HashMap<>(); */
            AggregatedDevice licensingStatistics = new AggregatedDevice();
            aggregatedDeviceProcessor.applyProperties(licensingStatistics, licensingData.get(0), "NodeLicensing");
            Map<String, String> dynamicProperties = licensingStatistics.getProperties();
            extendedStatistics.setDynamicStatistics(dynamicProperties);
        }

        extendedStatistics.setStatistics(staticStatistics);
        extendedStatistics.setControllableProperties(controllableProperties);

        return statistics;
    }

    @Override
    protected void internalInit() throws Exception {
        adapterInitializationTimestamp = System.currentTimeMillis();
        setBaseUri(BASE_URI);
        setTrustAllCertificates(true);
        super.internalInit();

        Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("mapping/model-mapping.yml", getClass());
        aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
        mailSender = mailSender();
        properties.load(getClass().getResourceAsStream("/version.properties"));
    }

    /**
     * Setting up {@link JavaMailSender} for populating csv reports
     * Requires multiple settings to be inplace - {@link #smtpHost}, {@link #smtpPort}, {@link #smtpUsername},
     * {@link #smtpPassword}
     *
     * @return configured {@link JavaMailSender} instance
     */
    private static JavaMailSender mailSender() {
        final JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtpHost);
        sender.setPort(smtpPort);
        sender.setUsername(smtpUsername);
        sender.setPassword(smtpPassword);
        return sender;
    }

    /**
     * Retrieves {@code {@link #smtpPort}}
     *
     * @return value of {@link #smtpPort}
     */
    public static int getSmtpPort() {
        return smtpPort;
    }

    /**
     * Retrieves {@code {@link #smtpHost}}
     *
     * @return value of {@link #smtpHost}
     */
    public static String getSmtpHost() {
        return smtpHost;
    }

    /**
     * Retrieves {@code {@link #smtpUsername}}
     *
     * @return value of {@link #smtpUsername}
     */
    public static String getSmtpUsername() {
        return smtpUsername;
    }

    /**
     * Retrieves {@code {@link #smtpPassword}}
     *
     * @return value of {@link #smtpPassword}
     */
    public static String getSmtpPassword() {
        return smtpPassword;
    }

    /**
     * Retrieves {@code {@link #smtpSender}}
     *
     * @return value of {@link #smtpSender}
     */
    public static String getSmtpSender() {
        return smtpSender;
    }

    /**
     * Retrieves {@code {@link #emailReportsRecipients}}
     *
     * @return value of {@link #emailReportsRecipients}
     */
    public String getEmailReportsRecipients() {
        return emailReportsRecipients;
    }

    /**
     * Sets {@code emailReportsRecipients}
     *
     * @param emailReportsRecipients the {@code java.lang.String} field
     */
    public void setEmailReportsRecipients(String emailReportsRecipients) {
        this.emailReportsRecipients = emailReportsRecipients;
    }

    /**
     * Retrieves {@code {@link #displayConferencesStatistics}}
     *
     * @return value of {@link #displayConferencesStatistics}
     */
    public boolean isDisplayConferencesStatistics() {
        return displayConferencesStatistics;
    }

    /**
     * Sets {@code displayConferencesStatistics}
     *
     * @param displayConferencesStatistics the {@code boolean} field
     */
    public void setDisplayConferencesStatistics(boolean displayConferencesStatistics) {
        this.displayConferencesStatistics = displayConferencesStatistics;
    }

    /**
     * Retrieves {@code {@link #displayParticipantsStatistics}}
     *
     * @return value of {@link #displayParticipantsStatistics}
     */
    public boolean isDisplayParticipantsStatistics() {
        return displayParticipantsStatistics;
    }

    /**
     * Sets {@code displayParticipantsStatistics}
     *
     * @param displayParticipantsStatistics the {@code boolean} field
     */
    public void setDisplayParticipantsStatistics(boolean displayParticipantsStatistics) {
        this.displayParticipantsStatistics = displayParticipantsStatistics;
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        String property = controllableProperty.getProperty();
        String value = String.valueOf(controllableProperty.getValue());

        if(property.equals("Export#LicensingReport")) {
            JsonNode response = doGet(LICENSING_URI, JsonNode.class);
            ArrayNode licensingData = (ArrayNode) response.get(OBJECTS);
            if(licensingData != null && !licensingData.isEmpty()) {
                /* After 5.2 will be changed to Map<String, String> report = new HashMap<>(); */
                AggregatedDevice report = new AggregatedDevice();
                aggregatedDeviceProcessor.applyProperties(report, licensingData.get(0), "NodeLicensingReport");
                sendReportsEmail("licensing_report", report.getProperties());
            } else {
                throw new RuntimeException("Empty licensing data response, unable to compose a licensing report");
            }
        } else if (property.equals("Export#HistoricalReport")) {
            retrieveHistoricalInfo();
        } else if (property.equals("Export#DaysBack")) {
            daysBackReports = Integer.parseInt(value);
        } else if (property.endsWith("Disconnect") || property.startsWith("Export") || property.endsWith("ExportParticipants")) {
            String key = property.substring(property.indexOf(":") + 1, property.indexOf("#"));
            if(property.startsWith("Conference") && property.endsWith("Disconnect")) {
                disconnectConference(knownConferences.get(key));
            } else if (property.startsWith("Participant")) {
                disconnectParticipant(knownParticipants.get(key));
            } else if(property.endsWith("Export#TotalStatistics")) {
                sendReportsEmail("avg_monthly", buildMajorNodeReport());
            } else if(property.endsWith("ExportParticipants")) {
                sendReportsEmail(Collections.singletonList(new ReportWrapper(("participants_" + LocalDateTime.now()).replaceAll(":", "-"), retrieveParticipants(key))));
            }
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        if (CollectionUtils.isEmpty(list)) {
            throw new IllegalArgumentException("Controllable properties cannot be null or empty");
        }

        for (ControllableProperty controllableProperty : list) {
            controlProperty(controllableProperty);
        }
    }

    /**
     * Disconnect participant from the node by {@code participantId}
     *
     * @param participantId id of a participant, retrieved from the node
     * @throws Exception if any error occurs
     */
    private void disconnectParticipant(String participantId) throws Exception {
        Map<String, String> command = new HashMap<>();
        command.put("participant_id", participantId);
        doPost(COMMAND_DISCONNECT_PARTICIPANT, command);
    }

    /**
     * Disconnect conference from the node by {@code conferenceId}
     *
     * @param conferenceId id of a conference, retrieved from the node
     * @throws Exception if any error occurs
     */
    private void disconnectConference(String conferenceId) throws Exception {
        Map<String, String> command = new HashMap<>();
        command.put("conference_id", conferenceId);
        doPost(COMMAND_DISCONNECT_CONFERENCE, command);
    }

    @Override
    protected void authenticate() throws Exception {
    }

    /**
     * Build node report, containing average stats for conferences and participants for the last 2 months (and in comparison)
     *
     * @return Map of values
     * @throws Exception if any error occurs
     */
    private Map<String, String> buildMajorNodeReport() throws Exception {
        Map<String, String> reportData = new HashMap<>();

        LocalDateTime currentDayStart = LocalDateTime.now().withHour(0).withMinute(0);
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);

        LocalDate previousMonthStart;
        if(currentMonthStart.getMonthValue() == 1) {
            previousMonthStart = LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(1);
        } else {
            previousMonthStart = LocalDate.now().withMonth(currentMonthStart.getMonthValue() - 1).withDayOfMonth(1);
        }

        LocalDateTime currentDateTime = LocalDateTime.now();

//        JsonNode participantsDaily = doGet(String.format(HISTORICAL_PARTICIPANTS_URI, 5000, currentDayStart, currentDateTime), JsonNode.class).get("objects");
//        JsonNode participantsMonthly = doGet(String.format(HISTORICAL_PARTICIPANTS_URI, 5000, currentMonthStart, currentDateTime), JsonNode.class).get("objects");
//        JsonNode participantsLastMonth = doGet(String.format(HISTORICAL_PARTICIPANTS_URI, 5000, previousMonthStart, currentMonthStart), JsonNode.class).get("objects");
        List<Conference> conferencesDaily = doGet(String.format(HISTORICAL_CONFERENCE_URI, RESPONSE_LIMIT, currentDayStart, currentDateTime),
                new ParameterizedTypeReference<ManagementNodeResponse<Conference>>() {}).getObjects();

        List<Conference> conferencesMonthly = doGet(String.format(HISTORICAL_CONFERENCE_URI, RESPONSE_LIMIT, currentMonthStart, currentDateTime),
                new ParameterizedTypeReference<ManagementNodeResponse<Conference>>() {}).getObjects();

        List<Conference> conferencesLastMonth = doGet(String.format(HISTORICAL_CONFERENCE_URI, RESPONSE_LIMIT, previousMonthStart, currentMonthStart),
                new ParameterizedTypeReference<ManagementNodeResponse<Conference>>() {}).getObjects();

        AtomicInteger totalDailyDuration = new AtomicInteger(0);
        AtomicInteger totalMonthlyDuration = new AtomicInteger(0);
        AtomicInteger totalPreviousMonthDuration = new AtomicInteger(0);
        AtomicInteger participantsDailyCount = new AtomicInteger(0);
        AtomicInteger participantsMonthlyCount = new AtomicInteger(0);
        AtomicInteger participantsPreviousMonthCount = new AtomicInteger(0);

        int conferencesDailyCount = conferencesDaily.size();
        int conferencesMonthlyCount = conferencesMonthly.size();
        int conferencesPreviousMonthCount = conferencesLastMonth.size();

        conferencesDaily.forEach(node -> {
            totalDailyDuration.addAndGet(node.getDuration());
            participantsDailyCount.addAndGet(node.getParticipantCount());
        });

        conferencesMonthly.forEach(node -> {
            totalMonthlyDuration.addAndGet(node.getDuration());
            participantsMonthlyCount.addAndGet(node.getParticipantCount());
        });

        conferencesLastMonth.forEach(node -> {
            totalPreviousMonthDuration.addAndGet(node.getDuration());
            participantsPreviousMonthCount.addAndGet(node.getParticipantCount());
        });

        Integer averageDurationDaily = safeDivision(totalDailyDuration.get(),conferencesDailyCount);
        Integer averageDurationMonthly = safeDivision(totalMonthlyDuration.get(),conferencesMonthlyCount);
        Integer averageDurationPreviousMonth = safeDivision(totalPreviousMonthDuration.get(),conferencesPreviousMonthCount);

        Integer averageParticipantsDaily = safeDivision(participantsDailyCount.get(),conferencesDailyCount);
        Integer averageParticipantsMonthly = safeDivision(participantsMonthlyCount.get(),conferencesMonthlyCount);
        Integer averageParticipantsLastMonth = safeDivision(participantsPreviousMonthCount.get(),conferencesPreviousMonthCount);

        reportData.put("ConferencesDaily", String.valueOf(conferencesDailyCount));
        reportData.put("ConferencesDurationDaily", String.valueOf(totalDailyDuration.get()));
        reportData.put("ParticipantsSumDaily", String.valueOf(participantsDailyCount.get()));
        reportData.put("AvgDurationDaily", String.valueOf(averageDurationDaily));
        reportData.put("AvgParticipantsDaily", String.valueOf(averageParticipantsDaily));
        reportData.put("ConferencesMonthly", String.valueOf(conferencesMonthly.size()));
        reportData.put("ConferencesPreviousMonth", String.valueOf(conferencesLastMonth.size()));
        reportData.put("ConferencesMonthlyDiff", String.valueOf(conferencesMonthly.size() - conferencesLastMonth.size()));
        reportData.put("DurationMonthly", String.valueOf(totalMonthlyDuration.get()));
        reportData.put("DurationPreviousMonth", String.valueOf(totalPreviousMonthDuration.get()));
        reportData.put("DurationMonthlyDiff", String.valueOf(totalMonthlyDuration.get() - totalPreviousMonthDuration.get()));
        reportData.put("ParticipantsMonthly", String.valueOf(participantsMonthlyCount.get()));
        reportData.put("ParticipantsPreviousMonth", String.valueOf(participantsPreviousMonthCount.get()));
        reportData.put("ParticipantsMonthlyDiff", String.valueOf(participantsMonthlyCount.get() - participantsPreviousMonthCount.get()));
        reportData.put("DurationAvgMonthly", String.valueOf(averageDurationMonthly));
        reportData.put("DurationAvgPreviousMonth", String.valueOf(averageDurationPreviousMonth));
        reportData.put("DurationAvgMonthlyDiff", String.valueOf(averageDurationMonthly - averageDurationPreviousMonth));
        reportData.put("ParticipantsAvgMonthly", String.valueOf(averageParticipantsMonthly));
        reportData.put("ParticipantsAvgPreviousMonth", String.valueOf(averageParticipantsLastMonth));
        reportData.put("ParticipantsAvgMonthlyDiff", String.valueOf(averageParticipantsMonthly - averageParticipantsLastMonth));

        return reportData;
    }

    /**
     * Make safe division operation
     *
     * @param totalNumber the total sum value
     * @param div division value
     * @return int end division result
     */
    private int safeDivision (int totalNumber, int div){
        if(div == 0 || totalNumber == 0) {
            return 0;
        }
        return totalNumber/div;
    }

    /**
     * Build and send historical report for conferences and participants over email
     *
     * @throws Exception if any error occurs
     */
    private void retrieveHistoricalInfo() throws Exception {
        List<Map<String, String>> conferencesReport = new ArrayList<>();
        List<Map<String, String>> participantsReport = new ArrayList<>();

        LocalDateTime dateFrom = LocalDateTime.now().minusDays(daysBackReports);
        LocalDateTime dateTo = LocalDateTime.now();
        JsonNode historicalConferences = doGet(String.format(HISTORICAL_CONFERENCE_URI, RESPONSE_LIMIT, dateFrom, dateTo), JsonNode.class);
        JsonNode historicalParticipants = doGet(String.format(HISTORICAL_PARTICIPANTS_URI, RESPONSE_LIMIT, dateFrom, dateTo), JsonNode.class);

        ArrayNode historicalConferenceArray = (ArrayNode) historicalConferences.get(OBJECTS);
        ArrayNode historicalParticipantArray = (ArrayNode) historicalParticipants.get(OBJECTS);


        if(historicalConferenceArray != null && !historicalConferenceArray.isEmpty()) {
            historicalConferenceArray.forEach(node -> {
                /* After 5.2 will be changed to Map<String, String> report = new HashMap<>(); */
                AggregatedDevice report = new AggregatedDevice();
                aggregatedDeviceProcessor.applyProperties(report, node, "ConferenceHistoricalReportStats");
                conferencesReport.add(report.getProperties());
            });
        }

        if(historicalParticipantArray != null && !historicalParticipantArray.isEmpty()) {
            historicalParticipantArray.forEach(node -> {
                /* After 5.2 will be changed to Map<String, String> report = new HashMap<>(); */
                AggregatedDevice report = new AggregatedDevice();
                aggregatedDeviceProcessor.applyProperties(report, node, "ParticipantHistoricalReportStats");
                participantsReport.add(report.getProperties());
            });
        }

        ReportWrapper conferencesReportWrapper = new ReportWrapper(String.format("conferences_report_%s_%s", LocalDate.now().minusDays(daysBackReports), LocalDate.now()), conferencesReport);
        ReportWrapper participantsReportWrapper = new ReportWrapper(String.format("participants_report_%s_%s", LocalDate.now().minusDays(daysBackReports), LocalDate.now()), participantsReport);

        sendReportsEmail(Arrays.asList(conferencesReportWrapper, participantsReportWrapper));
    }

    /*TODO: OTJ for V2:*/
    /*
    private Map<String, String> retrieveOneTouchJoinMeetingsPerEndpointDetails(String endpoint) throws Exception {
        JsonNode response = doGet(String.format(ONE_TOUCH_JOIN_MEETINGS_PER_ENDPOINT_URI, endpoint), JsonNode.class);
        return null;
    }

    private Map<String, String> retrieveOneTouchJoinMeetingsPerDateDetails(String date) throws Exception {
        JsonNode response = doGet(String.format(ONE_TOUCH_JOIN_MEETINGS_PER_DATE_URI, date), JsonNode.class);
        return null;
    }
    */

    /**
     * Retrieve a list of conferencing nodes attached to the management node, as Aggregated Devices
     *
     * @return {@link List} of {@link AggregatedDevice} instances, containing the data extracted from {@link #CONFERENCING_NODES_URI}
     *          using model-mapping.yml mapping
     * @throws Exception if any error occurs
     */
    private List<AggregatedDevice> retrieveConferencingNodes() throws Exception {
        JsonNode response = doGet(String.format(CONFERENCING_NODES_URI, RESPONSE_LIMIT), JsonNode.class);
        List<AggregatedDevice> devices = aggregatedDeviceProcessor.extractDevices(response);

        JsonNode conferencingNodesConfig = doGet(String.format(CONFERENCING_NODES_CONFIGURATION_URI, RESPONSE_LIMIT), JsonNode.class);
        ArrayNode conferencingNodesConfigObjects = (ArrayNode) conferencingNodesConfig.get(OBJECTS);

        if(conferencingNodesConfigObjects != null && !conferencingNodesConfigObjects.isEmpty()) {
            Map<String, Map<String, String>> configurations = new HashMap<>();
            conferencingNodesConfigObjects.forEach(node -> {
                /* After 5.2 will be changed to Map<String, String> conferenceNodesConfig = new HashMap<>(); */
                AggregatedDevice conferenceNodesConfig = new AggregatedDevice();
                aggregatedDeviceProcessor.applyProperties(conferenceNodesConfig, node, "ConferencingNodesConfig");
                configurations.put(conferenceNodesConfig.getProperties().get("Configuration#Name"), conferenceNodesConfig.getProperties());
            });

            devices.forEach(aggregatedDevice -> aggregatedDevice.getProperties().putAll(configurations.get(aggregatedDevice.getDeviceName())));
        }

        devices.forEach(aggregatedDevice -> {
            List<Statistics> statistics = new ArrayList<>();
            ExtendedStatistics extendedStatistics = new ExtendedStatistics();
            statistics.add(extendedStatistics);
            extendedStatistics.setStatistics(aggregatedDevice.getProperties());
            //extendedStatistics.setDynamicStatistics(aggregatedDevice.getDynamicStatistics());
            aggregatedDevice.setMonitoredStatistics(statistics);
        });
        return devices;
    }

    /**
     * Retrieve conferences status as {@link List} of {@link Map}, map instance per conference.
     * Values are extracted using model-mapping.yml, as AggregatedDevice instances (for v1, targeted to SY 5.1),
     * v2 will have data exported as {@link Map} (targeted to SY v5.2)
     *
     * @return {@link List} of {@link Map} containing {@link String} key:value pairs, representing conferences statuses
     * @throws Exception if any error occurs
     */
    private List<Map<String, String>> retrieveConferencesStatus() throws Exception {
        List<Map<String, String>> conferences = new ArrayList<>();
        JsonNode conferencesResponse = doGet(String.format(CONFERENCE_URI, RESPONSE_LIMIT), JsonNode.class);
        JsonNode conferenceShards = doGet(String.format(CONFERENCE_SHARD_URI, RESPONSE_LIMIT), JsonNode.class);

        ArrayNode conferenceShardObjects = (ArrayNode) conferenceShards.get(OBJECTS);
        ArrayNode conferenceObjects = (ArrayNode) conferencesResponse.get(OBJECTS);

        if(conferenceShardObjects != null && conferenceObjects != null &&
                !conferenceShardObjects.isEmpty() && !conferenceObjects.isEmpty()) {
            conferenceObjects.forEach(node -> {
                /* After 5.2 will be changed to Map<String, String> conferenceStatus = new HashMap<>(); */
                AggregatedDevice report = new AggregatedDevice();
                aggregatedDeviceProcessor.applyProperties(report, node, "ConferenceStatus");
                conferenceShardObjects.forEach(shard -> {
                    if (shard.get("id").asText().equals(report.getProperties().get("ID"))) {
                        /* After 5.2 will be changed to Map<String, String> status = new HashMap<>(); */
                        AggregatedDevice status = new AggregatedDevice();
                        aggregatedDeviceProcessor.applyProperties(status, shard, "ConferenceShard");
                        report.getProperties().putAll(status.getProperties());
                    }
                });
                conferences.add(report.getProperties());
            });
        }
        return conferences;
    }

    /**
     * Retrieve participants status as {@link List} of {@link Map}, map instance per participant.
     * Values are extracted using model-mapping.yml, as AggregatedDevice instances (for v1, targeted to SY 5.1),
     * v2 will have data exported as {@link Map} (targeted to SY v5.2)
     *
     * @param conferenceId to lookup participants for a specific conference
     * @return {@link List} of {@link Map} containing {@link String} key:value pairs, representing participants statuses
     * @throws Exception if any error occurs
     */
    private List<Map<String, String>> retrieveParticipants(String conferenceId) throws Exception {
        JsonNode response = doGet(String.format(CONFERENCE_PARTICIPANTS_URI, 5000, conferenceId), JsonNode.class);
        return extractParticipants(response);
    }

    /**
     * Retrieve participants status as {@link List} of {@link Map}, map instance per participant.
     * Values are extracted using model-mapping.yml, as AggregatedDevice instances (for v1, targeted to SY 5.1),
     * v2 will have data exported as {@link Map} (targeted to SY v5.2)
     *
     * @return {@link List} of {@link Map} containing {@link String} key:value pairs, representing participants statuses
     * @throws Exception if any error occurs
     */
    private List<Map<String, String>> retrieveParticipants() throws Exception {
        JsonNode response = doGet(String.format(PARTICIPANTS_URI, 5000), JsonNode.class);
        return extractParticipants(response);
    }

    /**
     * Extract participants as {@link List} of {@link Map}, map instance per participant, from {@link JsonNode} API response.
     * Values are extracted using model-mapping.yml, as AggregatedDevice instances (for v1, targeted to SY 5.1),
     * v2 will have data exported as {@link Map} (targeted to SY v5.2)
     *
     * @return {@link List} of {@link Map} containing {@link String} key:value pairs, representing participants statuses
     * @throws Exception if any error occurs
     */
    private List<Map<String, String>> extractParticipants(JsonNode response) throws Exception {
        List<Map<String, String>> participants = new ArrayList<>();
        ArrayNode participantObjects = (ArrayNode) response.get(OBJECTS);

        if(participantObjects != null && !participantObjects.isEmpty()) {
            participantObjects.forEach(node -> {
                /* After 5.2 will be changed to Map<String, String> participantReport = new HashMap<>(); */
                AggregatedDevice participantReport = new AggregatedDevice();
                aggregatedDeviceProcessor.applyProperties(participantReport, node, "Participant");
                participants.add(participantReport.getProperties());
            });
        }
        return participants;
    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
        List<Map<String, String>> conferences = retrieveConferencesStatus();
        List<Map<String, String>> participants = retrieveParticipants();
        List<AggregatedDevice> conferencingNodes = retrieveConferencingNodes();

        knownConferences.clear();
        knownParticipants.clear();

        if(!displayParticipantsStatistics && !displayConferencesStatistics) {
            return conferencingNodes;
        }
        conferencingNodes.forEach(aggregatedDevice -> {
            if(displayConferencesStatistics) {
                conferences.stream().filter(map -> map.get("NodeAddress")
                        .equals(aggregatedDevice.getProperties().get("Configuration#NodeAddress"))).forEach(map -> {
                    String groupPrefix = map.get("Name");
                    map.keySet().forEach(s -> aggregatedDevice.getProperties().put("Conference:" + groupPrefix + "#" + s, map.get(s)));
                    knownConferences.put(groupPrefix, map.get("ID"));
                    aggregatedDevice.getProperties().put("Conference:" + groupPrefix + "#" + "ParticipantsCount",
                            String.valueOf(participants.stream().filter(data -> data.get("Conference").equals(groupPrefix)).count()));

                    aggregatedDevice.getProperties().put("Conference:" + groupPrefix + "#" + "ExportParticipants", "");
                    aggregatedDevice.getControllableProperties().add(createButton("Conference:" + groupPrefix + "#" + "ExportParticipants",
                            "Export", "Exporting", 0L));
                    aggregatedDevice.getProperties().put("Conference:" + groupPrefix + "#" + "Disconnect", "");
                    aggregatedDevice.getControllableProperties().add(createButton("Conference:" + groupPrefix + "#" + "Disconnect",
                            "Disconnect", "Disconnecting", 0L));
                });
            }

//            if(displayParticipantsStatistics) {
//                participants.stream().filter(stub -> stub.getStaticData().get("MediaNode")
//                        .equals(aggregatedDevice.getProperties().get("Configuration#NodeAddress"))).forEach(stub -> {
//                    String groupPrefix = stub.getStaticData().get("DisplayName");
//                    stub.getStaticData().keySet().forEach(s -> aggregatedDevice.getProperties().put("Participant:" + groupPrefix + "#" + s, stub.getStaticData().get(s)));
//                    //stub.getHistoricalData().keySet().forEach(s -> aggregatedDevice.getDynamicStatistics().put("Participant:" + groupPrefix + "#" + s, stub.getHistoricalData().get(s)));
//
//                    aggregatedDevice.getProperties().put("Participant:" + groupPrefix + "#" + "Disconnect", "");
//                    aggregatedDevice.getControllableProperties().add(createButton("Participant:" + groupPrefix + "#" + "Disconnect",
//                            "Disconnect", "Disconnecting", 0L));
//
//                    knownParticipants.put(groupPrefix, stub.getStaticData().get("ID"));
//                });
//            }
        });
        return conferencingNodes;
    }

    /**
     * Instantiate a {@link AdvancedControllableProperty.Numeric} controllable property
     *
     * @param name of the controllable property
     * @param value of the controllable property
     * @return {@link AdvancedControllableProperty} with {@code name}, {@link Date},
     *         {@link AdvancedControllableProperty.Numeric} type and {@code value} specified
     */
    private AdvancedControllableProperty createNumber(String name, int value){
        AdvancedControllableProperty.Numeric numeric = new AdvancedControllableProperty.Numeric();
        return new AdvancedControllableProperty(name, new Date(), numeric, value);
    }

    /**
     * Instantiate a {@link AdvancedControllableProperty.Button} controllable property
     *
     * @param name of the controllable property
     * @param gracePeriod to pause a device for after button is being pressed
     * @param label default button label
     * @param labelPressed button label after being pressed
     * @return {@link AdvancedControllableProperty} instance with {@code name}, {@link Date},
     *         {@link AdvancedControllableProperty.Button} type and {@code value} (default - "" for button) specified
     */
    private AdvancedControllableProperty createButton(String name, String label, String labelPressed, long gracePeriod) {
        AdvancedControllableProperty.Button button = new AdvancedControllableProperty.Button();
        button.setLabel(label);
        button.setLabelPressed(labelPressed);
        button.setGracePeriod(gracePeriod);

        return new AdvancedControllableProperty(name, new Date(), button, "");
    }

    /**
     * Send csv files over email to {@link #emailReportsRecipients}
     *
     * @param reports data to present as csv files and send over email
     * @throws MessagingException if {@link #mailSender} is misconfigured or not able to access SMTP server
     * @throws IOException if any csv file related error occurs (no space left, unable to create/remove file, etc)
     */
    private void sendReportsEmail (List<ReportWrapper> reports) throws MessagingException, IOException {
        List<File> files = new ArrayList<>();
        try {
            MimeMessageHelper helper = prepareMimeMessageHelper("Reports");

            for (ReportWrapper reportWrapper : reports) {
                String reportFileName = reportWrapper.getReportName() + ".csv";
                File file = new File(reportFileName);
                files.add(file);
                BufferedWriter writer = new BufferedWriter(new FileWriter(reportFileName));

                List<Map<String, String>> reportsList = reportWrapper.getReport();

                Optional<Map<String, String>> map = reportsList.stream().max(Comparator.comparing(Map::size));
                String keys = "";
                if (map.isPresent()) {
                    keys = String.join(",", map.get().keySet());
                    writer.append(keys);
                }

                for (Map<String, String> stringStringMap : reportsList) {
                    writer.newLine();

                    Arrays.stream(keys.split(",")).forEach(s -> {
                        if (!stringStringMap.containsKey(s)) {
                            stringStringMap.put(s, "-");
                        }
                    });
                    writer.append(String.join(",", stringStringMap.values()));
                }
                writer.close();
                helper.addAttachment(file.getName(), file);
            }

            mailSender.send(helper.getMimeMessage());
        } finally {
            files.forEach(File::deleteOnExit);
        }
    }

    /**
     * Uptime is received in seconds, need to normalize it and make it human readable, like
     * 1 day(s) 5 hour(s) 12 minute(s) 55 minute(s)
     * Incoming parameter is may have a decimal point, so in order to safely process this - it's rounded first.
     * We don't need to add a segment of time if it's 0.
     *
     * @param uptimeSeconds value in seconds
     * @return string value of format 'x day(s) x hour(s) x minute(s) x minute(s)'
     */
    private String normalizeUptime(long uptimeSeconds) {
        StringBuilder normalizedUptime = new StringBuilder();

        long seconds = uptimeSeconds % 60;
        long minutes = uptimeSeconds % 3600 / 60;
        long hours = uptimeSeconds % 86400 / 3600;
        long days = uptimeSeconds / 86400;

        if (days > 0) {
            normalizedUptime.append(days).append(" day(s) ");
        }
        if (hours > 0) {
            normalizedUptime.append(hours).append(" hour(s) ");
        }
        if (minutes > 0) {
            normalizedUptime.append(minutes).append(" minute(s) ");
        }
        if (seconds > 0) {
            normalizedUptime.append(seconds).append(" second(s)");
        }
        return normalizedUptime.toString().trim();
    }

    /**
     * Send csv files over email to {@link #emailReportsRecipients}
     *
     * @param name name of the reports file to use
     * @param report data to present as csv files and send over email
     * @throws MessagingException if {@link #mailSender} is misconfigured or not able to access SMTP server
     * @throws IOException if any csv file related error occurs (no space left, unable to create/remove file, etc)
     */
    private void sendReportsEmail (String name, Map<String, String> report) throws MessagingException, IOException {
        MimeMessageHelper helper = prepareMimeMessageHelper("Reports");
        File file = null;
        try {
            file = new File(name + ".csv");
            BufferedWriter writer = new BufferedWriter(new FileWriter(name + ".csv"));

            String keys = String.join(",", report.keySet());
            String values = String.join(",", report.values());
            writer.write(keys + "\n" + values);
            writer.close();
            helper.addAttachment(file.getName(), file);
            mailSender.send(helper.getMimeMessage());
        } finally {
            if(file != null) {
                file.deleteOnExit();
            }
        }
    }

    /**
     * Prepare generic {@link MimeMessageHelper} for future use. Fill in FROM/TO, {@code subject}, message type and
     * bodytext.
     *
     * @param subject of a message
     * @return configured {@link MimeMessageHelper} instance
     * @throws MessagingException if any error occurs during configuration
     * @throws IllegalStateException if no recipients set in {@link #emailReportsRecipients} variable
     */
    private MimeMessageHelper prepareMimeMessageHelper(String subject) throws MessagingException {
            if(StringUtils.isNullOrEmpty(emailReportsRecipients)) {
                throw new IllegalStateException("No email recipients specified. Please set emailReportsRecipients csv property");
            }
            String[] emailRecipients = emailReportsRecipients.split(",");

            MimeMessage message = mailSender.createMimeMessage();
            SimpleMailMessage mailMessage = new SimpleMailMessage();

            mailMessage.setFrom(smtpSender);
            mailMessage.setTo(emailRecipients);
            mailMessage.setSubject(subject);
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(smtpSender);
            helper.setTo(emailRecipients);
            helper.setSubject(subject);
            helper.setText("Please see reports in attachments");

            return helper;
    }


    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
        return retrieveMultipleStatistics().stream().filter(s ->
                list.contains(s.getDeviceId())).collect(Collectors.toList());
    }

    @Override
    protected RestTemplate obtainRestTemplate() throws Exception {
        RestTemplate restTemplate = super.obtainRestTemplate();

        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));
        messageConverters.add(converter);
        restTemplate.setMessageConverters(messageConverters);

        return restTemplate;
    }
}
