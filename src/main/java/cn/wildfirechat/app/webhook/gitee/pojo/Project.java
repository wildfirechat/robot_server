package cn.wildfirechat.app.webhook.gitee.pojo;

public class Project {
    public long id;
    public String name;
    public String path;
    public String full_name;
    public User owner;
//    public boolean private;
    public String html_url;
    public String url;
    public String description;
    public boolean fork;
    public String created_at;
    public String updated_at;
    public String pushed_at;
    public String git_url;
    public String ssh_url;
    public String clone_url;
    public String svn_url;
    public String git_http_url;
    public String git_ssh_url;
    public String git_svn_ur;
    public String homepage;
    public int stargazers_count;
    public int watchers_count;
    public int forks_count;
    public String language;
    public boolean has_issues;
    public boolean has_wiki;
    public boolean has_pages;
    public String license;
    public int open_issues_count;
    public String default_branch;
    public String namespace;
    public String name_with_namespace;
    public String path_with_namespace;
    /*
    id: Number,
  name: String,                    # 仓库名。eg：gitee
  path: String,                    # 仓库所属的空间地址。eg：oschian
  full_name: String,               # 完整的名字，name + path。eg：gitee/oschian
  owner: *user,                    # 仓库的所有者。
  private: Boolean,                # 是否公开。
  html_url: String,                # 对应 Gitee 的 url。eg：https://gitee.com/oschina/git-osc
  url: String,                     # 与上面 html_url 一致
  description: String,             # 仓库描述。eg：这是一个开源仓库...
  fork: Boolean,                   # 是不是 fork 仓库。
  created_at: String,              # 仓库的创建时间。eg：2020-01-01T00:00:00+08:00
  updated_at: String,              # 仓库的更新时间。eg：2020-01-01T00:00:00+08:00
  pushed_at: String,               # 仓库的最近一次推送时间。eg：2020-01-01T00:00:00+08:00
  git_url: String,                 # 仓库的 git 地址。eg：git://gitee.com:oschina/git-osc.git
  ssh_url: String,                 # 仓库的 ssh 地址。eg：git@gitee.com:oschina/git-osc.git
  clone_url: String,               # 仓库的 clone 地址。eg：https://gitee.com/oschina/git-osc.git
  svn_url: String,                 # 仓库的 svn 地址。eg：svn://gitee.com/oschina/git-osc
  git_http_url: String,            # 与上面的 clone_url 一致。
  git_ssh_url: String,             # 与上面的 ssh_url 一致。
  git_svn_url: String,             # 与上面的 svn_url 一致。
  homepage: String || null,        # 仓库的网页主页。eg：https://gitee.com
  stargazers_count: Number,        # 仓库的 star 数量。
  watchers_count: Number,          # 仓库的 watch 数量。
  forks_count: Number,             # 仓库的 fork 数量。
  language: String,                # 仓库的编程语言。eg： Ruby
  has_issues: Boolean,             # 仓库的是否开启了 issue 功能。
  has_wiki: Boolean,               # 仓库的是否开启了 wiki 功能。
  has_pages: Boolean,              # 仓库的是否开启了 page 服务。
  license: String || null,         # 仓库的开源协议。eg：MIT
  open_issues_count: Number,       # 仓库开启状态的 issue 总数。
  default_branch: String,          # 仓库的默认复制。eg：master
  namespace: String,               # 仓库所属的 Gitee 地址。eg：oschina
  name_with_namespace: String,     # 与上面的 full_name 一致。
  path_with_namespace: String      # 仓库的在 Gitee 的资源唯一标识。eg：oschia/git-osc
     */
}
