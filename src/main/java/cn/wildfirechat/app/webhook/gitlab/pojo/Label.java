package cn.wildfirechat.app.webhook.gitlab.pojo;

public class Label {
    /*
            "id": 206,
        "title": "API",
        "color": "#ffffff",
        "project_id": 14,
        "created_at": "2013-12-03T17:15:43Z",
        "updated_at": "2013-12-03T17:15:43Z",
        "template": false,
        "description": "API related issues",
        "type": "ProjectLabel",
        "group_id": 41
     */
    public long id;
    public String title;
    public String color;
    public long project_id;
    public String created_at;
    public String updated_at;
    public boolean template;
    public String description;
    public String type;
    public long group_id;
}
