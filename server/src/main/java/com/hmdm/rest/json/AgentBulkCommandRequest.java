package com.hmdm.rest.json;

import com.hmdm.persistence.domain.AgentCommand;
import java.io.Serializable;
import java.util.List;

/** Request body for POST /private/agent/v1/bulk/commands: one opaque command fanned to many devices. */
public class AgentBulkCommandRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Integer> deviceIds;
    private AgentCommand command;

    public List<Integer> getDeviceIds() { return deviceIds; }
    public void setDeviceIds(List<Integer> deviceIds) { this.deviceIds = deviceIds; }

    public AgentCommand getCommand() { return command; }
    public void setCommand(AgentCommand command) { this.command = command; }
}
