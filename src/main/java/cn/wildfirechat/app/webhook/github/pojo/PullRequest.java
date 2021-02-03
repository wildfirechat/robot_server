package cn.wildfirechat.app.webhook.github.pojo;

import java.util.Date;

public class PullRequest {
    public String html_url;
    public String id;
    public String node_id;
    public String number;
    public String title;
    public String body;
    public User user;
    public String state;
}
