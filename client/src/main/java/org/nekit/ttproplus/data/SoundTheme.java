package org.nekit.ttproplus.data;

import java.io.Serializable;

public class SoundTheme implements Serializable {
    private String id;
    private String name;
    private String baseTheme;
    private boolean isCustom;
    private long createdAt;

    public SoundTheme(String id, String name, String baseTheme, boolean isCustom) {
        this.id = id;
        this.name = name;
        this.baseTheme = baseTheme != null ? baseTheme : "default";
        this.isCustom = isCustom;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseTheme() {
        return baseTheme;
    }

    public void setBaseTheme(String baseTheme) {
        this.baseTheme = baseTheme;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
