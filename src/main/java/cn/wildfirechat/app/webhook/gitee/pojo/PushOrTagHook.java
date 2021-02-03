package cn.wildfirechat.app.webhook.gitee.pojo;

public class PushOrTagHook {
    public String hook_url;
    public String hook_name;
    public String password;
    public long timestamp;
    public String sign;
    public String ref;
    public String before;
    public String after;
    public int total_commits_count;
    public boolean commits_more_than_ten;
    public boolean created;
    public boolean deleted;
    public String compare;
    public Commit[] commits;
    public Commit head_commit;
    public Project repository;
    public Project project;
    public long user_id;
    public String user_name;
    public User user;
    public User pusher;
    public User sender;
    public Enterprise enterprise;
    /*
    hook_id: self.id,                    # 钩子 id。
  hook_url: hook_url,                  # 钩子路由。
  hook_name: String,                   # 钩子名，固定为 push_hooks/tag_push_hooks。
  password: String,                    # 钩子密码。eg：123456
  timestamp: Number,                   # 触发钩子的时间戳。eg: 1576754827988
  sign: String,                        # 钩子根据密钥计算的签名。eg: "rLEHLuZRIQHuTPeXMib9Czoq9dVXO4TsQcmQQHtjXHA="
  ref: String,                         # 推送的分支。eg：refs/heads/master
  before: String,                      # 推送前分支的 commit id。eg：5221c062df39e9e477ab015df22890b7bf13fbbd
  after: String,                       # 推送后分支的 commit id。eg：1cdcd819599cbb4099289dbbec762452f006cb40
  [total_commits_count: Number],       # 推送包含的 commit 总数。
  [commits_more_than_ten: Boolean],    # 推送包含的 commit 总数是否大于十二。
  created: Boolean,                    # 推送的是否是新分支。
  deleted: Boolean,                    # 推送的是否是删除分支。
  compare: String,                     # 推送的 commit 差异 url。eg：https://gitee.com/oschina/git-osc/compare/5221c062df39e9e477ab015df22890b7bf13fbbd...1cdcd819599cbb4099289dbbec762452f006cb40
  commits: [*commit] || null,          # 推送的全部 commit 信息。
  head_commit: commit,                 # 推送最前面的 commit 信息。
  repository: *project,                # 推送的目标仓库信息。
  project: *project,                   # 推送的目标仓库信息。
  user_id: Number,
  user_name: String,                   # 推送者的昵称。
  user: *user,                         # 推送者的用户信息。
  pusher: *user,                       # 推送者的用户信息。
  sender: *user,                       # 推送者的用户信息。
  enterprise: *enterprise || ull       # 推送的目标仓库所在的企业信息。
     */
}
