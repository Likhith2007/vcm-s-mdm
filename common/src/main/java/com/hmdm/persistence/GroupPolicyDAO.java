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

package com.hmdm.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.domain.GroupPolicy;
import com.hmdm.persistence.mapper.GroupPolicyMapper;

import java.util.List;

/**
 * <p>DAO for {@link GroupPolicy} — the app-restriction policy attached to a device Group (Group
 * Policies feature).</p>
 */
@Singleton
public class GroupPolicyDAO {

    private final GroupPolicyMapper mapper;

    @Inject
    public GroupPolicyDAO(GroupPolicyMapper mapper) {
        this.mapper = mapper;
    }

    public void upsert(GroupPolicy policy) {
        mapper.upsert(policy);
    }

    public GroupPolicy findByGroupId(Integer groupId) {
        return mapper.findByGroupId(groupId);
    }

    /** Every group's policy — used by GroupPolicyScheduler's tick. */
    public List<GroupPolicy> listAll() {
        return mapper.listAll();
    }

    public void setBlockedNow(Integer groupId, boolean blockedNow, long updatedAt) {
        mapper.setBlockedNow(groupId, blockedNow, updatedAt);
    }

    public void deleteByGroupId(Integer groupId) {
        mapper.deleteByGroupId(groupId);
    }
}
