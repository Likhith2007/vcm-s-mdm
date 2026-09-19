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
import com.hmdm.persistence.domain.AgentAppPendingInstall;
import com.hmdm.persistence.domain.AgentCommand;
import com.hmdm.persistence.domain.DeviceEvent;
import com.hmdm.persistence.domain.DeviceState;
import com.hmdm.persistence.mapper.AgentAppPendingInstallMapper;
import com.hmdm.persistence.mapper.AgentCommandMapper;
import com.hmdm.persistence.mapper.AgentDeviceMapper;
import com.hmdm.persistence.mapper.DeviceEventMapper;
import com.hmdm.persistence.mapper.DeviceStateMapper;

import java.util.List;

/**
 * <p>DAO for the opaque agent v1 command queue and the device capability column. The server never
 * interprets command {@code type}/{@code payload}; this DAO only stores and forwards them.</p>
 */
@Singleton
public class AgentCommandDAO {

    private final AgentCommandMapper mapper;
    private final AgentDeviceMapper deviceMapper;
    private final DeviceStateMapper stateMapper;
    private final DeviceEventMapper eventMapper;
    private final AgentAppPendingInstallMapper pendingInstallMapper;

    @Inject
    public AgentCommandDAO(AgentCommandMapper mapper, AgentDeviceMapper deviceMapper,
                          DeviceStateMapper stateMapper, DeviceEventMapper eventMapper,
                          AgentAppPendingInstallMapper pendingInstallMapper) {
        this.mapper = mapper;
        this.deviceMapper = deviceMapper;
        this.stateMapper = stateMapper;
        this.eventMapper = eventMapper;
        this.pendingInstallMapper = pendingInstallMapper;
    }

    public void insert(AgentCommand command) {
        mapper.insert(command);
    }

    public List<AgentCommand> listPending(String deviceNumber) {
        return mapper.listPending(deviceNumber);
    }

    /**
     * Record a result status + detail + the completion timestamp (epoch millis). Ownership-scoped:
     * the row is only touched when it belongs to {@code deviceNumber}, and a genuinely terminal
     * done/failed/unsupported is never overwritten (device-reported results DO overwrite 'expired').
     */
    public void markResultWithTime(String deviceNumber, Integer commandId, String status,
                                   String detail, Long completedAt) {
        mapper.markResultWithTime(deviceNumber, commandId, status, detail, completedAt);
    }

    /** Atomically claim a pending command for delivery. Returns true iff this caller claimed it. */
    public boolean claimForDelivery(Integer commandId, Long deliveredAt) {
        return mapper.claimForDelivery(commandId, deliveredAt) == 1;
    }

    /**
     * Lazy TTL expiry. Pending commands age out {@code pendingTtlMillis} after creation;
     * delivered ones get their own {@code deliveredTtlMillis} leash from delivery time (the
     * device already holds them — see the mapper note).
     */
    public void expireStale(String deviceNumber, long pendingTtlMillis, long deliveredTtlMillis) {
        long now = System.currentTimeMillis();
        mapper.expireStale(deviceNumber, now - pendingTtlMillis, now - deliveredTtlMillis, now);
    }

    /** Command lifecycle history for a device, newest first, created at/after {@code since}. */
    public List<AgentCommand> listHistory(String deviceNumber, long since, int limit) {
        return mapper.listHistory(deviceNumber, since, limit);
    }

    /** Upsert the latest device-state snapshot. */
    public void upsertState(DeviceState state) {
        stateMapper.upsert(state);
    }

    /** Current device-state snapshot, or null if none reported yet. */
    public DeviceState getState(String deviceNumber) {
        return stateMapper.findByDeviceNumber(deviceNumber);
    }

    /** Insert one agent lifecycle event. */
    public void insertEvent(String deviceNumber, String type, Long ts, String detail) {
        DeviceEvent e = new DeviceEvent();
        e.setDeviceNumber(deviceNumber);
        e.setType(type);
        e.setTs(ts);
        e.setDetail(detail);
        eventMapper.insert(e);
    }

    /** Event timeline for a device, newest first, at/after {@code since}. */
    public List<DeviceEvent> listEvents(String deviceNumber, long since, int limit) {
        return eventMapper.list(deviceNumber, since, limit);
    }

    public AgentCommand findByDeviceAndId(String deviceNumber, Integer commandId) {
        return mapper.findByDeviceAndId(deviceNumber, commandId);
    }

    public void updateDeviceCapabilities(String deviceNumber, String capabilitiesJson) {
        deviceMapper.updateDeviceCapabilities(deviceNumber, capabilitiesJson);
    }

    public String getDeviceCapabilities(String deviceNumber) {
        return deviceMapper.getDeviceCapabilities(deviceNumber);
    }

    public void updateDeviceSecretHash(String deviceNumber, String secretHash) {
        deviceMapper.updateDeviceSecretHash(deviceNumber, secretHash);
    }

    public void updateHardwareId(String deviceNumber, String hardwareId) {
        deviceMapper.updateHardwareId(deviceNumber, hardwareId);
    }

    public String getDeviceSecretHash(String deviceNumber) {
        return deviceMapper.getDeviceSecretHash(deviceNumber);
    }

    /** Stamp the device's last-seen time (drives the admin UI's online/offline state). */
    public void touchLastUpdate(String deviceNumber) {
        deviceMapper.updateLastUpdate(deviceNumber, System.currentTimeMillis());
    }

    /** Persist the reported Android version into infojson so the device list can show it. */
    public void updateAndroidVersion(String deviceNumber, String androidVersion) {
        deviceMapper.updateAndroidVersion(deviceNumber, androidVersion);
    }

    /**
     * Append a location fix to the device's trail, skipping it if it isn't newer than the last
     * stored fix — passive last-known reporting returns the same fix until the OS refreshes it, so
     * de-duping on capturedAt keeps the trail meaningful without a distance calc. The dedupe lives
     * inside the INSERT itself (single statement), so concurrent check-ins can't double-insert.
     */
    public void recordLocation(com.hmdm.persistence.domain.DeviceLocation location) {
        location.setRecordedAt(System.currentTimeMillis());
        deviceMapper.insertLocation(location);
    }

    public java.util.List<com.hmdm.persistence.domain.DeviceLocation> listLocations(
            String deviceNumber, long since, int limit) {
        return deviceMapper.listLocations(deviceNumber, since, limit);
    }

    /** Upsert a pending app-install approval; a no-op if one is already open for this device+package. */
    public void insertPendingApprovalIfNotOpen(String deviceNumber, String packageName, long createdAt) {
        pendingInstallMapper.insertIfNotOpen(deviceNumber, packageName, createdAt);
    }

    /** Pending approvals across all devices, newest first, capped at {@code limit}. */
    public List<AgentAppPendingInstall> listPendingApprovals(int limit) {
        return pendingInstallMapper.listPending(limit);
    }

    /** Pending approvals for one device, newest first. */
    public List<AgentAppPendingInstall> listPendingApprovalsForDevice(String deviceNumber) {
        return pendingInstallMapper.listPendingForDevice(deviceNumber);
    }

    public AgentAppPendingInstall findPendingApprovalById(Integer id) {
        return pendingInstallMapper.findById(id);
    }

    /** Atomically resolve a pending approval. Returns true iff this caller resolved it (false = already resolved). */
    public boolean resolvePendingApproval(Integer id, String status, Long resolvedAt, Integer commandId) {
        return pendingInstallMapper.resolve(id, status, resolvedAt, commandId) == 1;
    }
}
