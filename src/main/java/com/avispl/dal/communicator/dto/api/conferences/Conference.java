/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.dal.communicator.dto.api.conferences;

import com.avispl.dal.communicator.dto.api.entities.BaseResponseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A basic representation of a Conference entity from Pexip Management Node
 *
 * @author Maksym.Rossiytsev / Symphony Dev Team<br>
 * @since 1.0
 * Created June 1, 2021
 */
public class Conference extends BaseResponseEntity {
    @JsonProperty("duration")
    private int duration;
    @JsonProperty("participant_count")
    private int participantCount;

    /**
     * Retrieves {@code {@link #duration}}
     *
     * @return value of {@link #duration}
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Sets {@code duration}
     *
     * @param duration the {@code int} field
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * Retrieves {@code {@link #participantCount}}
     *
     * @return value of {@link #participantCount}
     */
    public int getParticipantCount() {
        return participantCount;
    }

    /**
     * Sets {@code participantCount}
     *
     * @param participantCount the {@code int} field
     */
    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }
}
