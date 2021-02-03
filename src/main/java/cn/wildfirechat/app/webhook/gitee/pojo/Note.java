package cn.wildfirechat.app.webhook.gitee.pojo;

public class Note {
    public long id;
    public String body;
    public User user;
    public String created_at;
    public String updated_at;
    public String html_url;
    public String position;
    public String commit_id;
//    id: Number,
//    body: String,            # 评论内容。eg：好的东西应该开源...
//    user: *user,             # 评论的作者信息。
//    created_at: String,      # 评论的创建时间。eg：2020-01-01T00:00:00+08:00
//    updated_at: String,      # 评论的更新时间。eg：2020-11-11T11:11:11+08:00
//    html_url: String,        # 这条评论在 Gitee 上的 url。eg：https://gitee.com/oschina/git-osc#note_1
//            [position: String],      # 在代码 commit 评论中对应的代码位置。eg：76ec1c6df700af34ae5f8dd00bd7bcb56c1bd706_9_9
//  [commit_id: String]      # 在代码 commit 评论中对应的 commit id。eg：611de62f634d353bb75a290f59fa238ff2d8d3c7
//}
}
