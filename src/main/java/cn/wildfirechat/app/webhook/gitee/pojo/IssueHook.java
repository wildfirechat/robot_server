package cn.wildfirechat.app.webhook.gitee.pojo;

import com.google.gson.Gson;

public class IssueHook {
    public String hook_url;
    public String hook_name;
    public String password;
    public long timestamp;
    public String sign;
    public String action;
    public Issue issue;
    public Project repository;
    public Project project;
    public User sender;
    public User target_user;
    public User assignee;
    public User updated_by;
    public String iid;
    public String title;
    public String description;
    public String state;
    public String milestone;
    public String url;
    public Enterprise enterprise;
    /*
    hook_id: self.id,                  # 钩子 id。
  hook_url: hook_url,                # 钩子路由。
  hook_name: String,                 # 钩子名，固定为 issue_hooks。
  password: String,                  # 钩子密码。eg：123456
  timestamp: Number,                 # 触发钩子的时间戳。eg: 1576754827988
  sign: String,                      # 钩子根据密钥计算的签名。eg: "rLEHLuZRIQHuTPeXMib9Czoq9dVXO4TsQcmQQHtjXHA="
  action: String,                    # issue 状态。eg：open
  issue: *issue,                     # issue 信息。
  repository: *project || null,      # 仓库信息。
  project: *project || null,         # 仓库信息。
  sender: *user,                     # 触发 hook 的用户信息。
  target_user: *user || null,        # 被委托处理 issue 的用户信息。
  user: *user,                       # issue 创建者。
  assignee: *user || null,           # issue 负责人。
  updated_by: *user,                 # 触发 hook 的用户信息。
  iid: String,                       # issue 对应的标识。eg：IG6E9
  title: String,                     # issue 标题。eg：这是一个 issue 标题
  description: String,               # issue 的内容体。eg：数据库优化...
  state: String,                     # issue 状态。eg：open
  milestone: String || null,         # 里程碑的标题。eg：开源计划
  url: String,                       # issue 在 Gitee 上对应的 url。eg：https://gitee.com/oschina/git-osc/issues/1
  enterprise: *enterprise || null    # issue 所属的企业信息。
     */
    public static void main(String[] var0) {
        String str = "{\"action\":\"open\",\"issue\":{\"html_url\":\"https://gitee.com/heavyrain2012/test/issues/I35DB8\",\"id\":5289380,\"number\":\"I35DB8\",\"title\":\"test\",\"user\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"labels\":[],\"state\":\"open\",\"state_name\":\"待办的\",\"type_name\":\"任务\",\"assignee\":null,\"collaborators\":[],\"milestone\":null,\"comments\":0,\"created_at\":\"2021-02-03T16:07:03+08:00\",\"updated_at\":\"2021-02-03T16:07:03+08:00\",\"body\":\"### 该问题是怎么引起的？\\r\\n\\r\\n\\r\\n\\r\\n### 重现步骤\\r\\n\\r\\n\\r\\n\\r\\n### 报错信息\\r\\n\\r\\n\\r\\n\\r\\n\\r\\n\"},\"repository\":{\"id\":14074728,\"name\":\"test\",\"path\":\"test\",\"full_name\":\"heavyrain2012/test\",\"owner\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"private\":false,\"html_url\":\"https://gitee.com/heavyrain2012/test\",\"url\":\"https://gitee.com/heavyrain2012/test\",\"description\":\"test\",\"fork\":false,\"created_at\":\"2021-02-03T14:44:33+08:00\",\"updated_at\":\"2021-02-03T14:44:34+08:00\",\"pushed_at\":\"2021-02-03T14:44:34+08:00\",\"git_url\":\"git://gitee.com/heavyrain2012/test.git\",\"ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"clone_url\":\"https://gitee.com/heavyrain2012/test.git\",\"svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"git_http_url\":\"https://gitee.com/heavyrain2012/test.git\",\"git_ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"git_svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"homepage\":null,\"stargazers_count\":0,\"watchers_count\":1,\"forks_count\":0,\"language\":\"Java\",\"has_issues\":true,\"has_wiki\":true,\"has_pages\":false,\"license\":\"MIT\",\"open_issues_count\":1,\"default_branch\":\"master\",\"namespace\":\"heavyrain2012\",\"name_with_namespace\":\"heavyrain2012/test\",\"path_with_namespace\":\"heavyrain2012/test\"},\"project\":{\"id\":14074728,\"name\":\"test\",\"path\":\"test\",\"full_name\":\"heavyrain2012/test\",\"owner\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"private\":false,\"html_url\":\"https://gitee.com/heavyrain2012/test\",\"url\":\"https://gitee.com/heavyrain2012/test\",\"description\":\"test\",\"fork\":false,\"created_at\":\"2021-02-03T14:44:33+08:00\",\"updated_at\":\"2021-02-03T14:44:34+08:00\",\"pushed_at\":\"2021-02-03T14:44:34+08:00\",\"git_url\":\"git://gitee.com/heavyrain2012/test.git\",\"ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"clone_url\":\"https://gitee.com/heavyrain2012/test.git\",\"svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"git_http_url\":\"https://gitee.com/heavyrain2012/test.git\",\"git_ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"git_svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"homepage\":null,\"stargazers_count\":0,\"watchers_count\":1,\"forks_count\":0,\"language\":\"Java\",\"has_issues\":true,\"has_wiki\":true,\"has_pages\":false,\"license\":\"MIT\",\"open_issues_count\":1,\"default_branch\":\"master\",\"namespace\":\"heavyrain2012\",\"name_with_namespace\":\"heavyrain2012/test\",\"path_with_namespace\":\"heavyrain2012/test\"},\"sender\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"target_user\":null,\"user\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"assignee\":null,\"updated_by\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"iid\":\"I35DB8\",\"title\":\"test\",\"description\":\"### 该问题是怎么引起的？\\r\\n\\r\\n\\r\\n\\r\\n### 重现步骤\\r\\n\\r\\n\\r\\n\\r\\n### 报错信息\\r\\n\\r\\n\\r\\n\\r\\n\\r\\n\",\"state\":\"open\",\"milestone\":null,\"url\":\"https://gitee.com/heavyrain2012/test/issues/I35DB8\",\"enterprise\":null,\"hook_name\":\"issue_hooks\",\"hook_id\":548608,\"hook_url\":\"https://gitee.com/heavyrain2012/test/hooks/548608/edit\",\"password\":\"\",\"timestamp\":\"1612339623863\",\"sign\":\"\"}";
        IssueHook noteHook = new Gson().fromJson(str, IssueHook.class);
        System.out.println(noteHook.action);
    }
}
