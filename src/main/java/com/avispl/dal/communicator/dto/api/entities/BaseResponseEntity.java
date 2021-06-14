/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.dal.communicator.dto.api.entities;

/**
 * An abstract representation of a basic response entity.
 * Currently, the majority of responses are processed by {@link com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor}
 * but for certain features it is necessary to retrieve specific batches of information separately
 *
 * @author Maksym.Rossiytsev / Symphony Dev Team<br>
 * @since 1.0
 * Created June 1, 2021
 */
public abstract class BaseResponseEntity {
}
