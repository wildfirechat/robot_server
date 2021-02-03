package cn.wildfirechat.app.webhook.gitee.pojo;

public class User {
    public long id;
    public String name;
    public String email;
    public String username;
    public String user_name;
    public String url;
    public String login;
    public String avatar_url;
    public String html_url;
    public String type;
    public String site_admin;
    public String time;
    public String remark;
//    [id: Number],
//    name: String,                   # 用户的昵称。eg：红薯
//    email: String,                  # 用户的邮箱。eg：git@oschina.cn
//  [username: String],             # 用户的 Gitee 个人空间地址。eg：gitee
//  [user_name: String],            # 与上面的 username 一致。
//            [url: String],                  # 用户的 Gitee 个人主页 url。eg：https://gitee.com/gitee
//            [login: String],                # 与上面的 username 一致。
//            [avatar_url: String || null],   # 用户头像 url。eg：https://gitee.com/assets/favicon.ico
//            [html_url: String],             # 与上面的 url 一致。
//            [type: String],                 # 用户类型，目前固定为 User。
//            [site_admin: Boolean],          # 是不是管理员。
//            [time: String],                 # git commit 中的时间。eg：2020-01-01T00:00:00+08:00
//            [remark: String]                # 用户备注名。eg：Ruby 大神
}
