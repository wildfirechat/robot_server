package cn.wildfirechat.app.webhook.gitlab.pojo;

public class IssueEvent {
    public User user;
    public Project project;
    public Repository repository;
    public IssueObjectAttributes object_attributes;
    public User[] assignees;
}
