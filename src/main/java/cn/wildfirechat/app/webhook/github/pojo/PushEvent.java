package cn.wildfirechat.app.webhook.github.pojo;

import com.google.gson.Gson;

public class PushEvent {
    public String ref;
    public String before;
    public String after;
    public boolean created;
    public boolean deleted;
    public boolean forced;
    public String base_ref;
    public String compare;
    public Commit[] commits;
    public Commit head_commit;
    public Repository repository;
    public Pusher pusher;
    public Sender sender;

    public static PushEvent fromJson(String jsonStr) {
        return new Gson().fromJson(jsonStr, PushEvent.class);
    }
}
