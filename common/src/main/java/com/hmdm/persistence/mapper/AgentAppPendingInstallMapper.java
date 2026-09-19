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

import com.hmdm.persistence.domain.AgentAppPendingInstall;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>MyBatis mapper for {@link AgentAppPendingInstall} — non-MDM-initiated installs awaiting
 * admin approval.</p>
 */
public interface AgentAppPendingInstallMapper {

    /** No-op if an open (pending) row already exists for this device+package — makes ingesting a
     *  retried/duplicate buffered event idempotent via the table's partial unique index. */
    @Insert({"INSERT INTO agentAppPendingInstall (deviceNumber, packageName, status, createdAt) " +
            "VALUES (#{deviceNumber}, #{packageName}, 'pending', #{createdAt}) " +
            "ON CONFLICT (deviceNumber, packageName) WHERE status = 'pending' DO NOTHING"})
    void insertIfNotOpen(@Param("deviceNumber") String deviceNumber,
                        @Param("packageName") String packageName,
                        @Param("createdAt") long createdAt);

    @Select({"SELECT * FROM agentAppPendingInstall WHERE status = 'pending' ORDER BY createdAt DESC LIMIT #{limit}"})
    List<AgentAppPendingInstall> listPending(@Param("limit") int limit);

    @Select({"SELECT * FROM agentAppPendingInstall WHERE deviceNumber = #{deviceNumber} AND status = 'pending' " +
            "ORDER BY createdAt DESC"})
    List<AgentAppPendingInstall> listPendingForDevice(@Param("deviceNumber") String deviceNumber);

    @Select({"SELECT * FROM agentAppPendingInstall WHERE id = #{id}"})
    AgentAppPendingInstall findById(@Param("id") Integer id);

    /**
     * Atomically resolve a still-pending row. Returns 1 if THIS caller resolved it, 0 if it was
     * already resolved (double-click, or two admins racing) — the same claim-guard idiom as
     * {@link AgentCommandMapper#claimForDelivery}.
     */
    @Update({"UPDATE agentAppPendingInstall SET status = #{status}, resolvedAt = #{resolvedAt}, commandId = #{commandId} " +
            "WHERE id = #{id} AND status = 'pending'"})
    int resolve(@Param("id") Integer id, @Param("status") String status,
               @Param("resolvedAt") Long resolvedAt, @Param("commandId") Integer commandId);
}
