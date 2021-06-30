package com.avispl.dal.communicator.pexip;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class PexipManagementNodeTest {
    private PexipManagementNode pexipManagementNode;

    @Before
    public void setUp() throws Exception {
        pexipManagementNode = new PexipManagementNode();
        pexipManagementNode.setHost("209.251.7.100");
        pexipManagementNode.setPort(443);
        pexipManagementNode.setLogin("admin");
        pexipManagementNode.setPassword("P@$$w0rd");

        pexipManagementNode.setSmtpPort(25);
        pexipManagementNode.setSmtpHost("172.31.11.188");
        pexipManagementNode.setSmtpPassword("");
        pexipManagementNode.setSmtpSender("noreply@avispl.com");
        pexipManagementNode.setSmtpUsername("");
        pexipManagementNode.setEmailReportsRecipients("max@rossiytsev.com");
        pexipManagementNode.init();
    }

    @Test
    public void testRetrieveMultipleStatistics() throws Exception {
        pexipManagementNode.setDisplayConferencesStatistics(true);
        List<AggregatedDevice> devices = pexipManagementNode.retrieveMultipleStatistics();

        Assert.assertNotNull(devices.get(0));
        Assert.assertEquals("CHI-SRV-PEXIP-CONF05", devices.get(0).getProperties().get("Configuration#Hostname"));
        Assert.assertEquals("CHI-SRV-PEXIP-CONF05", devices.get(0).getProperties().get("Configuration#Name"));
        Assert.assertEquals("vnoc1.chicago", devices.get(0).getProperties().get("Configuration#Domain"));
    }

    @Test
    public void testGetMultipleStatistics() throws Exception {
        List<Statistics> stats = pexipManagementNode.getMultipleStatistics();
        ExtendedStatistics extendedStatistics = (ExtendedStatistics) stats.get(0);
        Map<String, String> statisticsMap = extendedStatistics.getStatistics();
        Map<String, String> dynamicStatisticsMap = extendedStatistics.getDynamicStatistics();
        List<AdvancedControllableProperty> controls = extendedStatistics.getControllableProperties();

        Assert.assertEquals(4, controls.size());
        Assert.assertEquals("1.0.0-SNAPSHOT", statisticsMap.get("AdapterVersion"));
        Assert.assertEquals("", statisticsMap.get("Export#DaysBack"));
        Assert.assertEquals("", statisticsMap.get("Export#HistoricalReport"));
        Assert.assertEquals("", statisticsMap.get("Export#LicensingReport"));
        Assert.assertEquals("", statisticsMap.get("Export#TotalStatistics"));
        Assert.assertEquals("65", dynamicStatisticsMap.get("Licensing#PortTotal"));
    }

    @Test
    public void testSendHistoricalReports() throws Exception {
        ControllableProperty daysBackControl = new ControllableProperty();
        daysBackControl.setValue(10);
        daysBackControl.setProperty("Export#DaysBack");
        pexipManagementNode.controlProperty(daysBackControl);

        ControllableProperty sendReportsControl = new ControllableProperty();
        sendReportsControl.setProperty("Export#HistoricalReport");

        pexipManagementNode.controlProperty(sendReportsControl);
    }

    @Test
    public void testSendTotalStatisticsReports() throws Exception {
        ControllableProperty sendReportsControl = new ControllableProperty();
        sendReportsControl.setProperty("Export#TotalStatistics");

        pexipManagementNode.controlProperty(sendReportsControl);
    }

    @Test
    public void testDisconnectConference() throws Exception {
       // Conference:Symphony_625_3075417_LH Pexip MCU#Disconnect
       // Stuff to enrich caches and known participants/conferences maps
        pexipManagementNode.retrieveMultipleStatistics();
        pexipManagementNode.getMultipleStatistics();

        ControllableProperty disconnect = new ControllableProperty();
        disconnect.setProperty("Conference:Symphony_625_3075417_LH Pexip MCU#Disconnect");
        pexipManagementNode.controlProperty(disconnect);
    }

    @Test
    public void testExportConferenceParticipants() throws Exception {
        // Conference:Symphony_625_3075417_LH Pexip MCU#Disconnect
        // Stuff to enrich caches and known participants/conferences maps
        pexipManagementNode.retrieveMultipleStatistics();
        pexipManagementNode.getMultipleStatistics();
        ControllableProperty export = new ControllableProperty();
        export.setProperty("Conference:Symphony_625_3075558_LH Pexip MCU#ExportParticipants");
        pexipManagementNode.controlProperty(export);
    }

    public void testExportStatisticLogs() throws Exception {
        pexipManagementNode.retrieveMultipleStatistics();
        pexipManagementNode.getMultipleStatistics();
        ControllableProperty export = new ControllableProperty();
        export.setProperty("Logs#StatisticLogs");
    }
}
