package cn.wildfirechat.app.webhook.gitee.pojo;

public class Issue {
    public String html_url;
    public long id;
    public String number;
    public String title;
    public User user;
    public Label[] labels;
    public String state;
    public String state_name;
    public String type_name;
    public User assignee;
    public User[] collaborators;
    public Milestone milestone;
    public int comments;
    public String  created_at;
    public String updated_at;
    public String body;
    /*
    html_url: String,                 # Gitee 上对应的 url。eg：https://gitee.com/oschina/git-osc/issues/1
  id: Number,
  number: String,                   # issue 对应的标识。eg：IG6E9
  title: String,                    # issue 标题。eg：这是一个 issue 标题
  user: *user,                      # issue 创建者。
  labels: [*label] || null,         # issue 对应的标签。
  state: String,                    # issue 状态。eg：open
  state_name: String,               # issue 状态名。eg：代办的
  type_name: String,                # issue 类型。eg：任务
  assignee: *user || null,          # issue 负责人。
  collaborators: [*user] || null,   # issue 协助者。
  milestone: *milestone || null,    # issue 所属的里程碑。
  comments: Number,                 # issue 的评论总数
  created_at: String,               # issue 的创建时间。eg：2020-01-01T00:00:00+08:00
  updated_at: String,               # issue 的更新时间。eg：2020-01-01T00:00:00+08:00
  body: String                      # issue 的内容体。eg：数据库优化...
     */
}
