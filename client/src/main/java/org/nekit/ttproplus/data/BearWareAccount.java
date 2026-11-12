package org.nekit.ttproplus.data;

import java.io.Serializable;
import java.util.Objects;

public class BearWareAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    public String username;
    public String token;
    public String nickname;
    public long addedTimestamp;
    public long lastUsedTimestamp;

    public BearWareAccount() {
        this.username = "";
        this.token = "";
        this.nickname = "";
        this.addedTimestamp = System.currentTimeMillis();
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public BearWareAccount(String username, String token, String nickname) {
        this.username = username != null ? username : "";
        this.token = token != null ? token : "";
        this.nickname = nickname != null ? nickname : "";
        this.addedTimestamp = System.currentTimeMillis();
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public String getDisplayName() {
        if (nickname != null && !nickname.trim().isEmpty() && !nickname.equalsIgnoreCase(username)) {
            return nickname + " (" + username + ")";
        }
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BearWareAccount that = (BearWareAccount) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
}
