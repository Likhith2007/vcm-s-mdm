package com.hmdm.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.notification.AgentWakeHub;
import com.hmdm.persistence.AgentCommandDAO;
import com.hmdm.persistence.GroupDAO;
import com.hmdm.persistence.GroupPolicyDAO;
import com.hmdm.persistence.domain.AgentCommand;
import com.hmdm.persistence.domain.GroupPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * <p>Evaluates every {@code blockScheduled} {@link GroupPolicy} once a minute and queues
 * {@code policy.apply}/{@code appBlock} commands for the group's devices whenever the computed
 * "should be blocked right now" state actually changes — not on every tick, only on a real
 * transition, so this never spams the command queue. See {@code proto/registry.md} § Command
 * types for the exact `appBlock` payload/capability contract this reuses (already built
 * agent-side; this class is the first thing to ever queue it from the server).</p>
 *
 * <p>Eager singleton (see {@code PrivateRestModule}), same pattern as {@link AgentWakeHub}: the
 * constructor starts the ticker so it's running from boot, before any device checks in.</p>
 *
 * <p>Time window evaluation uses the server's local time-of-day against {@code startTime}/
 * {@code endTime} (`HH:mm`), same as every other command/wake in this app already depends on the
 * device eventually checking in or holding an open wake socket to actually receive the queued
 * command — this scheduler only decides *when* to queue it, not when the device applies it.</p>
 */
@Singleton
public class GroupPolicyScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GroupPolicyScheduler.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TICK_SECONDS = 60L;

    /** Server-side capability token — see AgentCapabilityTokens.flatten(): every
     *  capabilities.policy[] entry is prefixed "policy." before gating delivery, so this must be
     *  "policy.appBlock", not the bare "appBlock" the agent uses for its own local map lookup
     *  (that bare key belongs in the payload's "policy" field instead — see queueAppBlock). */
    private static final String REQUIRES_CAPABILITY = "policy.appBlock";

    private final GroupPolicyDAO groupPolicyDAO;
    private final GroupDAO groupDAO;
    private final AgentCommandDAO commandDAO;
    private final AgentWakeHub wakeHub;

    @Inject
    public GroupPolicyScheduler(GroupPolicyDAO groupPolicyDAO, GroupDAO groupDAO,
                                AgentCommandDAO commandDAO, AgentWakeHub wakeHub) {
        this.groupPolicyDAO = groupPolicyDAO;
        this.groupDAO = groupDAO;
        this.commandDAO = commandDAO;
        this.wakeHub = wakeHub;

        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "group-policy-scheduler");
            t.setDaemon(true);
            return t;
        };
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(daemonFactory);
        executor.scheduleAtFixedRate(this::safeEvaluateAll, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
        logger.info("GroupPolicyScheduler started, ticking every {}s", TICK_SECONDS);
    }

    private void safeEvaluateAll() {
        try {
            evaluateAll();
        } catch (Exception e) {
            logger.error("GroupPolicyScheduler tick failed", e);
        }
    }

    void evaluateAll() {
        LocalTime now = LocalTime.now();
        for (GroupPolicy policy : groupPolicyDAO.listAll()) {
            if (!GroupPolicy.TYPE_BLOCK_SCHEDULED.equals(policy.getPolicyType())) {
                continue;
            }
            LocalTime start = parseTime(policy.getStartTime());
            LocalTime end = parseTime(policy.getEndTime());
            if (start == null || end == null) {
                continue;
            }
            boolean shouldBlock = isWithinWindow(now, start, end);
            boolean currentlyBlocked = Boolean.TRUE.equals(policy.getBlockedNow());
            if (shouldBlock == currentlyBlocked) {
                continue; // no transition — nothing to queue
            }
            applyTransition(policy, shouldBlock);
        }
    }

    /** A window is active if now falls inside [start, end); handles the common case where the
     *  window crosses midnight (e.g. 22:00-06:00, so end < start). */
    private boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return false; // a zero-length window never blocks
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private void applyTransition(GroupPolicy policy, boolean shouldBlock) {
        Integer groupId = policy.getGroupId();
        List<String> packages = readPackages(policy.getPackages());
        List<String> deviceNumbers = groupDAO.listDeviceNumbersByGroupId(groupId);

        int queued = 0;
        for (String deviceNumber : deviceNumbers) {
            for (String pkg : packages) {
                queueAppBlock(deviceNumber, pkg, shouldBlock);
                queued++;
            }
        }
        groupPolicyDAO.setBlockedNow(groupId, shouldBlock, System.currentTimeMillis());
        logger.info("Group {} policy transitioned to {}: {} command(s) queued across {} device(s), {} package(s)",
                groupId, shouldBlock ? "blocked" : "allowed", queued, deviceNumbers.size(), packages.size());
    }

    private void queueAppBlock(String deviceNumber, String packageName, boolean block) {
        AgentCommand command = new AgentCommand();
        command.setDeviceNumber(deviceNumber);
        command.setType("policy.apply");
        command.setRequiresCapability(REQUIRES_CAPABILITY);
        command.setPayload(buildPayload(packageName, block));
        command.setStatus("pending");
        command.setCreatedAt(System.currentTimeMillis());
        commandDAO.insert(command);
        wakeHub.wake(deviceNumber, "commands");
    }

    private static String buildPayload(String packageName, boolean block) {
        try {
            return MAPPER.writeValueAsString(
                    Collections.unmodifiableMap(new java.util.LinkedHashMap<String, Object>() {{
                        put("policy", "appBlock"); // bare key — agent-side ComplexPolicyHandler map lookup
                        put("packageName", packageName);
                        put("value", block);
                    }}));
        } catch (Exception e) {
            return "{}";
        }
    }

    private static LocalTime parseTime(String hhmm) {
        if (hhmm == null || hhmm.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(hhmm.trim(), TIME_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> readPackages(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
