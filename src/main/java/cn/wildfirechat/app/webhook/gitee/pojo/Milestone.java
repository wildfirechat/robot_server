package cn.wildfirechat.app.webhook.gitee.pojo;

public class Milestone {
    public String html_url;
    public long id;
    public long number;
    public String title;
    public String description;
    public int open_issues;
    public int closed_issues;
    public String state;
    public String created_at;
    public String updated_at;
    public String due_on;
    /*
    html_url: String,                 # Gitee 上对应的 url。eg：https://gitee.com/oschina/git-osc/milestones/1
  id: Number,
  number: Number,                   # 与上面的 id 一致
  title: String,                    # 里程碑的标题。eg：开源计划
  description: String || null,      # 里程碑的详细描述。eg：走向世界
  open_issues: Number,              # 开启状态的 issue 数量
  closed_issues: Number,            # 关闭状态的 issue 数量
  state: String,                    # 里程碑的状态。eg：open
  created_at: String,               # 里程碑创建的时间。eg：2020-01-01T00:00:00+08:00
  updated_at: String,               # 里程碑更新的时间。eg：2020-01-01T00:00:00+08:00
  due_on: String || null            # 里程碑结束的时间。eg：2020-01-01T00:00:00+08:00
     */
}
