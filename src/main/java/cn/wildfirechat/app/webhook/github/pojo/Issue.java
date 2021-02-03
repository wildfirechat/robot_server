package cn.wildfirechat.app.webhook.github.pojo;

import java.util.Date;

public class Issue {
    public String url;
    public String repository_url;
    public String labels_url;
    public String comments_url;
    public String events_url;
    public String html_url;
    public String id;
    public String node_id;
    public String number;
    public String title;
    public User user;
    public String state;
    public boolean locked;
    public String assignee;
    public String[] assignees;
    public String milestone;
    public int comments;
    public Date created_at;
    public Date updated_at;
    public Date closed_at;
    public String author_association;
    public String body;
}
