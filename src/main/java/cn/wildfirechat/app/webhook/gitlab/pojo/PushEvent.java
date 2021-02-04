package cn.wildfirechat.app.webhook.gitlab.pojo;

public class PushEvent {
    public String user_name;
    public Repository repository;
    public Commit[] commits;
    public int total_commits_count;
}
