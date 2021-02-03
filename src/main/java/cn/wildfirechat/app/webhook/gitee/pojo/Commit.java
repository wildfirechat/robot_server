package cn.wildfirechat.app.webhook.gitee.pojo;

public class Commit {
    public String id;
    public String tree_id;
    public String[] parent_ids;
    public String message;
    public String timestamp;
    public String url;
    public User author;
    public User committer;
    public boolean distinct;
    public String[] added;
    public String[] removed;
    public String[] modified;
//    id: String,
//    tree_id: String,                  # commit tree oid。eg：db78f3594ec0683f5d857ef731df0d860f14f2b2
//    parent_ids: [String],             # commit parent_ids。eg：['a3bddf21a35af54348aae5b0f5627e6ba35be51c']
//    message: String,                  # commit 的信息。eg：fix(cache): 修复了缓存问题
//    timestamp: String,                # commit 的时间。eg：2020-01-01T00:00:00+08:00
//    url: String,                      # commit 对应的 Gitee url。eg：https://gitee.com/mayun-team/oauth2_dingtalk/commit/664b34859fc4a924cd60be2592c0fc788fbeaf8f
//    author: *user,                    # 作者信息。
//    committer: *user,                 # 提交者信息。
//    distinct: Boolean,                # 特殊的 commit，没任何改动，如 tag
//    added: [String] || null,          # 新加入的文件名。eg：['README.md']
//    removed: [String] || null,        # 被移除的文件名。eg：['README.md']
//    modified: [String] || null        # 修改过的文件名。eg：['README.md']
}
