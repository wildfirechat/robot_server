package cn.wildfirechat.app.webhook.gitlab.pojo;

public class MergeRequestEvent {
    public User user;
    public Repository repository;
    public MergeRequest object_attributes;
}
