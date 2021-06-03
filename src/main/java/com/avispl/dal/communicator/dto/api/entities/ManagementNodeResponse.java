/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.dal.communicator.dto.api.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A basic representation of a generic Management Node response.
 * Contains a list of abstract entity types and metadata
 *
 * @author Maksym.Rossiytsev / Symphony Dev Team<br>
 * @since 1.0
 * Created June 1, 2021
 */
public class ManagementNodeResponse<T extends BaseResponseEntity> {
    @JsonProperty("objects")
    private List<T> objects;

    private Meta meta;

    /**
     * Retrieves {@code {@link #meta}}
     *
     * @return value of {@link #meta}
     */
    public Meta getMeta() {
        return meta;
    }

    /**
     * Sets {@code meta}
     *
     * @param meta the {@code com.avispl.dal.communicator.dto.Meta} field
     */
    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    /**
     * Retrieves {@code {@link #objects}}
     *
     * @return value of {@link #objects}
     */
    public List<T> getObjects() {
        return objects;
    }

    /**
     * Sets {@code objects}
     *
     * @param objects the {@code java.util.List<T>} field
     */
    public void setObjects(List<T> objects) {
        this.objects = objects;
    }
}
