package cn.wildfirechat.app.webhook.gitee.pojo;

import com.google.gson.Gson;

public class NoteHook {
    public String hook_url;
    public String hook_name;
    public String password;
    public long timestamp;
    public String sign;
    public String action;
    public Commit commit;
    public Project repository;
    public Project project;
    public User author;
    public User sender;
    public String url;
    public String note;
    public String noteable_type;
    public long noteable_id;
    public Issue issue;
    public PullRequest pullRequest;
    public String title;
    public String per_iid;
    public String short_commit_id;
    public Enterprise enterprise;
    /*
    hook_id: self.id,                     # 钩子 id。
  hook_url: hook_url,                   # 钩子路由。
  hook_name: String,                    # 钩子名，固定为 note_hooks。
  password: String,                     # 钩子密码。eg：123456
  timestamp: Number,                    # 触发钩子的时间戳。eg: 1576754827988
  sign: String,                         # 钩子根据密钥计算的签名。eg: "rLEHLuZRIQHuTPeXMib9Czoq9dVXO4TsQcmQQHtjXHA="
  action: String,                       # 评论的动作。eg：comment
  comment: *note,                       # 评论的数据信息。
  repository: *project || null,         # 评论所在仓库的信息。
  project: *project || null,            # 评论所在仓库的信息。
  author: *user,                        # 评论的作者信息。
  sender: *user,                        # 评论的作者信息。
  url: String,                          # 这条评论在 Gitee 上的 url。eg：https://gitee.com/oschina/git-osc#note_1
  note: String,                         # 评论内容。eg：好的东西应该开源...
  noteable_type: String,                # 被评论的目标类型。eg：Issue
  noteable_id: Number,                  # 被评论的目标 id。
  [issue: *issue],                      # 被评论的 Issue 信息。
  [pull_request: *pull_request],        # 被评论的 PR 信息。
  title: String || null,                # 被评论的目标标题。eg：这是一个 PR 标题
  per_iid: String,                      # 被评论的目标标识。eg：IG6E9
  short_commit_id: String || null,      # 被平路的 commit 提交中的简短 sha。eg：51b1acb
  enterprise: *enterprise || null       # 被评论的目标所在的企业信息。
     */
    public static void main(String[] var0) {
        String str = "{\"action\":\"comment\",\"comment\":{\"id\":4227147,\"body\":\"aksdjfklads\",\"user\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"created_at\":\"2021-02-03T16:10:05+08:00\",\"updated_at\":\"2021-02-03T16:10:05+08:00\",\"html_url\":\"https://gitee.com/heavyrain2012/test/issues/I35DB8#note_4227147\"},\"repository\":{\"id\":14074728,\"name\":\"test\",\"path\":\"test\",\"full_name\":\"heavyrain2012/test\",\"owner\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"private\":false,\"html_url\":\"https://gitee.com/heavyrain2012/test\",\"url\":\"https://gitee.com/heavyrain2012/test\",\"description\":\"test\",\"fork\":false,\"created_at\":\"2021-02-03T14:44:33+08:00\",\"updated_at\":\"2021-02-03T16:07:03+08:00\",\"pushed_at\":\"2021-02-03T14:44:34+08:00\",\"git_url\":\"git://gitee.com/heavyrain2012/test.git\",\"ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"clone_url\":\"https://gitee.com/heavyrain2012/test.git\",\"svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"git_http_url\":\"https://gitee.com/heavyrain2012/test.git\",\"git_ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"git_svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"homepage\":null,\"stargazers_count\":0,\"watchers_count\":1,\"forks_count\":0,\"language\":\"Java\",\"has_issues\":true,\"has_wiki\":true,\"has_pages\":false,\"license\":\"MIT\",\"open_issues_count\":1,\"default_branch\":\"master\",\"namespace\":\"heavyrain2012\",\"name_with_namespace\":\"heavyrain2012/test\",\"path_with_namespace\":\"heavyrain2012/test\"},\"project\":{\"id\":14074728,\"name\":\"test\",\"path\":\"test\",\"full_name\":\"heavyrain2012/test\",\"owner\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"private\":false,\"html_url\":\"https://gitee.com/heavyrain2012/test\",\"url\":\"https://gitee.com/heavyrain2012/test\",\"description\":\"test\",\"fork\":false,\"created_at\":\"2021-02-03T14:44:33+08:00\",\"updated_at\":\"2021-02-03T16:07:03+08:00\",\"pushed_at\":\"2021-02-03T14:44:34+08:00\",\"git_url\":\"git://gitee.com/heavyrain2012/test.git\",\"ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"clone_url\":\"https://gitee.com/heavyrain2012/test.git\",\"svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"git_http_url\":\"https://gitee.com/heavyrain2012/test.git\",\"git_ssh_url\":\"git@gitee.com:heavyrain2012/test.git\",\"git_svn_url\":\"svn://gitee.com/heavyrain2012/test\",\"homepage\":null,\"stargazers_count\":0,\"watchers_count\":1,\"forks_count\":0,\"language\":\"Java\",\"has_issues\":true,\"has_wiki\":true,\"has_pages\":false,\"license\":\"MIT\",\"open_issues_count\":1,\"default_branch\":\"master\",\"namespace\":\"heavyrain2012\",\"name_with_namespace\":\"heavyrain2012/test\",\"path_with_namespace\":\"heavyrain2012/test\"},\"author\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"sender\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"url\":\"https://gitee.com/heavyrain2012/test/issues/I35DB8#note_4227147\",\"note\":\"aksdjfklads\",\"noteable_type\":\"Issue\",\"noteable_id\":5289380,\"title\":\"test\",\"per_iid\":\"#I35DB8\",\"short_commit_id\":\"\",\"enterprise\":null,\"issue\":{\"html_url\":\"https://gitee.com/heavyrain2012/test/issues/I35DB8\",\"id\":5289380,\"number\":\"I35DB8\",\"title\":\"test\",\"user\":{\"id\":8657970,\"name\":\"heavyrain2012\",\"email\":\"8657970+heavyrain2012@user.noreply.gitee.com\",\"username\":\"heavyrain2012\",\"user_name\":\"heavyrain2012\",\"url\":\"https://gitee.com/heavyrain2012\",\"login\":\"heavyrain2012\",\"avatar_url\":\"https://gitee.com/assets/no_portrait.png\",\"html_url\":\"https://gitee.com/heavyrain2012\",\"type\":\"User\",\"site_admin\":false},\"labels\":[],\"state\":\"open\",\"state_name\":\"待办的\",\"type_name\":\"任务\",\"assignee\":null,\"collaborators\":[],\"milestone\":null,\"comments\":1,\"created_at\":\"2021-02-03T16:07:03+08:00\",\"updated_at\":\"2021-02-03T16:10:05+08:00\",\"body\":\"### 该问题是怎么引起的？\\r\\n\\r\\n\\r\\n\\r\\n### 重现步骤\\r\\n\\r\\n\\r\\n\\r\\n### 报错信息\\r\\n\\r\\n\\r\\n\\r\\n\\r\\n\"},\"hook_name\":\"note_hooks\",\"hook_id\":548608,\"hook_url\":\"https://gitee.com/heavyrain2012/test/hooks/548608/edit\",\"password\":\"\",\"timestamp\":\"1612339806012\",\"sign\":\"\"}";
        NoteHook noteHook = new Gson().fromJson(str, NoteHook.class);
        System.out.println(noteHook.action);
    }
}
