package cn.wildfirechat.app.webhook.gitlab.pojo;

public class MergeRequest {
    public String target_branch;
    public String source_branch;
    public String state;
    public String title;
    public String merge_status;
    public String description;
    public Repository source;
    public Repository target;
    public Commit last_commit;
}
