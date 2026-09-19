package com.hmdm.rest.json;

import java.io.Serializable;
import java.util.List;

/** Request body for PUT /private/group-policies: create (id absent) or update (id present) a
 *  group's name, policy, and device membership together. */
public class GroupPolicyRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private String policyType;
    private List<String> packages;
    private String startTime;
    private String endTime;
    private List<Integer> deviceIds;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }
    public List<String> getPackages() { return packages; }
    public void setPackages(List<String> packages) { this.packages = packages; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public List<Integer> getDeviceIds() { return deviceIds; }
    public void setDeviceIds(List<Integer> deviceIds) { this.deviceIds = deviceIds; }
}
