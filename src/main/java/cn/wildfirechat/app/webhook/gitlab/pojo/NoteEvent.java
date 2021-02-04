package cn.wildfirechat.app.webhook.gitlab.pojo;

import cn.wildfirechat.app.webhook.gitee.pojo.Issue;

public class NoteEvent {
    public User user;
    public Project project;
    public Repository repository;
    public NoteObjectAttributes object_attributes;
    public Commit commit;
    public MergeRequest merge_request;
    public Issue issue;
    public Snippet snippet;
}
