package cn.wildfirechat.app.webhook.gitlab.pojo;

public class Project {
    /*
        "id": 15,
    "name":"Diaspora",
    "description":"",
    "web_url":"http://example.com/mike/diaspora",
    "avatar_url":null,
    "git_ssh_url":"git@example.com:mike/diaspora.git",
    "git_http_url":"http://example.com/mike/diaspora.git",
    "namespace":"Mike",
    "visibility_level":0,
    "path_with_namespace":"mike/diaspora",
    "default_branch":"master",
    "homepage":"http://example.com/mike/diaspora",
    "url":"git@example.com:mike/diaspora.git",
    "ssh_url":"git@example.com:mike/diaspora.git",
    "http_url":"http://example.com/mike/diaspora.git"
     */
    public long id;
    public String name;
    public String description;
    public String web_url;
    public String avatar_url;
    public String git_ssh_url;
    public String git_http_url;
    public String namespace;
    public int visibility_level;
    public String path_with_namespace;
    public String default_branch;
    public String homepage;
    public String url;
    public String ssh_url;
    public String http_url;
}
