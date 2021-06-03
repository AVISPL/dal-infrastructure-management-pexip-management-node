package com.avispl.dal.communicator.pexip;

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import org.junit.Before;
import org.junit.Test;

public class PexipManagementNodeTest {
    private PexipManagementNode pexipManagementNode;

    @Before
    public void setUp() throws Exception {
        pexipManagementNode = new PexipManagementNode();
        pexipManagementNode.setHost("***REMOVED***");
        pexipManagementNode.setPort(443);
        pexipManagementNode.setLogin("admin");
        pexipManagementNode.setPassword("***REMOVED***");
        pexipManagementNode.init();
    }

    @Test
    public void testRetrieveMultipleStatistics() throws Exception {
        pexipManagementNode.setDisplayConferencesStatistics(true);
        pexipManagementNode.retrieveMultipleStatistics();
    }


    @Test
    public void testGetMultipleStatistics() throws Exception {
        pexipManagementNode.getMultipleStatistics();
    }

    @Test
    public void testSendHistoricalReports() throws Exception {
        pexipManagementNode.setEmailReportsRecipients("max@rossiytsev.com");

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
        pexipManagementNode.setEmailReportsRecipients("max@rossiytsev.com");

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
        pexipManagementNode.setEmailReportsRecipients("max@rossiytsev.com");
        ControllableProperty export = new ControllableProperty();
        export.setProperty("Conference:Symphony_625_3075558_LH Pexip MCU#ExportParticipants");
        pexipManagementNode.controlProperty(export);
    }
}
