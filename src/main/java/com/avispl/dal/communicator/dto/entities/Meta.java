package com.avispl.dal.communicator.dto.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Meta {
    private Integer limit;
    private String next;
    private Integer offset;
    private String previous;
    @JsonProperty("total_count")
    private Integer totalCount;

    /**
     * Retrieves {@code {@link #limit}}
     *
     * @return value of {@link #limit}
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Sets {@code limit}
     *
     * @param limit the {@code java.lang.Integer} field
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * Retrieves {@code {@link #next}}
     *
     * @return value of {@link #next}
     */
    public String getNext() {
        return next;
    }

    /**
     * Sets {@code next}
     *
     * @param next the {@code java.lang.String} field
     */
    public void setNext(String next) {
        this.next = next;
    }

    /**
     * Retrieves {@code {@link #offset}}
     *
     * @return value of {@link #offset}
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Sets {@code offset}
     *
     * @param offset the {@code java.lang.Integer} field
     */
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    /**
     * Retrieves {@code {@link #previous}}
     *
     * @return value of {@link #previous}
     */
    public String getPrevious() {
        return previous;
    }

    /**
     * Sets {@code previous}
     *
     * @param previous the {@code java.lang.String} field
     */
    public void setPrevious(String previous) {
        this.previous = previous;
    }

    /**
     * Retrieves {@code {@link #totalCount}}
     *
     * @return value of {@link #totalCount}
     */
    public Integer getTotalCount() {
        return totalCount;
    }

    /**
     * Sets {@code totalCount}
     *
     * @param totalCount the {@code java.lang.Integer} field
     */
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
}
