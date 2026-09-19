package com.hmdm.rest.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.persistence.GroupDAO;
import com.hmdm.persistence.GroupPolicyDAO;
import com.hmdm.persistence.domain.Group;
import com.hmdm.persistence.domain.GroupPolicy;
import com.hmdm.rest.json.GroupPolicyRequest;
import com.hmdm.rest.json.GroupPolicyView;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>Group Policies: attaches an app-restriction policy (optionally time-scheduled) to a device
 * {@link Group}. A separate resource from the legacy {@code GroupResource} (which only handles
 * bare group CRUD) — this one layers policy + membership management on top of {@link GroupDAO}.
 * The actual schedule evaluation happens in {@code GroupPolicyScheduler}, not here.</p>
 */
@Api(tags = {"Group Policies"})
@Singleton
@Path("/private/group-policies")
public class GroupPolicyResource {

    private static final Logger logger = LoggerFactory.getLogger(GroupPolicyResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GroupDAO groupDAO;
    private GroupPolicyDAO groupPolicyDAO;

    /** A constructor required by Swagger. */
    public GroupPolicyResource() {
    }

    @Inject
    public GroupPolicyResource(GroupDAO groupDAO, GroupPolicyDAO groupPolicyDAO) {
        this.groupDAO = groupDAO;
        this.groupPolicyDAO = groupPolicyDAO;
    }

    // =================================================================================================================
    @ApiOperation(value = "List group policies", notes = "Lists every device Group with its attached policy. "
            + "Self-seeds the two default groups (\"Enable all apps\", \"Disable these apps after 10 PM\") "
            + "the first time this is called for a customer, whether or not other (e.g. legacy-seeded) "
            + "groups already exist.")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listGroupPolicies() {
        seedDefaultsIfMissing();
        List<Group> groups = groupDAO.getAllGroups();

        List<GroupPolicyView> views = new ArrayList<>();
        for (Group group : groups) {
            views.add(toView(group));
        }
        return Response.OK(views);
    }

    // =================================================================================================================
    @ApiOperation(value = "Create or update a group policy", notes = "Creates a new group (id absent) or "
            + "updates an existing one (id present), together with its policy and device membership.")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveGroupPolicy(GroupPolicyRequest req) {
        if (!SecurityContext.get().hasPermission("settings")) {
            logger.error("Unauthorized attempt to update group policies by user {}",
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
            return Response.ERROR("error.group.policy.invalid");
        }
        String name = req.getName().trim();

        Group dbGroup = groupDAO.getGroupByName(name);
        if (dbGroup != null && !dbGroup.getId().equals(req.getId())) {
            return Response.DUPLICATE_ENTITY("error.duplicate.group");
        }

        Integer groupId;
        if (req.getId() == null) {
            Group group = new Group();
            group.setName(name);
            groupDAO.insertGroup(group); // stamps customerId + generates id
            groupId = group.getId();
        } else {
            // Fetch first rather than build a bare Group(id, name) from the request: updateRecord's
            // ownership check compares customerId, which a client-supplied object would never carry
            // (Group#customerId is @ApiModelProperty(hidden=true)) — fetching preserves the real one.
            Group group = groupDAO.getGroupById(req.getId());
            if (group == null) {
                return Response.ERROR("error.agent.device.unknown");
            }
            group.setName(name);
            groupDAO.updateGroup(group);
            groupId = group.getId();
        }

        String policyType = GroupPolicy.TYPE_BLOCK_SCHEDULED.equals(req.getPolicyType())
                ? GroupPolicy.TYPE_BLOCK_SCHEDULED : GroupPolicy.TYPE_ALLOW_ALL;
        GroupPolicy policy = new GroupPolicy();
        policy.setGroupId(groupId);
        policy.setPolicyType(policyType);
        policy.setPackages(writePackages(req.getPackages()));
        policy.setStartTime(req.getStartTime());
        policy.setEndTime(req.getEndTime());
        policy.setUpdatedAt(System.currentTimeMillis());
        groupPolicyDAO.upsert(policy);

        groupDAO.setGroupDevices(groupId,
                req.getDeviceIds() != null ? req.getDeviceIds() : Collections.emptyList());

        logger.info("Group policy saved: group {} ({}), type {}", groupId, name, policyType);
        return Response.OK(toView(groupDAO.getGroupById(groupId)));
    }

    // =================================================================================================================
    @ApiOperation(value = "Delete a group", notes = "Deletes a group and its policy. Blocked while it still has devices.")
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteGroupPolicy(@PathParam("id") Integer id) {
        if (!SecurityContext.get().hasPermission("settings")) {
            return Response.PERMISSION_DENIED();
        }
        Long count = groupDAO.countDevicesByGroupId(id);
        if (count != null && count > 0) {
            return Response.ERROR("error.notempty.group");
        }
        groupDAO.removeGroupById(id);
        return Response.OK();
    }

    // --- internals -----------------------------------------------------------------

    private static final String DEFAULT_ALLOW_ALL_NAME = "Enable all apps";
    private static final String DEFAULT_BLOCK_NIGHT_NAME = "Disable these apps after 10 PM";

    /** Seeds whichever of the two default groups is missing, by NAME — not "no groups exist at
     *  all", since a fresh customer already has a legacy-seeded default group (e.g. "Общая" from
     *  the Headwind init SQL) that would otherwise make an empty-check never fire. Runs on every
     *  list call but is a no-op once both exist (idempotent, self-healing, no migration needed). */
    private void seedDefaultsIfMissing() {
        if (groupDAO.getGroupByName(DEFAULT_ALLOW_ALL_NAME) == null) {
            Group allowAll = new Group();
            allowAll.setName(DEFAULT_ALLOW_ALL_NAME);
            groupDAO.insertGroup(allowAll);
            GroupPolicy allowAllPolicy = new GroupPolicy();
            allowAllPolicy.setGroupId(allowAll.getId());
            allowAllPolicy.setPolicyType(GroupPolicy.TYPE_ALLOW_ALL);
            allowAllPolicy.setPackages(writePackages(Collections.emptyList()));
            allowAllPolicy.setUpdatedAt(System.currentTimeMillis());
            groupPolicyDAO.upsert(allowAllPolicy);
            logger.info("Seeded default Group Policies group: {} ({})", allowAll.getId(), allowAll.getName());
        }

        if (groupDAO.getGroupByName(DEFAULT_BLOCK_NIGHT_NAME) == null) {
            Group blockNight = new Group();
            blockNight.setName(DEFAULT_BLOCK_NIGHT_NAME);
            groupDAO.insertGroup(blockNight);
            GroupPolicy blockNightPolicy = new GroupPolicy();
            blockNightPolicy.setGroupId(blockNight.getId());
            blockNightPolicy.setPolicyType(GroupPolicy.TYPE_BLOCK_SCHEDULED);
            blockNightPolicy.setPackages(writePackages(Collections.emptyList()));
            blockNightPolicy.setStartTime("22:00");
            blockNightPolicy.setEndTime("06:00");
            blockNightPolicy.setUpdatedAt(System.currentTimeMillis());
            groupPolicyDAO.upsert(blockNightPolicy);
            logger.info("Seeded default Group Policies group: {} ({})", blockNight.getId(), blockNight.getName());
        }
    }

    private GroupPolicyView toView(Group group) {
        GroupPolicy policy = groupPolicyDAO.findByGroupId(group.getId());
        GroupPolicyView view = new GroupPolicyView();
        view.setId(group.getId());
        view.setName(group.getName());
        view.setPolicyType(policy != null ? policy.getPolicyType() : GroupPolicy.TYPE_ALLOW_ALL);
        view.setPackages(policy != null ? readPackages(policy.getPackages()) : Collections.emptyList());
        view.setStartTime(policy != null ? policy.getStartTime() : null);
        view.setEndTime(policy != null ? policy.getEndTime() : null);
        view.setBlockedNow(policy != null && Boolean.TRUE.equals(policy.getBlockedNow()));
        Long count = groupDAO.countDevicesByGroupId(group.getId());
        view.setDeviceCount(count != null ? count.intValue() : 0);
        view.setDeviceIds(groupDAO.listDeviceIdsByGroupId(group.getId()));
        return view;
    }

    private static String writePackages(List<String> packages) {
        try {
            return MAPPER.writeValueAsString(packages != null ? packages : Collections.emptyList());
        } catch (Exception e) {
            return "[]";
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
