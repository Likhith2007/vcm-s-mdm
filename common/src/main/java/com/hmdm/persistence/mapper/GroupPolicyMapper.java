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

package com.hmdm.persistence.mapper;

import com.hmdm.persistence.domain.GroupPolicy;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>MyBatis mapper for {@link GroupPolicy} — the app-restriction policy attached to a device
 * Group (Group Policies feature).</p>
 */
public interface GroupPolicyMapper {

    @Insert({"INSERT INTO groupPolicy (groupId, policyType, packages, startTime, endTime, blockedNow, updatedAt) " +
            "VALUES (#{groupId}, #{policyType}, #{packages}, #{startTime}, #{endTime}, " +
            "COALESCE(#{blockedNow}, FALSE), #{updatedAt}) " +
            "ON CONFLICT (groupId) DO UPDATE SET " +
            "policyType = EXCLUDED.policyType, packages = EXCLUDED.packages, " +
            "startTime = EXCLUDED.startTime, endTime = EXCLUDED.endTime, updatedAt = EXCLUDED.updatedAt"})
    void upsert(GroupPolicy policy);

    @Select({"SELECT * FROM groupPolicy WHERE groupId = #{groupId}"})
    GroupPolicy findByGroupId(@Param("groupId") Integer groupId);

    @Select({"SELECT * FROM groupPolicy"})
    List<GroupPolicy> listAll();

    @Update({"UPDATE groupPolicy SET blockedNow = #{blockedNow}, updatedAt = #{updatedAt} WHERE groupId = #{groupId}"})
    void setBlockedNow(@Param("groupId") Integer groupId, @Param("blockedNow") boolean blockedNow,
                       @Param("updatedAt") long updatedAt);

    @Delete({"DELETE FROM groupPolicy WHERE groupId = #{groupId}"})
    void deleteByGroupId(@Param("groupId") Integer groupId);
}
