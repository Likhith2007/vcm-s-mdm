/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * <p>The app-restriction policy attached to a {@link Group} (Group Policies feature). One row per
 * group. {@code policyType} is {@code allowAll} (no restriction) or {@code blockScheduled} (block
 * {@code packages} — a JSON array of package names — during the daily {@code startTime}/
 * {@code endTime} window, {@code HH:mm}, which may cross midnight). {@code blockedNow} is
 * maintained only by {@code GroupPolicyScheduler} — never set directly by an admin edit.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_ALLOW_ALL = "allowAll";
    public static final String TYPE_BLOCK_SCHEDULED = "blockScheduled";

    private Integer groupId;
    private String policyType;
    private String packages;
    private String startTime;
    private String endTime;
    private Boolean blockedNow;
    private Long updatedAt;

    public GroupPolicy() {
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    public String getPackages() {
        return packages;
    }

    public void setPackages(String packages) {
        this.packages = packages;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Boolean getBlockedNow() {
        return blockedNow;
    }

    public void setBlockedNow(Boolean blockedNow) {
        this.blockedNow = blockedNow;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
