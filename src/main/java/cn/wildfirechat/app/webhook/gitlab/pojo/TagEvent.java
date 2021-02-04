package cn.wildfirechat.app.webhook.gitlab.pojo;

public class TagEvent {
    public String user_name;
    public String ref;
    public Repository repository;
    public String object_kind;
}
