package cn.wildfirechat.app.webhook.gitee.pojo;

public class PullRequestHook {
    public String hook_url;
    public String hook_name;
    public String password;
    public long timestamp;
    public String sign;
    public String action;
    public PullRequest pull_request;
    public long number;
    public long iid;
    public String title;
    public String body;
    public String state;
    public String merge_status;
    public String merge_commit_sha;
    public String url;
    public String source_branch;
    public String target_branch;
    public Project project;
    public Project repository;
    public User author;
    public User updated_by;
    public User sender;
    public User target_user;
    public Enterprise enterprise;
    /*
    hook_id: self.id,                    # 钩子 id。
  hook_url: hook_url,                  # 钩子路由。
  hook_name: String,                   # 钩子名，固定为 merge_request_hooks。
  password: String,                    # 钩子密码。eg：123456
  timestamp: Number,                   # 触发钩子的时间戳。eg: 1576754827988
  sign: String,                        # 钩子根据密钥计算的签名。eg: "rLEHLuZRIQHuTPeXMib9Czoq9dVXO4TsQcmQQHtjXHA="
  action: String,                      # PR 状态。eg：open
  pull_request: *pull_request,         # PR 的信息。
  number: Number,                      # PR 的 id。
  iid: Number,                         # 与上面 number 一致。
  title: String,                       # PR 的标题。eg：这是一个 PR 标题
  body: String || nil,                 # PR 的内容。eg：升级服务...
  state: String,                       # PR 状态。eg：open
  merge_status: String,                # PR 的合并状态。eg：unchecked
  merge_commit_sha: String,            # PR 合并产生的 commit id。eg：51b1acb1b4044fcdb2ff8a75ad15a4b655101754
  url: String,                         # PR 在 Gitee 上 url。eg：https://gitee.com/oschina/pulls/1
  source_branch: String || null,       # PR 的源分支名。eg：fixbug
  source_repo: {
    project: *project,                 # PR 的源仓库信息。
    repository: *project               # PR 的源仓库信息。
  } || null,
  target_branch: String,               # PR 的目标分支名。master
  target_repo: {
    project: *project,                 # PR 的目标仓库信息。
    repository: *project               # PR 的目标仓库信息。
  },
  project: *project,                   # PR 的目标仓库信息。
  repository: *project,                # PR 的目标仓库信息。
  author: *user,                       # PR 的创建者信息。
  updated_by: *user,                   # PR 的更新者信息。
  sender: *user,                       # PR 的更新者信息。
  target_user: *user || null,          # 被委托处理 PR 的用户信息。
  enterprise: *enterprise || null      # PR 仓库所在的企业信息。
     */
}
