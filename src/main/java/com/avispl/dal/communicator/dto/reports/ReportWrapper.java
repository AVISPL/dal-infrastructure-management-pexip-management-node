/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.dal.communicator.dto.reports;

import java.util.List;
import java.util.Map;

/**
 * Report wrapper, created for transferring multiple Map entries, related to a single report, along with the
 * report name. Created mainly for future use in csv generation process.
 *
 * @author Maksym.Rossiytsev / Symphony Dev Team<br>
 * @since 1.0
 * Created June 1, 2021
 */
public class ReportWrapper {
    private String reportName;
    private List<Map<String, String>> report;

    public ReportWrapper(String reportName, List<Map<String, String>> report) {
        this.reportName = reportName;
        this.report = report;
    }

    /**
     * Retrieves {@code {@link #reportName}}
     *
     * @return value of {@link #reportName}
     */
    public String getReportName() {
        return reportName;
    }

    /**
     * Sets {@code reportName}
     *
     * @param reportName the {@code java.lang.String} field
     */
    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    /**
     * Retrieves {@code {@link #report}}
     *
     * @return value of {@link #report}
     */
    public List<Map<String, String>> getReport() {
        return report;
    }

    /**
     * Sets {@code report}
     *
     * @param report the {@code java.util.List<java.util.Map<java.lang.String,java.lang.String>>} field
     */
    public void setReport(List<Map<String, String>> report) {
        this.report = report;
    }
}
