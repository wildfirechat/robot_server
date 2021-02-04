package cn.wildfirechat.app.webhook.gitlab;

import cn.wildfirechat.app.webhook.IWebhook;
import cn.wildfirechat.app.webhook.gitlab.pojo.*;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

public class GitLabWebhook implements IWebhook {
    private static final Logger LOG = LoggerFactory.getLogger(GitLabWebhook.class);
    @Override
    public String invokeCommand() {
        return "/gitlab";
    }

    @Override
    public Object handleWebhookPost(HttpServletRequest request, String payload, SendMessageCallback callback) {
        String event = request.getHeader("X-Gitlab-Event");
        LOG.info("on receive message {}, event {}", payload, event);

        String message = null;

        //https://gitee.com/help/articles/4271#article-header0
        try {
            if (event.equals("Push Hook")) {
                PushEvent pushEvent = new Gson().fromJson(payload, PushEvent.class);
                message = pushEvent.user_name + " push " + pushEvent.total_commits_count + " commit(s) 到项目 " + pushEvent.repository.name;
                for (Commit commit: pushEvent.commits) {
                    message += "\n";
                    message += "\nTitle: " + commit.title;
                    message += "\nMessage: " + commit.message;
                    message += "\nUrl: " + commit.url;
                }
            } else if(event.equals("Tag Push Hook")) {
                TagEvent tagEvent = new Gson().fromJson(payload, TagEvent.class);
                message = tagEvent.user_name + " 在项目 " + tagEvent.repository.name + " " + tagEvent.object_kind + " " + tagEvent.ref;
            } else if(event.equals("Issue Hook")) {
                IssueEvent issueEvent = new Gson().fromJson(payload, IssueEvent.class);
                message = issueEvent.user.name + " 在项目 " + issueEvent.repository.name + " 提交了issue";
                message += "\nTitle: " + issueEvent.object_attributes.title;
                message += "\nMessage: " + issueEvent.object_attributes.description;
                message += "\nUrl: " + issueEvent.object_attributes.url;
            } else if(event.equals("Note Hook")) {
                NoteEvent noteEvent = new Gson().fromJson(payload, NoteEvent.class);
                message = noteEvent.user.name + " 在项目 " + noteEvent.repository.name + " 评论了 " + noteEvent.object_attributes.noteable_type;
                message += "\nMessage: " + noteEvent.object_attributes.note;
                if(noteEvent.object_attributes.noteable_type.equalsIgnoreCase("Commit")) {
                    message += "\nCommit title: " + noteEvent.commit.title;
                    message += "\nCommit message: " + noteEvent.commit.message;
                    message += "\nCommit url: " + noteEvent.commit.url;
                } else if(noteEvent.object_attributes.noteable_type.equalsIgnoreCase("MergeRequest")) {
                    message += "\nMergeRequest title: " + noteEvent.merge_request.description;
                    message += "\nMergeRequest state: " + noteEvent.merge_request.state;
                    message += "\nMergeRequest merge_status: " + noteEvent.merge_request.merge_status;
                } else if(noteEvent.object_attributes.noteable_type.equalsIgnoreCase("Issue")) {
                    message += "\nIssue title: " + noteEvent.issue.title;
                    message += "\nIssue message: " + noteEvent.issue.body;
                    message += "\nIssue url: " + noteEvent.issue.html_url;
                } else if(noteEvent.object_attributes.noteable_type.equalsIgnoreCase("Snippet")) {
                    message += "\nSnippet title: " + noteEvent.snippet.title;
                    message += "\nSnippet message: " + noteEvent.snippet.content;
                    message += "\nSnippet file: " + noteEvent.snippet.file_name;
                }
            } else if(event.equals("Merge Request Hook")) {
                MergeRequestEvent mergeRequestEvent = new Gson().fromJson(payload, MergeRequestEvent.class);
                message = mergeRequestEvent.user.name + " merge request 在项目 " + mergeRequestEvent.repository.name;
                message += "\nTitle: " + mergeRequestEvent.object_attributes.title;
                message += "\ndescription: " + mergeRequestEvent.object_attributes.description;
                message += "\nsource_branch: " + mergeRequestEvent.object_attributes.source_branch;
                message += "\ntarget_branch: " + mergeRequestEvent.object_attributes.target_branch;
            }
        } catch (Exception e) {
            e.printStackTrace();
            callback.sendMessage("糟糕，处理gitlab事件出错了：" + e.getMessage());
        }

        if (message == null) {
            callback.sendMessage("你收到了一个gitlab事件：" + event);
            callback.sendMessage(payload);
        } else {
            callback.sendMessage(message);
        }
        return "ok";
    }
}
