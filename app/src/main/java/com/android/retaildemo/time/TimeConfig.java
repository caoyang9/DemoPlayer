package com.android.retaildemo.time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 时间管理配置 Get()/Set()
 */
public class TimeConfig {
    /**
     * 默认开始和结束时间
     */
    private String startTime = "08:00";
    private String endTime = "22:00";
    private List<Integer> weekDays = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
    private boolean enabled = false;

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
    public List<Integer> getWeekDays() {
        return weekDays;
    }
    public void setWeekDays(List<Integer> weekDays) {
        this.weekDays = weekDays;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}