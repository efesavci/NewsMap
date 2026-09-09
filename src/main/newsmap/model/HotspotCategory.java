package main.newsmap.model;

public enum HotspotCategory {
    POLITICS("Politics"),
    BUSINESS("Business"),
    TECHNOLOGY("Technology"),
    HEALTH("Health"),
    WAR("War"),
    OTHER("Other");


    private final String displayName;

    HotspotCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
