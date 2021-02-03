package cn.wildfirechat.app.webhook.gitee.pojo;

public class Branch {
    public String label;
    public String ref;
    public String sha;
    public User user;
    public Project repo;
    /*
    label: String,    # 分支标记。eg：oschina:master
  ref: String,      # 分支名。eg：master
  sha: String,      # git 提交记录中 sha 值。eg：51b1acb1b4044fcdb2ff8a75ad15a4b655101754
  user: *user,      # 分支所在仓库的所有者信息
  repo: *project    # 分支所在仓库的信息
     */
}
