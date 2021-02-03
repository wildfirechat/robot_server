package cn.wildfirechat.app.webhook.gitee.pojo;

public class PullRequest {
    public long id;
    public long number;
    public String state;
    public String html_url;
    public String diff_url;
    public String patch_url;
    public String title;
    public String body;
    public String created_at;
    public String updated_at;
    public String closed_at;
    public String merged_at;
    public String merge_commit_sha;
    public String merge_refreence_name;
    public User user;
    public User assignee;
    public User[] assignees;
    public User tester;
    public User[] testers;
    public boolean need_test;
    public boolean need_review;
    public Milestone milestone;
    public Branch head;
    public Branch base;
    public boolean merged;
    public boolean mergeable;
    public String merge_status;
    public User updated_by;
    public int comments;
    public int commits;
    public int additions;
    public int deletions;
    public int changed_files;
    /*id: Number,
  number: Number,                       # 与上面 id 一致
  state: String,                        # PR 状态。eg：open
  html_url: String,                     # PR 在 Gitee 上 url。eg：https://gitee.com/oschina/pulls/1
  diff_url: String,                     # PR diff 信息 url。eg：https://gitee.com/oschina/pulls/1.diff
  patch_url: String,                    # PR patch 信息 url。eg：https://gitee.com/oschina/pulls/1.patch
  title: String,                        # PR 的标题。eg：这是一个 PR 标题
  body: String || null,                 # PR 的内容。eg：升级服务...
  created_at: String,                   # PR 的创建时间。eg：2020-01-01T00:00:00+08:00
  updated_at: String,                   # PR 的更新时间。eg：2020-01-01T00:00:00+08:00
  closed_at: String || null,            # PR 的关闭时间。eg：2020-01-01T00:00:00+08:00
  merged_at: String || null,            # PR 的合并时间。eg：2020-01-01T00:00:00+08:00
  merge_commit_sha: String || null,     # PR 合并产生的 commit id。eg：51b1acb1b4044fcdb2ff8a75ad15a4b655101754
  merge_reference_name: String,         # PR 的源分支目标。eg：refs/pull/1/MERGE
  user: *user,                          # PR 的创建者。
  assignee: *user || null,              # PR 的负责人。
  assignees: [*user] || null,           # PR 的审核人。
  tester: *user || null,                # PR 的测试者。
  testers: [*user] || null,             # PR 的所有测试者。
  need_test: Boolean,                   # PR 是否需要测试。
  need_review: Boolean,                 # PR 是否需要审核。
  milestone: *milestone || null,        # PR 所属的里程碑。
  head: *branch || null,                # PR 的源分支。
  base: *branch,                        # PR 要合并的目标分支
  merged: Boolean,                      # PR 是否已合并。
  mergeable: Boolean,                   # PR 是否可以合并。
  merge_status: String,                 # PR 的合并状态。eg：unchecked
  updated_by: *user || null,            # PR 的修改者。
  comments: Number,                     # PR 的总评论数量。
  commits: Number,                      # PR 的总 commit 数量。
  additions: Number,                    # PR 新增了多少行。
  deletions: Number,                    # PR 删除了多少行。
  changed_files: Number                 # PR 修改了多少行。
     */
}
